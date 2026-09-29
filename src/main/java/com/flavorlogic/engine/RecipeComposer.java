package com.flavorlogic.engine;

import com.flavorlogic.model.AnalysisConstraints;
import com.flavorlogic.model.ComposedRecipe;
import com.flavorlogic.model.FlavorDimensions;
import com.flavorlogic.model.FlavorKnowledge;
import com.flavorlogic.model.FlavorVector;
import com.flavorlogic.model.GoalVector;
import com.flavorlogic.model.Ingredient;
import com.flavorlogic.model.RecipeSnapshot;
import com.flavorlogic.util.TextUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 配方合成器：把「基准配方 ＋ 目标向量 ＋ 知识卡」变成一份可执行的新配方。
 *
 * <p><b>这是本系统的核心</b>——它把过去交给用户的三件事收回系统：读建议、换算用量、反复试改。
 * 用户只需给出研发目标，就能拿到一张配料表。</p>
 *
 * <p>合成分两轮，顺序不能颠倒：</p>
 * <ol>
 *   <li><b>成分层</b>：按目标模板的倍率缩放指定类目的用量（控糖减甜味料、控脂减油脂）。
 *       这一步先做，因为它会显著改变风味，后续的风味补偿必须建立在减量之后的基础上。</li>
 *   <li><b>风味层</b>：逐维计算「预测值」与「目标值」（KEEP 维度的目标就是基准值）的缺口，
 *       缺口超过容差的维度去知识卡取候选与用量区间，把该候选的占比调整到区间内的合适位置。
 *       每调整一维就重算一次向量——加料会改变配方总量（也就是分母），不重算会让后续维度读到过时的缺口。</li>
 * </ol>
 *
 * <p><b>可追溯是硬要求</b>：每一处改动都会产出一条 {@link ComposedRecipe.Adjustment}，
 * 记录知识卡编码、参考区间与取值理由。**没有依据的改动一律不做**——宁可少改一处，
 * 也不能让生成的配方变成黑盒。</p>
 */
public class RecipeComposer {

    private static final Logger LOG = Logger.getLogger(RecipeComposer.class.getName());

    /** 维度偏差容差（0-100 量表上的绝对值）：偏差小于它视为达标，不再调整 */
    private static final BigDecimal TOLERANCE = new BigDecimal("5");

    /** 相对缺口达到该比例时，知识卡取值推到区间上限 */
    private static final BigDecimal FULL_GAP_RATIO = new BigDecimal("0.5");

    private static final int AMOUNT_SCALE = 3;
    private static final int SHARE_SCALE = 2;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /** 复配体系候选的分隔符，例如「赤藓糖醇+甜菊糖苷」 */
    private static final String COMPOUND_SEPARATOR = "+";

    private static final String ACTION_ADD = "ADD";
    private static final String ACTION_REPLACE = "REPLACE";
    private static final String ACTION_REDUCE = "REDUCE";
    private static final String DEFAULT_UNIT = "g";

    private final FlavorEngine flavorEngine = new FlavorEngine();

    /**
     * 依据目标向量合成一份新配方。
     *
     * @param baseline         基准配方快照（含配料与八维属性）
     * @param goal             目标向量，由 {@code GoalTemplateService.expand()} 产出
     * @param cardIndex        知识卡索引，由 {@code KnowledgeIndex.build()} 产出
     * @param ingredientByName 启用中的食材（按名称索引），用于把知识卡候选加入配方
     * @return 合成结果：新配料表 ＋ 改动清单 ＋ 逐维达标情况 ＋ 提示
     */
    public ComposedRecipe compose(RecipeSnapshot baseline, GoalVector goal,
                                  Map<String, List<FlavorKnowledge>> cardIndex,
                                  Map<String, Ingredient> ingredientByName) {
        if (baseline == null || baseline.getItems() == null || baseline.getItems().isEmpty()) {
            throw new IllegalArgumentException("基准配方没有配料，无法合成");
        }
        ComposedRecipe result = new ComposedRecipe();
        result.setSourceRecipeId(baseline.getRecipeId());
        result.setSourceRecipeName(baseline.getRecipeName());
        result.setGoalType(goal.getGoalType());
        result.setRegionName(goal.getRegionName());

        List<WorkItem> workItems = copyItems(baseline);
        result.setBaselineVector(flavorEngine.compute(toSnapshot(baseline, workItems)));

        applyComponents(goal, workItems, result);
        FlavorVector current = flavorEngine.compute(toSnapshot(baseline, workItems));
        applyFlavorGoals(goal, workItems, cardIndex, ingredientByName, current, result);

        FlavorVector predicted = flavorEngine.compute(toSnapshot(baseline, workItems));
        result.setPredictedVector(predicted);
        result.setItems(buildOutputItems(workItems));
        buildChecks(goal, predicted, result);
        explainUnsatisfied(result);

        if (result.getAdjustments().isEmpty()) {
            boolean allSatisfied = true;
            for (ComposedRecipe.Check check : result.getChecks()) {
                if (!Boolean.TRUE.equals(check.getSatisfied())) {
                    allSatisfied = false;
                    break;
                }
            }
            if (allSatisfied) {
                // 目标维度本来就已经达标（例如配方中根本没有油脂却选了控脂），
                // 这不是缺陷，不应该提示"缺少动作或知识卡"去误导用户
                result.getWarnings().add("本次目标的全部维度都已达标，无需调整，输出配方与基准一致。"
                        + "若这不是预期结果，请确认所选研发目标是否与这份配方匹配。");
            } else {
                result.getWarnings().add("本次目标没有产生可自动执行的改动，输出配方与基准一致。"
                        + "常见原因：该目标缺少成分层动作、候选达不到目标、"
                        + "或「原料替换」需要在合成时额外指定「把 A 换成 B」的替换关系（当前版本尚未支持）。");
            }
        }
        return result;
    }

    /* ---------------- 第一轮：成分层 ---------------- */

    /**
     * 按目标模板缩放指定类目的用量。
     *
     * <p>倍率为空时<b>跳过并提示</b>，而不是擅自猜一个减量幅度——凭空定值会让生成的配方失去依据。</p>
     */
    private void applyComponents(GoalVector goal, List<WorkItem> workItems, ComposedRecipe result) {
        for (GoalVector.ComponentGoal component : goal.getComponents()) {
            if (!ACTION_REDUCE.equals(component.getIntent())) {
                continue;
            }
            if (component.getMagnitude() == null) {
                result.getWarnings().add("目标要求的成分层动作「减 " + component.getCategory()
                        + "」尚未配置减量倍率，本次已跳过。请在目标模板表 `goal_target` 中补上 magnitude。");
                continue;
            }
            int touched = 0;
            for (WorkItem item : workItems) {
                if (!component.getCategory().equals(item.snapshot.getIngredientCategory())) {
                    continue;
                }
                BigDecimal before = item.snapshot.getAmount();
                if (before == null || before.signum() <= 0) {
                    continue;
                }
                BigDecimal after = normalizeAmount(before.multiply(component.getMagnitude()));
                item.snapshot.setAmount(after);
                item.changed = true;
                item.reason = component.getNote();
                touched++;
                result.getAdjustments().add(
                        buildComponentAdjustment(component, item, before, after));
            }
            if (touched == 0) {
                result.getWarnings().add("配方中没有「" + component.getCategory()
                        + "」类原料，成分层动作「减 " + component.getCategory() + "」未产生任何改动。");
            }
        }
    }

    /* ---------------- 第二轮：风味层 ---------------- */

    /**
     * 逐维对齐目标：缺口超过容差的维度，用知识卡把候选配料的占比调整到合适位置。
     */
    private void applyFlavorGoals(GoalVector goal, List<WorkItem> workItems,
                                  Map<String, List<FlavorKnowledge>> cardIndex,
                                  Map<String, Ingredient> ingredientByName,
                                  FlavorVector current, ComposedRecipe result) {
        Map<String, GoalVector.DimensionGoal> goalByDimension = new LinkedHashMap<>();
        for (GoalVector.DimensionGoal dimensionGoal : goal.getDimensions()) {
            goalByDimension.put(dimensionGoal.getKey(), dimensionGoal);
        }

        for (String dimension : FlavorDimensions.ALL) {
            GoalVector.DimensionGoal dimensionGoal = goalByDimension.get(dimension);
            if (dimensionGoal == null || GoalVector.INTENT_ALLOW.equals(dimensionGoal.getIntent())) {
                continue;
            }
            // KEEP 维度的目标就是基准值：把甜度「保持」住、靠代糖补回，正是控糖的核心动作
            BigDecimal target = GoalVector.INTENT_CHANGE.equals(dimensionGoal.getIntent())
                    ? dimensionGoal.getTarget() : dimensionGoal.getBaseline();
            if (target == null) {
                continue;
            }
            BigDecimal gap = target.subtract(current.get(dimension));
            if (gap.abs().compareTo(TOLERANCE) <= 0) {
                continue;
            }

            FlavorKnowledge card = pickCard(cardIndex.get(dimension), gap, dimension, target,
                    ingredientByName, result);
            if (card == null) {
                continue;
            }
            Ingredient candidate = ingredientByName == null
                    ? null : ingredientByName.get(card.getCandidateIngredient());
            if (candidate == null) {
                result.getWarnings().add("知识卡 " + card.getKnowledgeCode() + " 命中的候选「"
                        + card.getCandidateIngredient() + "」不在食材库中，无法加入配方。"
                        + "请先补充该食材，否则该维度的缺口无法自动补偿。");
                continue;
            }
            applyCard(card, candidate, dimension, dimensionGoal, gap, workItems, result);
            // 加料改变了配方总量（也就改变了分母），必须重算后再处理下一个维度
            current = flavorEngine.compute(toSnapshot(null, workItems));
        }
    }

    /**
     * 为该维度挑一张可用的知识卡。
     *
     * <p><b>方向匹配规则</b>（这一条很容易定错，定错会让合成器挑到完全无关的配料）：</p>
     * <ul>
     *   <li>缺口为正（需要提高该维度）→ 接受 {@code ADD} 与 {@code REPLACE} 两类卡。
     *       {@code REPLACE} 在此按「补入该维度的替代来源」处理：控糖场景下
     *       「减掉蔗糖」已经由成分层负责，{@code REPLACE} 卡要做的正是补上代糖。
     *       若只接受 {@code ADD}，控糖就会跳过赤藓糖醇这类卡，转而去加「洋葱粉」——完全错。</li>
     *   <li>缺口为负（需要降低该维度）→ 只接受 {@code REDUCE} 卡。</li>
     *   <li>{@code NOTICE} 卡永不参与合成——它本就不产生配方变更。</li>
     * </ul>
     *
     * <p><b>可行性规则</b>（只作用于「需要提高」的情形）：候选配料的该维度属性值必须<b>高于目标值</b>。
     * 因为维度得分是各配料属性按用量占比的加权平均，**结果不会超过参与计算的最高属性值** ——
     * 属性值低于目标的候选，加多少都到不了目标，只会白白稀释其他维度。
     * 例如目标甜度 77.4 而赤藓糖醇的甜度属性只有 65，加它只会把甜度往 65 拉，越加越远。</p>
     *
     * <p>另外两类会被跳过的卡：<b>复配体系</b>（候选含「+」）与<b>缺用量区间</b>的卡。
     * 前者各成分的配比只写在 {@code ratio_note} 自由文本里，程序不能可靠解析，
     * 硬按等分处理会得出离谱结果（例如把甜菊糖苷按赤藓糖醇的量加）；后者无法换算具体用量。</p>
     *
     * @param cards            该维度的候选卡（已按优先级排序）
     * @param gap              缺口，正值表示需要提高该维度
     * @param dimension        维度键
     * @param target           该维度的目标值
     * @param ingredientByName 食材索引，用于做可行性判断
     * @param result           出参：把跳过原因写进提示
     * @return 可用的知识卡；没有则返回 null
     */
    private FlavorKnowledge pickCard(List<FlavorKnowledge> cards, BigDecimal gap, String dimension,
                                     BigDecimal target, Map<String, Ingredient> ingredientByName,
                                     ComposedRecipe result) {
        boolean needAdd = gap.signum() > 0;
        String label = FlavorDimensions.label(dimension);
        List<String> compoundSkipped = new ArrayList<>();
        List<String> rangeMissing = new ArrayList<>();
        List<String> cannotReach = new ArrayList<>();
        int total = cards == null ? 0 : cards.size();
        if (cards != null) {
            for (FlavorKnowledge card : cards) {
                String candidateName = card.getCandidateIngredient();
                if (TextUtil.isBlank(candidateName)) {
                    continue;
                }
                if (candidateName.contains(COMPOUND_SEPARATOR)) {
                    compoundSkipped.add(card.getKnowledgeCode());
                    continue;
                }
                if (card.getAmountMin() == null || card.getAmountMax() == null) {
                    rangeMissing.add(card.getKnowledgeCode());
                    continue;
                }
                if (needAdd) {
                    if (!ACTION_ADD.equals(card.getDirection()) && !ACTION_REPLACE.equals(card.getDirection())) {
                        continue;
                    }
                } else if (!ACTION_REDUCE.equals(card.getDirection())) {
                    continue;
                }
                // 可行性：需要提高时，候选属性值必须高于目标值，否则加了也到不了
                if (needAdd && target != null && ingredientByName != null) {
                    Ingredient candidate = ingredientByName.get(candidateName);
                    if (candidate != null && candidate.valueOf(dimension).compareTo(target) <= 0) {
                        cannotReach.add(candidateName + "（属性 " + plain(candidate.valueOf(dimension))
                                + " ≤ 目标 " + plain(target) + "）");
                        continue;
                    }
                }
                return card;
            }
        }

        if (!cannotReach.isEmpty()) {
            result.getWarnings().add(label + "维度缺口 " + gap.abs().setScale(1, RoundingMode.HALF_UP)
                    + "，但知识库里方向匹配的候选都达不到该目标：" + String.join("、", cannotReach)
                    + "。维度得分是各配料属性按用量占比的加权平均，不会超过配方中的最高属性值，"
                    + "因此这些候选加多少都到不了目标，本次未添加——否则只会稀释其他维度。"
                    + "如需达成，需要引入属性值更高的原料，或调整目标幅度。");
        } else if (!compoundSkipped.isEmpty()) {
            result.getWarnings().add(label + "维度命中的知识卡 " + String.join("、", compoundSkipped)
                    + " 是复配体系，各成分比例只写在文本说明里，当前版本无法自动确定配比，已跳过；"
                    + "建议人工确认后再加入配方。");
        } else if (!rangeMissing.isEmpty()) {
            result.getWarnings().add(label + "维度的知识卡 " + String.join("、", rangeMissing)
                    + " 没有给出用量区间，无法换算具体用量，已跳过。");
        } else if (total == 0) {
            result.getWarnings().add(label + "维度缺口 " + gap.abs().setScale(1, RoundingMode.HALF_UP)
                    + "，但知识库中没有该维度的可用知识卡，缺口无法自动补偿。");
        } else {
            result.getWarnings().add(label + "维度缺口 " + gap.abs().setScale(1, RoundingMode.HALF_UP)
                    + "，需要" + (needAdd ? "提高" : "降低") + "该维度，"
                    + "但知识库里没有方向匹配的可用知识卡。");
        }
        return null;
    }

    /**
     * 施加一张知识卡：把候选配料在配方中的占比调整到量程区间的合适位置。
     *
     * <p>取值位置由缺口大小决定：相对缺口越大取值越靠近区间上限；缺口达到
     * {@link #FULL_GAP_RATIO}（50%）及以上时直接取上限。调整记录里会写明「缺口多少、取在区间什么位置」，
     * 因此这个映射是公开可核对的，不是暗箱参数。</p>
     */
    private void applyCard(FlavorKnowledge card, Ingredient candidate, String dimension,
                           GoalVector.DimensionGoal dimensionGoal, BigDecimal gap,
                           List<WorkItem> workItems, ComposedRecipe result) {
        boolean needAdd = gap.signum() > 0;
        BigDecimal total = totalAmount(workItems);
        if (total.signum() <= 0) {
            return;
        }
        BigDecimal baseline = dimensionGoal.getBaseline();
        BigDecimal ratio = gap.abs().divide(
                baseline == null || baseline.signum() <= 0 ? ONE : baseline, 4, RoundingMode.HALF_UP);
        BigDecimal position = ratio.divide(FULL_GAP_RATIO, 4, RoundingMode.HALF_UP).min(ONE).max(ZERO);
        BigDecimal percent = card.getAmountMin()
                .add(card.getAmountMax().subtract(card.getAmountMin()).multiply(position));
        BigDecimal amount = normalizeAmount(
                total.multiply(percent).divide(HUNDRED, 6, RoundingMode.HALF_UP));

        WorkItem existing = findByName(workItems, candidate.getName());
        BigDecimal before = existing == null ? null : existing.snapshot.getAmount();
        if (existing == null) {
            WorkItem created = new WorkItem(newSnapshotItem(candidate, amount), null);
            created.changed = true;
            created.knowledgeCode = card.getKnowledgeCode();
            created.reason = "依据知识卡 " + card.getKnowledgeCode() + " 新增（"
                    + card.referenceRangeText() + "）";
            workItems.add(created);
        } else {
            // ADD 方向只抬高、不降低；REDUCE 方向只降低、不抬高 —— 避免把已达标的部分又改坏
            boolean needChange = needAdd
                    ? before == null || before.compareTo(amount) < 0
                    : before == null || before.compareTo(amount) > 0;
            if (!needChange) {
                return;
            }
            existing.snapshot.setAmount(amount);
            existing.changed = true;
            existing.knowledgeCode = card.getKnowledgeCode();
            existing.reason = "依据知识卡 " + card.getKnowledgeCode() + " 调整用量";
        }

        ComposedRecipe.Adjustment adjustment = new ComposedRecipe.Adjustment();
        adjustment.setKnowledgeCode(card.getKnowledgeCode());
        adjustment.setKnowledgeTitle(card.getTitle());
        adjustment.setDimension(dimension);
        adjustment.setDimensionLabel(FlavorDimensions.label(dimension));
        adjustment.setCandidate(card.getCandidateIngredient());
        adjustment.setActionType(card.getDirection());
        adjustment.setAmount(normalizeAmount(amount));
        adjustment.setUnit(TextUtil.defaultIfBlank(candidate.getDefaultUnit(), DEFAULT_UNIT));
        adjustment.setReferenceRange(card.referenceRangeText());
        adjustment.setReason("该维度需要" + (needAdd ? "提高" : "降低") + " "
                + gap.abs().setScale(1, RoundingMode.HALF_UP) + "（基准 "
                + plain(dimensionGoal.getBaseline()) + "，目标 " + plain(dimensionGoal.getTarget())
                + "）。按知识卡区间 " + card.referenceRangeText() + " 计算，"
                + "取在区间 " + position.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP) + "% 位置；"
                + (before == null ? "基准配方中原无此配料" : "原用量 " + plain(before))
                + "，调整后为 " + plain(normalizeAmount(amount)) + "。");
        adjustment.setRisk(card.getRiskNote());
        adjustment.setEvidenceSource(card.getEvidenceSource());
        result.getAdjustments().add(adjustment);
    }

    /* ---------------- 达标情况与输出 ---------------- */

    /**
     * 逐维对比预测值与目标值。ALLOW 维度不参与判定，恒视为达标。
     */
    private void buildChecks(GoalVector goal, FlavorVector predicted, ComposedRecipe result) {
        for (GoalVector.DimensionGoal dimensionGoal : goal.getDimensions()) {
            ComposedRecipe.Check check = new ComposedRecipe.Check();
            check.setDimension(dimensionGoal.getKey());
            check.setLabel(dimensionGoal.getLabel());
            check.setIntent(dimensionGoal.getIntent());
            check.setBaseline(dimensionGoal.getBaseline());
            check.setPredicted(predicted.get(dimensionGoal.getKey()));
            if (GoalVector.INTENT_ALLOW.equals(dimensionGoal.getIntent())) {
                // 放行维度不设目标值：它的下降本来就是目标自身带来的代价，不是配方缺陷
                check.setSatisfied(Boolean.TRUE);
            } else {
                BigDecimal target = GoalVector.INTENT_CHANGE.equals(dimensionGoal.getIntent())
                        ? dimensionGoal.getTarget() : dimensionGoal.getBaseline();
                check.setTarget(target);
                if (target == null) {
                    check.setSatisfied(Boolean.TRUE);
                } else {
                    BigDecimal gap = predicted.get(dimensionGoal.getKey()).subtract(target);
                    check.setGap(gap);
                    check.setSatisfied(gap.abs().compareTo(TOLERANCE) <= 0);
                }
            }
            result.getChecks().add(check);
        }
    }

    /**
     * 为「已调整过、但预测仍未达标」的维度补充说明。
     *
     * <p>这类偏差<b>不是配方错了，而是模型表达力不够</b>：维度得分是各配料属性按用量占比的
     * 加权平均，无法体现高倍原料的实际效力 —— 例如 0.02% 的甜菊糖苷在真实配方里可以替代
     * 相当量的蔗糖，但模型只按它 0.02% 的占比参与平均，因此改善几乎为零。</p>
     *
     * <p>这个矛盾必须讲清楚：否则用户看到"预测没达标"，不知道该信配方还是信预测。
     * 用量本身是依据知识卡区间给出的，是可信的。</p>
     */
    private void explainUnsatisfied(ComposedRecipe result) {
        Set<String> adjusted = new LinkedHashSet<>();
        for (ComposedRecipe.Adjustment adjustment : result.getAdjustments()) {
            if (adjustment.getDimension() != null) {
                adjusted.add(adjustment.getDimension());
            }
        }
        for (ComposedRecipe.Check check : result.getChecks()) {
            if (Boolean.TRUE.equals(check.getSatisfied()) || check.getGap() == null
                    || !adjusted.contains(check.getDimension())) {
                continue;
            }
            result.getWarnings().add(check.getLabel() + "维度已按知识卡调整，但预测仍未达标（预测 "
                    + plain(check.getPredicted()) + "，目标 " + plain(check.getTarget()) + "）。"
                    + "这属于模型表达力问题而非配方问题：当前引擎的维度得分是各配料属性按用量占比的"
                    + "加权平均，无法体现高倍原料的实际效力 —— 例如 0.02% 的甜菊糖苷在真实配方中可替代"
                    + "相当量的蔗糖，但模型只按它 0.02% 的占比计算，因此改善微乎其微。"
                    + "配方中的用量是依据知识卡给出的参考区间确定的，可信；预测偏差已列入计设计划的模型升级范围。");
        }
    }

    /**
     * 把工作行转成输出配料表，并计算占比。
     */
    private List<ComposedRecipe.Item> buildOutputItems(List<WorkItem> workItems) {
        BigDecimal total = totalAmount(workItems);
        List<ComposedRecipe.Item> items = new ArrayList<>();
        for (WorkItem work : workItems) {
            RecipeSnapshot.SnapshotItem snapshot = work.snapshot;
            ComposedRecipe.Item item = new ComposedRecipe.Item();
            item.setIngredientId(snapshot.getIngredientId());
            item.setIngredientName(snapshot.getIngredientName());
            item.setCategory(snapshot.getIngredientCategory());
            item.setAmount(normalizeAmount(snapshot.getAmount()));
            item.setUnit(snapshot.getUnit());
            item.setShare(total.signum() <= 0 ? ZERO
                    : snapshot.getAmount().multiply(HUNDRED).divide(total, SHARE_SCALE, RoundingMode.HALF_UP));
            item.setBaseAmount(work.baseAmount == null ? null : normalizeAmount(work.baseAmount));
            item.setAdded(work.baseAmount == null);
            item.setChanged(work.changed);
            item.setKnowledgeCode(work.knowledgeCode);
            item.setReason(work.reason);
            items.add(item);
        }
        return items;
    }

    /* ---------------- 辅助 ---------------- */

    /** 合成过程中的工作行：持有可改的用量与来源信息。 */
    private static final class WorkItem {
        private final RecipeSnapshot.SnapshotItem snapshot;
        private final BigDecimal baseAmount;
        private boolean changed;
        private String knowledgeCode;
        private String reason;

        private WorkItem(RecipeSnapshot.SnapshotItem snapshot, BigDecimal baseAmount) {
            this.snapshot = snapshot;
            this.baseAmount = baseAmount;
        }
    }

    /**
     * 深拷贝基准配料的明细，避免改动污染调用方持有的快照。
     */
    private List<WorkItem> copyItems(RecipeSnapshot baseline) {
        List<WorkItem> items = new ArrayList<>();
        for (RecipeSnapshot.SnapshotItem source : baseline.getItems()) {
            RecipeSnapshot.SnapshotItem copy = new RecipeSnapshot.SnapshotItem();
            copy.setIngredientId(source.getIngredientId());
            copy.setIngredientName(source.getIngredientName());
            copy.setIngredientCategory(source.getIngredientCategory());
            copy.setAmount(source.getAmount());
            copy.setUnit(source.getUnit());
            copy.setAttributeAvailable(source.getAttributeAvailable());
            copy.setAttributes(source.getAttributes() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getAttributes()));
            items.add(new WorkItem(copy, source.getAmount()));
        }
        return items;
    }

    /**
     * 由工作行组装一份快照交给风味引擎计算。
     *
     * @param template  用于承接配方级信息（编号、名称等），可为 null
     * @param workItems 工作行
     * @return 快照
     */
    private RecipeSnapshot toSnapshot(RecipeSnapshot template, List<WorkItem> workItems) {
        RecipeSnapshot snapshot = new RecipeSnapshot();
        if (template != null) {
            snapshot.setRecipeId(template.getRecipeId());
            snapshot.setRecipeName(template.getRecipeName());
            snapshot.setProductType(template.getProductType());
            snapshot.setProcessNote(template.getProcessNote());
            snapshot.setSnapshotAt(template.getSnapshotAt());
        }
        BigDecimal total = ZERO;
        for (WorkItem work : workItems) {
            RecipeSnapshot.SnapshotItem item = work.snapshot;
            if (item.getAmount() != null && item.getAmount().signum() > 0) {
                total = total.add(item.getAmount());
            }
            snapshot.getItems().add(item);
        }
        snapshot.setTotalAmount(total);
        return snapshot;
    }

    /**
     * 依据食材生成一条快照配料行。
     */
    private RecipeSnapshot.SnapshotItem newSnapshotItem(Ingredient ingredient, BigDecimal amount) {
        RecipeSnapshot.SnapshotItem item = new RecipeSnapshot.SnapshotItem();
        item.setIngredientId(ingredient.getId());
        item.setIngredientName(ingredient.getName());
        item.setIngredientCategory(ingredient.getCategory());
        item.setAmount(amount);
        item.setUnit(TextUtil.defaultIfBlank(ingredient.getDefaultUnit(), DEFAULT_UNIT));
        item.setAttributeAvailable(Boolean.TRUE);
        Map<String, BigDecimal> attributes = new LinkedHashMap<>();
        for (String dimension : FlavorDimensions.ALL) {
            attributes.put(dimension, ingredient.valueOf(dimension));
        }
        item.setAttributes(attributes);
        return item;
    }

    /**
     * 生成一条成分层调整记录。它的依据是目标模板而非知识卡，因此不带知识卡编码。
     */
    private ComposedRecipe.Adjustment buildComponentAdjustment(GoalVector.ComponentGoal component,
                                                               WorkItem item, BigDecimal before,
                                                               BigDecimal after) {
        ComposedRecipe.Adjustment adjustment = new ComposedRecipe.Adjustment();
        adjustment.setDimensionLabel("成分层");
        adjustment.setCandidate(item.snapshot.getIngredientName());
        adjustment.setActionType(ACTION_REDUCE);
        adjustment.setAmount(normalizeAmount(after));
        adjustment.setUnit(item.snapshot.getUnit());
        adjustment.setReferenceRange("目标模板倍率 ×" + plain(component.getMagnitude()));
        adjustment.setReason("依据目标模板的成分层动作：减「" + component.getCategory() + "」。"
                + item.snapshot.getIngredientName() + " 由 " + plain(before) + " 调整为 " + plain(after)
                + "（×" + plain(component.getMagnitude()) + "）。" + TextUtil.defaultIfBlank(component.getNote(), ""));
        return adjustment;
    }

    private WorkItem findByName(List<WorkItem> workItems, String name) {
        if (TextUtil.isBlank(name)) {
            return null;
        }
        for (WorkItem work : workItems) {
            if (name.equals(work.snapshot.getIngredientName())) {
                return work;
            }
        }
        return null;
    }

    private BigDecimal totalAmount(List<WorkItem> workItems) {
        BigDecimal total = ZERO;
        for (WorkItem work : workItems) {
            BigDecimal amount = work.snapshot.getAmount();
            if (amount != null && amount.signum() > 0) {
                total = total.add(amount);
            }
        }
        return total;
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return ZERO;
        }
        return value.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private String plain(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }
}
