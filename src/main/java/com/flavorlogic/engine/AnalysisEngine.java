package com.flavorlogic.engine;

import com.flavorlogic.model.AnalysisConstraints;
import com.flavorlogic.model.AnalysisOutcome;
import com.flavorlogic.model.AnalysisTask;
import com.flavorlogic.model.FlavorDelta;
import com.flavorlogic.model.FlavorDimensions;
import com.flavorlogic.model.FlavorKnowledge;
import com.flavorlogic.model.FlavorRule;
import com.flavorlogic.model.FlavorVector;
import com.flavorlogic.model.RecipeSnapshot;
import com.flavorlogic.model.RegionProfile;
import com.flavorlogic.model.Suggestion;
import com.flavorlogic.util.TextUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 分析引擎门面：把配方快照、区域画像与规则库组织成一次完整的可解释分析。
 *
 * <p>调用顺序：计算基准/目标风味向量 → 区域折算 → 偏移计算 → 规则匹配 → 置信度与解释生成。
 * 引擎不访问数据库，配方快照与规则由 Service 层准备好后传入。</p>
 */
public class AnalysisEngine {

    /** 区域权重高于该值视为“偏好更强” */
    private static final BigDecimal REGION_STRONG = new BigDecimal("1.10");
    /** 区域权重低于该值视为“偏好更弱” */
    private static final BigDecimal REGION_WEAK = new BigDecimal("0.90");
    /** 置信度权重：数据完整度 */
    private static final BigDecimal WEIGHT_COMPLETENESS = new BigDecimal("0.8");
    /** 置信度权重：规则覆盖率 */
    private static final BigDecimal WEIGHT_RULE_COVERAGE = new BigDecimal("0.2");
    /** 规则覆盖率饱和条数 */
    private static final int RULE_COVERAGE_FULL = 3;
    /** 绝对差值小于该值时视为噪声，不作为「主要变化」列出（0-100 量表） */
    private static final BigDecimal MIN_ABS_DIFF = new BigDecimal("1");

    private final FlavorEngine flavorEngine = new FlavorEngine();
    private final RuleEngine ruleEngine = new RuleEngine();

    /**
     * 执行一次风味分析。
     *
     * @param task      分析任务（提供目标类型、区域与约束）
     * @param baseline  基准配方快照
     * @param target    目标配方快照
     * @param region    目标区域画像，可为 null
     * @param rules     适用于该目标的启用规则
     * @param knowledge 启用中的配料调配知识卡；为 null 时建议回落到规则自带候选
     * @return 分析结果
     */
    public AnalysisOutcome analyze(AnalysisTask task, RecipeSnapshot baseline, RecipeSnapshot target,
                                  RegionProfile region, List<FlavorRule> rules,
                                  List<FlavorKnowledge> knowledge) {
        AnalysisConstraints constraints = task == null ? null : task.getConstraints();
        String goalType = task == null ? AnalysisTask.GOAL_GENERAL : task.getGoalType();

        AnalysisOutcome outcome = new AnalysisOutcome();
        outcome.setGoalType(goalType);
        outcome.setGoalText(task == null ? "综合分析" : task.goalText());

        FlavorVector baselineVector = flavorEngine.compute(baseline);
        FlavorVector targetVector = flavorEngine.compute(target);
        if (region != null) {
            baselineVector = flavorEngine.applyRegion(baselineVector, region);
            targetVector = flavorEngine.applyRegion(targetVector, region);
            outcome.setRegionName(region.getRegionName());
        }
        outcome.setBaseline(baselineVector);
        outcome.setTarget(targetVector);

        List<FlavorDelta> deltas = flavorEngine.diff(baselineVector, targetVector);
        List<Suggestion> suggestions = new ArrayList<>(
                ruleEngine.evaluate(rules, deltas, constraints, knowledge, goalType));
        if (region != null) {
            suggestions.addAll(buildRegionSuggestions(region, baselineVector));
        }
        outcome.setDeltas(deltas);
        outcome.setSuggestions(suggestions);

        List<String> factors = flavorEngine.explainContributions(baseline, target, deltas);
        outcome.setMajorFactors(factors);

        Set<String> hitRules = new LinkedHashSet<>();
        for (Suggestion suggestion : suggestions) {
            if (!TextUtil.isBlank(suggestion.getRuleName())) {
                hitRules.add(suggestion.getRuleName());
            }
        }
        outcome.setHitRules(new ArrayList<>(hitRules));

        BigDecimal baselineCompleteness = flavorEngine.dataCompleteness(baseline);
        BigDecimal targetCompleteness = flavorEngine.dataCompleteness(target);
        BigDecimal completeness = baselineCompleteness.min(targetCompleteness);
        outcome.setDataCompleteness(completeness);

        double coverage = Math.min(hitRules.size() / (double) RULE_COVERAGE_FULL, 1.0);
        BigDecimal confidence = completeness.multiply(WEIGHT_COMPLETENESS)
                .add(BigDecimal.valueOf(coverage * 100).multiply(WEIGHT_RULE_COVERAGE))
                .setScale(2, RoundingMode.HALF_UP);
        if (confidence.compareTo(new BigDecimal("100")) > 0) {
            confidence = new BigDecimal("100.00");
        }
        outcome.setConfidence(confidence);

        outcome.setSummary(buildSummary(outcome, baseline, target));
        outcome.setExplanation(buildExplanation(outcome, baseline, target, region,
                baselineCompleteness, targetCompleteness, constraints));
        return outcome;
    }

    /**
     * 依据区域画像生成“区域偏好”建议：偏好更强提示加强，偏好更弱提示减弱。
     *
     * @param region   区域画像
     * @param baseline 基准向量
     * @return 建议列表
     */
    private List<Suggestion> buildRegionSuggestions(RegionProfile region, FlavorVector baseline) {
        List<Suggestion> suggestions = new ArrayList<>();
        for (String dimension : FlavorDimensions.ALL) {
            BigDecimal weight = region.weightOf(dimension);
            boolean strong = weight.compareTo(REGION_STRONG) >= 0;
            boolean weak = weight.compareTo(REGION_WEAK) <= 0;
            if (!strong && !weak) {
                continue;
            }
            Suggestion suggestion = new Suggestion();
            suggestion.setRuleCode("REGION_PREFERENCE_" + dimension.toUpperCase());
            suggestion.setRuleName("区域口味偏好");
            suggestion.setDimension(dimension);
            suggestion.setDimensionLabel(FlavorDimensions.label(dimension));
            suggestion.setActionType(strong ? Suggestion.ACTION_ADD : Suggestion.ACTION_REDUCE);
            suggestion.setActionText(strong ? "建议加强" : "建议减弱");
            suggestion.setTarget(FlavorDimensions.label(dimension) + "相关原料");
            suggestion.setDirection((strong ? "建议加强" : "建议减弱") + "：" + FlavorDimensions.label(dimension)
                    + "（区域偏好权重 " + weight.stripTrailingZeros().toPlainString() + "）");
            suggestion.setReferenceRange("按区域画像微调（演示数据）");
            suggestion.setReason("「" + region.getRegionName() + "」对" + FlavorDimensions.label(dimension)
                    + "的偏好权重为 " + weight.stripTrailingZeros().toPlainString()
                    + "，" + (strong ? "高于" : "低于") + "全国基准 1.000；基准配方在该维度的得分为 "
                    + baseline.get(dimension).stripTrailingZeros().toPlainString() + "。");
            suggestion.setRisk("区域画像为课设演示数据，实际投放前需结合目标城市消费者测试校准。");
            suggestion.setChangeText("权重 " + weight.stripTrailingZeros().toPlainString());
            suggestions.add(suggestion);
        }
        return suggestions;
    }

    private String buildSummary(AnalysisOutcome outcome, RecipeSnapshot baseline, RecipeSnapshot target) {
        // 「主要变化」按绝对差值筛选与排序：
        // 只用百分比排序会把基准极低的维度（如 0.13 的变化被放大成几十 %）排到最前面，
        // 这些实际上在 0-100 量表上属于噪声，因此额外要求绝对差值不小于 1。
        List<FlavorDelta> significant = new ArrayList<>();
        for (FlavorDelta delta : outcome.getDeltas()) {
            boolean moved = FlavorDelta.STATUS_DOWN.equals(delta.getStatus())
                    || FlavorDelta.STATUS_UP.equals(delta.getStatus());
            if (moved && delta.getDiff().abs().compareTo(MIN_ABS_DIFF) >= 0) {
                significant.add(delta);
            }
        }
        significant.sort((a, b) -> b.getDiff().abs().compareTo(a.getDiff().abs()));

        StringBuilder sb = new StringBuilder();
        sb.append("本次分析对比「").append(TextUtil.defaultIfBlank(baseline.getRecipeName(), "基准配方"))
                .append("」与目标方案，共 ").append(target.getItems() == null ? 0 : target.getItems().size())
                .append(" 项配料；");
        if (significant.isEmpty()) {
            sb.append("八维风味整体保持稳定（各维度变化均在 ±")
                    .append(FlavorDelta.STABLE_THRESHOLD.stripTrailingZeros().toPlainString()).append("% 以内）。");
        } else {
            sb.append("主要变化：");
            int limit = Math.min(3, significant.size());
            for (int i = 0; i < limit; i++) {
                FlavorDelta delta = significant.get(i);
                if (i > 0) {
                    sb.append("、");
                }
                sb.append(delta.getDimensionLabel()).append(" ").append(delta.getDirection()).append(" ");
                if (delta.isLowBaseline()) {
                    // 基准值过低时百分比会被放大（1.58 → 10.95 会算成 +593%），摘要改用绝对差值表述
                    sb.append(delta.getDiff().abs().stripTrailingZeros().toPlainString())
                            .append("（基准值仅 ").append(delta.getBaseline().stripTrailingZeros().toPlainString())
                            .append("，比例不具参考性）");
                } else {
                    sb.append(delta.getChangePercent().abs().stripTrailingZeros().toPlainString()).append("%");
                }
            }
            if (significant.size() > limit) {
                sb.append(" 等 ").append(significant.size()).append(" 个维度");
            }
            sb.append("。");
        }
        sb.append("命中 ").append(outcome.getHitRules().size()).append(" 条规则，给出 ")
                .append(outcome.getSuggestions().size()).append(" 条参考建议。");
        return TextUtil.truncate(sb.toString(), 900);
    }

    private String buildExplanation(AnalysisOutcome outcome, RecipeSnapshot baseline, RecipeSnapshot target,
                                    RegionProfile region, BigDecimal baselineCompleteness,
                                    BigDecimal targetCompleteness, AnalysisConstraints constraints) {
        StringBuilder sb = new StringBuilder();

        sb.append("一、计算口径").append('\n');
        sb.append("基准与目标的风味向量均按「食材用量占比 × 该食材的维度属性值」加权平均得到（0-100 相对评分），")
                .append("已按各自配方总量归一化，因此总用量不同的配方之间可以比较。").append('\n');
        sb.append("偏移比例 =（目标得分 - 基准得分）/ max(|基准得分|, ")
                .append(FlavorDelta.MIN_BASELINE.stripTrailingZeros().toPlainString())
                .append(") × 100%；变化幅度小于 ±")
                .append(FlavorDelta.STABLE_THRESHOLD.stripTrailingZeros().toPlainString())
                .append("% 视为稳定。").append('\n');
        sb.append("判读提醒：当某维度的基准得分低于 ")
                .append(FlavorDelta.LOW_BASELINE.stripTrailingZeros().toPlainString())
                .append("（0-100 量表上几乎感知不到）时，比例的分母过小会被放大到几百甚至上千 %，")
                .append("例如基准 1.58 → 目标 10.95 会算成 +593%，实际只是「从几乎不甜变成有点甜」。")
                .append("这类维度请以绝对差值判读，结果页会标注「基准极低」。").append('\n');
        if (region != null) {
            sb.append("区域折算：已按「").append(region.getRegionName())
                    .append("」的区域口味权重对基准与目标同时折算（权重 1.000 表示与全国基准一致）。").append('\n');
        } else {
            sb.append("区域折算：本次未选择目标区域，按全国基准口径计算。").append('\n');
        }
        if (constraints != null && constraints.getAllergens() != null && !constraints.getAllergens().isEmpty()) {
            sb.append("约束过滤：过敏原/禁用清单「").append(String.join("、", constraints.getAllergens()))
                    .append("」下的候选食材已从建议中剔除。").append('\n');
        }

        sb.append('\n').append("二、结果与依据").append('\n');
        if (outcome.getMajorFactors().isEmpty()) {
            sb.append("本次调整未造成显著风味偏移，无主导性原料变化。").append('\n');
        } else {
            sb.append("主要影响因素：").append(String.join("；", outcome.getMajorFactors())).append('。').append('\n');
        }
        if (outcome.getHitRules().isEmpty()) {
            sb.append("未命中补偿规则，说明本次调整未触发现有规则库中的风险区间。").append('\n');
        } else {
            sb.append("命中规则：").append(String.join("、", outcome.getHitRules()))
                    .append("，对应建议已在建议表中给出触发原因与参考幅度。").append('\n');
        }

        sb.append('\n').append("三、数据完整度与置信度").append('\n');
        sb.append("基准配方数据完整度 ").append(plain(baselineCompleteness))
                .append("%，目标配方数据完整度 ").append(plain(targetCompleteness)).append("%；")
                .append("结果置信度 = 完整度 × 0.8 + 规则覆盖率 × 0.2 = ").append(plain(outcome.getConfidence())).append("%。").append('\n');
        String missing = describeMissing(baseline, target);
        if (missing != null) {
            sb.append("以下配料缺少风味属性数据，未参与加权计算：").append(missing).append("。").append('\n');
        }

        sb.append('\n').append("四、免责声明").append('\n');
        sb.append("本结果基于课程设计阶段的演示数据与结构化规则生成，不等同于实验室仪器检测或感官评价结论，")
                .append("也不构成新品上市成功率的预测。最终配方仍需经过试产、感官评价与食品安全审核。");
        return sb.toString();
    }

    private String describeMissing(RecipeSnapshot baseline, RecipeSnapshot target) {
        Set<String> missing = new LinkedHashSet<>();
        collectMissing(baseline, missing);
        collectMissing(target, missing);
        return missing.isEmpty() ? null : String.join("、", missing);
    }

    private void collectMissing(RecipeSnapshot snapshot, Set<String> missing) {
        if (snapshot == null || snapshot.getItems() == null) {
            return;
        }
        for (RecipeSnapshot.SnapshotItem item : snapshot.getItems()) {
            if (Boolean.FALSE.equals(item.getAttributeAvailable())) {
                missing.add(TextUtil.defaultIfBlank(item.getIngredientName(), "未命名食材"));
            }
        }
    }

    private String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }
}
