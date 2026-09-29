package com.flavorlogic.engine;

import com.flavorlogic.model.AnalysisConstraints;
import com.flavorlogic.model.FlavorDimensions;
import com.flavorlogic.model.FlavorKnowledge;
import com.flavorlogic.util.TextUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识卡索引：按「风味维度 ＋ 研发目标」把启用中的知识卡组织成可检索的结构。
 *
 * <p><b>为什么单独成类</b>：规则引擎（生成建议）与配方合成器（生成配方）都要用这张索引，
 * 而且两者的筛选口径必须完全一致。如果各写一份，将来调整索引规则时必然漏改一处——
 * 「同一份逻辑写在两个地方、改一处忘一处」是本项目已经吃过亏的模式，因此这里收敛成共享类。</p>
 */
public final class KnowledgeIndex {

    /** 知识卡未填写优先级时的默认值，与建表语句的 DEFAULT 100 保持一致 */
    private static final int DEFAULT_PRIORITY = 100;

    private KnowledgeIndex() {
    }

    /**
     * 建立「维度 → 已排序知识卡」的索引。
     *
     * <p>目标精确匹配的卡排在前面，{@code GENERAL} 卡追加在后，保证既贴合本次研发目标
     * 又不至于在目标专属卡缺失时完全没有可用知识。同一维度内按 {@code priority} 升序。</p>
     *
     * <p>建索引时同时检查知识卡的<b>附加触发条件</b>（{@code trigger_constraint}）：
     * 例如「过敏原替代方向」只在用户填写了过敏原清单时才有意义，若不加以过滤，
     * 它会在任何涉及该维度的场景里冒出来，与用户的实际处境无关。</p>
     *
     * @param knowledge   启用中的知识卡，可为 null
     * @param goalType    本次的研发目标类型
     * @param constraints 分析约束，用于判断附加触发条件，可为 null
     * @return 维度 → 已按优先级排序的知识卡列表（无边界的维度不会出现在返回的 Map 中）
     */
    public static Map<String, List<FlavorKnowledge>> build(List<FlavorKnowledge> knowledge, String goalType,
                                                          AnalysisConstraints constraints) {
        Map<String, List<FlavorKnowledge>> index = new LinkedHashMap<>();
        if (knowledge == null || knowledge.isEmpty()) {
            return index;
        }
        List<String> allergens = constraints == null ? null : constraints.getAllergens();
        List<FlavorKnowledge> general = new ArrayList<>();
        for (FlavorKnowledge card : knowledge) {
            if (card == null || !FlavorDimensions.isDimension(card.getDimension())
                    || TextUtil.isBlank(card.getCandidateIngredient())) {
                continue;
            }
            // 附加触发条件不满足的卡直接不参与索引
            if (!card.triggerSatisfied(allergens)) {
                continue;
            }
            if (FlavorKnowledge.GOAL_GENERAL.equals(card.getGoalType())) {
                general.add(card);
            } else if (goalType != null && goalType.equals(card.getGoalType())) {
                index.computeIfAbsent(card.getDimension(), key -> new ArrayList<>()).add(card);
            }
        }
        for (FlavorKnowledge card : general) {
            index.computeIfAbsent(card.getDimension(), key -> new ArrayList<>()).add(card);
        }
        for (List<FlavorKnowledge> cards : index.values()) {
            cards.sort(Comparator.comparingInt(KnowledgeIndex::priorityOf));
        }
        return index;
    }

    /**
     * 知识卡优先级，未填写时按默认值处理。
     *
     * @param card 知识卡
     * @return 优先级数值，越小越优先
     */
    private static int priorityOf(FlavorKnowledge card) {
        return card.getPriority() == null ? DEFAULT_PRIORITY : card.getPriority();
    }
}
