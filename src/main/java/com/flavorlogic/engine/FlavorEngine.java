package com.flavorlogic.engine;

import com.flavorlogic.model.FlavorDelta;
import com.flavorlogic.model.FlavorDimensions;
import com.flavorlogic.model.FlavorVector;
import com.flavorlogic.model.RecipeSnapshot;
import com.flavorlogic.model.RegionProfile;
import com.flavorlogic.util.BizException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 风味计算引擎：把「配方 + 用量 + 食材八维属性」换算成可比较、可解释的风味向量。
 *
 * <p>计算口径（见项目介绍 7.3 / 12.1）：</p>
 * <pre>
 * 食材占比 = 该食材用量 / 参与计算的总用量
 * 维度得分 = Σ（食材占比 × 该食材的该维度属性值）      // 加权平均，消除配方总量差异
 * 偏移比例 = （目标得分 - 基准得分）/ max(|基准得分|, 1) × 100%
 * </pre>
 *
 * <p>缺少风味属性数据的食材不参与加权（既不进分子也不进分母），
 * 其用量占比会降低“数据完整度”，进而影响最终置信度——结果页会明确展示这一点。</p>
 */
public class FlavorEngine {

    /** 参与计算的最小总用量，避免除零 */
    private static final BigDecimal MIN_TOTAL_AMOUNT = new BigDecimal("0.001");
    private static final int CALC_SCALE = 4;

    /**
     * 计算一份配方快照的八维风味向量。
     *
     * @param snapshot 配方快照
     * @return 风味向量（加权平均，0-100 口径）
     */
    public FlavorVector compute(RecipeSnapshot snapshot) {
        if (snapshot == null || snapshot.getItems() == null || snapshot.getItems().isEmpty()) {
            throw BizException.badRequest("配方至少需要一项配料才能进行风味分析");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal usableAmount = BigDecimal.ZERO;
        for (RecipeSnapshot.SnapshotItem item : snapshot.getItems()) {
            BigDecimal amount = item.getAmount();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            totalAmount = totalAmount.add(amount);
            if (hasAttributes(item)) {
                usableAmount = usableAmount.add(amount);
            }
        }
        if (totalAmount.compareTo(MIN_TOTAL_AMOUNT) <= 0) {
            throw BizException.badRequest("配方总用量必须大于 0，请检查配料用量");
        }
        if (usableAmount.compareTo(MIN_TOTAL_AMOUNT) <= 0) {
            throw BizException.badRequest("配方中没有可用于计算的食材属性数据，请先由管理员补充食材风味属性");
        }

        FlavorVector vector = new FlavorVector();
        for (String dimension : FlavorDimensions.ALL) {
            BigDecimal sum = BigDecimal.ZERO;
            for (RecipeSnapshot.SnapshotItem item : snapshot.getItems()) {
                BigDecimal amount = item.getAmount();
                if (amount == null || amount.signum() <= 0 || !hasAttributes(item)) {
                    continue;
                }
                BigDecimal attribute = item.getAttributes().get(dimension);
                if (attribute == null) {
                    continue;
                }
                sum = sum.add(amount.multiply(attribute));
            }
            vector.put(dimension, sum.divide(usableAmount, CALC_SCALE, RoundingMode.HALF_UP));
        }
        vector.setTotalAmount(FlavorVector.normalize(totalAmount));
        vector.setItemCount(snapshot.getItems().size());
        return vector;
    }

    /**
     * 计算数据完整度：具备风味属性的用量占配方总用量的百分比。
     *
     * @param snapshot 配方快照
     * @return 0-100 的完整度
     */
    public BigDecimal dataCompleteness(RecipeSnapshot snapshot) {
        if (snapshot == null || snapshot.getItems() == null || snapshot.getItems().isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal usable = BigDecimal.ZERO;
        for (RecipeSnapshot.SnapshotItem item : snapshot.getItems()) {
            BigDecimal amount = item.getAmount();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            total = total.add(amount);
            if (hasAttributes(item)) {
                usable = usable.add(amount);
            }
        }
        if (total.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return usable.multiply(new BigDecimal("100")).divide(total, 2, RoundingMode.HALF_UP);
    }

    /**
     * 计算两个向量之间的八维偏移明细。
     *
     * @param baseline 基准向量
     * @param target   目标向量
     * @return 偏移明细列表（顺序与 {@link FlavorDimensions#ALL} 一致）
     */
    public List<FlavorDelta> diff(FlavorVector baseline, FlavorVector target) {
        List<FlavorDelta> deltas = new ArrayList<>();
        for (String dimension : FlavorDimensions.ALL) {
            FlavorDelta delta = new FlavorDelta(dimension, baseline.get(dimension), target.get(dimension));
            delta.refreshStatus();
            deltas.add(delta);
        }
        return deltas;
    }

    /**
     * 按区域口味权重折算风味向量。
     *
     * @param vector 原始向量
     * @param region 区域画像，可为 null（为 null 时原样返回）
     * @return 折算后的新向量
     */
    public FlavorVector applyRegion(FlavorVector vector, RegionProfile region) {
        if (region == null) {
            return vector;
        }
        return vector.weighted(region.toWeightMap());
    }

    /**
     * 生成“主要影响因素”：哪个食材的用量变化主导了该维度的偏移。
     *
     * @param baselineSnapshot 基准配方快照
     * @param targetSnapshot   目标配方快照
     * @param deltas           偏移明细（方法会就地写入 note 字段）
     * @return 主要影响因素文字列表
     */
    public List<String> explainContributions(RecipeSnapshot baselineSnapshot, RecipeSnapshot targetSnapshot,
                                             List<FlavorDelta> deltas) {
        List<String> factors = new ArrayList<>();
        Map<String, Map<String, BigDecimal>> baseline = contribution(baselineSnapshot);
        Map<String, Map<String, BigDecimal>> target = contribution(targetSnapshot);

        Set<String> names = new LinkedHashSet<>();
        names.addAll(baseline.keySet());
        names.addAll(target.keySet());

        for (FlavorDelta delta : deltas) {
            if (!FlavorDelta.STATUS_DOWN.equals(delta.getStatus())
                    && !FlavorDelta.STATUS_UP.equals(delta.getStatus())
                    && !FlavorDelta.STATUS_OUT_OF_RANGE.equals(delta.getStatus())) {
                continue;
            }
            String dimension = delta.getDimension();
            String downName = null;
            String upName = null;
            BigDecimal down = BigDecimal.ZERO;
            BigDecimal up = BigDecimal.ZERO;
            for (String name : names) {
                BigDecimal change = contributionOf(target, name, dimension)
                        .subtract(contributionOf(baseline, name, dimension));
                if (change.signum() < 0 && change.abs().compareTo(down.abs()) > 0) {
                    down = change;
                    downName = name;
                }
                if (change.signum() > 0 && change.compareTo(up) > 0) {
                    up = change;
                    upName = name;
                }
            }
            StringBuilder note = new StringBuilder();
            if (downName != null) {
                note.append("「").append(downName).append("」贡献减少 ")
                        .append(FlavorVector.normalize(down.abs()));
            }
            if (upName != null) {
                if (note.length() > 0) {
                    note.append("，");
                }
                note.append("「").append(upName).append("」贡献增加 ").append(FlavorVector.normalize(up));
            }
            if (note.length() > 0) {
                // 仅在规则未写入说明时补充，避免覆盖规则给出的更权威解释
                if (delta.getNote() == null) {
                    delta.setNote("主要来自：" + note);
                }
                factors.add(delta.getDimensionLabel() + "：" + note);
            }
        }
        return factors;
    }

    /**
     * 计算每个食材在每个维度上的绝对贡献（用量 × 属性值）。
     *
     * @param snapshot 配方快照
     * @return 食材名 → 维度 → 贡献值
     */
    private Map<String, Map<String, BigDecimal>> contribution(RecipeSnapshot snapshot) {
        Map<String, Map<String, BigDecimal>> result = new LinkedHashMap<>();
        if (snapshot == null || snapshot.getItems() == null) {
            return result;
        }
        for (RecipeSnapshot.SnapshotItem item : snapshot.getItems()) {
            if (item.getAmount() == null || item.getAmount().signum() <= 0 || !hasAttributes(item)) {
                continue;
            }
            String name = item.getIngredientName() == null ? "未命名食材" : item.getIngredientName();
            Map<String, BigDecimal> byDimension = result.computeIfAbsent(name, key -> new LinkedHashMap<>());
            for (String dimension : FlavorDimensions.ALL) {
                BigDecimal attribute = item.getAttributes().get(dimension);
                if (attribute == null) {
                    continue;
                }
                BigDecimal value = item.getAmount().multiply(attribute);
                byDimension.merge(dimension, value, BigDecimal::add);
            }
        }
        return result;
    }

    private BigDecimal contributionOf(Map<String, Map<String, BigDecimal>> contribution, String name, String dimension) {
        Map<String, BigDecimal> byDimension = contribution.get(name);
        if (byDimension == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = byDimension.get(dimension);
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 单条食材是否具备可用的八维属性。
     *
     * @param item 快照配料
     * @return 是否可用
     */
    private boolean hasAttributes(RecipeSnapshot.SnapshotItem item) {
        return !Boolean.FALSE.equals(item.getAttributeAvailable())
                && item.getAttributes() != null
                && !item.getAttributes().isEmpty();
    }
}
