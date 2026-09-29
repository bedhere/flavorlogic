package com.flavorlogic.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 配料调配知识卡（对应表 {@code flavor_knowledge}）。
 *
 * <p>与 {@link FlavorRule} 的分工：</p>
 * <ul>
 *   <li>{@code flavor_rule} 回答「<b>什么时候</b>该给建议」——触发条件；</li>
 *   <li>{@code flavor_knowledge} 回答「<b>具体补什么、补多少、为什么、有什么风险</b>」——知识内容。</li>
 * </ul>
 *
 * <p>规则命中后到本表取候选配料与用量区间，建议因此可以从「建议加强鲜味」
 * 升级为「补 0.05% - 0.30% 酵母抽提物，依据：呈味核苷酸与谷氨酸协同，风险：过量呈酵母味」。</p>
 */
public class FlavorKnowledge implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 通用目标类型：任何研发目标都可命中 */
    public static final String GOAL_GENERAL = AnalysisTask.GOAL_GENERAL;

    /** 附加触发条件：无附加条件，维度与目标命中即可用 */
    public static final String TRIGGER_NONE = "NONE";
    /** 附加触发条件：仅当分析设置了过敏原/禁用约束时才启用 */
    public static final String TRIGGER_ALLERGEN_ONLY = "ALLERGEN_ONLY";

    /** 默认用量单位，与建表语句的 DEFAULT '%' 保持一致 */
    public static final String DEFAULT_UNIT = "%";

    /** 知识卡编号 */
    private Long id;
    /** 知识卡编码，如 UK-UMAMI-001 */
    private String knowledgeCode;
    /** 知识卡标题 */
    private String title;
    /** 目标风味维度 */
    private String dimension;
    /** 适用研发目标类型 */
    private String goalType;
    /**
     * 附加触发条件。
     *
     * <p>知识卡的适用条件有三层：<b>风味维度</b>（dimension）、<b>研发目标</b>（goalType）、
     * <b>约束场景</b>（本字段）。前两层由引擎直接匹配，第三层用于表达
     * 「只有用户设了某类约束时这条才成立」——例如「过敏原替代方向」只在用户
     * 填写了过敏原清单时才有意义，否则会在无关场景里冒出来。</p>
     */
    private String triggerConstraint;
    /** 触发场景描述 */
    private String scene;
    /** 补偿方向：ADD / REDUCE / REPLACE / NOTICE */
    private String direction;
    /** 候选配料名称 */
    private String candidateIngredient;
    /** 候选配料类别 */
    private String candidateCategory;
    /** 建议用量下限 */
    private BigDecimal amountMin;
    /** 建议用量上限 */
    private BigDecimal amountMax;
    /** 用量单位 */
    private String amountUnit;
    /** 复配或替代的比例说明 */
    private String ratioNote;
    /** 起效原理，用于可解释输出 */
    private String mechanism;
    /** 风险与注意事项 */
    private String riskNote;
    /** 过敏原标签，逗号分隔 */
    private String allergenTags;
    /** 依据来源或演示数据说明 */
    private String evidenceSource;
    /** 检索关键词，逗号分隔 */
    private String keywords;
    /** 匹配优先级，越小越优先 */
    private Integer priority;
    /** 状态：1 启用，0 停用 */
    private Integer status;

    /**
     * 拼装面向用户的参考幅度文本，格式与 {@code RuleEngine} 原有口径保持一致。
     *
     * @return 参考幅度文本；未填写用量区间时返回 null
     */
    public String referenceRangeText() {
        if (amountMin == null && amountMax == null) {
            return null;
        }
        String unit = (amountUnit == null || amountUnit.trim().isEmpty()) ? DEFAULT_UNIT : amountUnit.trim();
        String suffix = DEFAULT_UNIT.equals(unit) ? "（占配方总量）" : "";
        if (amountMin != null && amountMax != null) {
            return "参考幅度 " + plain(amountMin) + unit + " - " + plain(amountMax) + unit + suffix;
        }
        BigDecimal single = amountMin == null ? amountMax : amountMin;
        return "参考幅度约 " + plain(single) + unit + suffix;
    }

    /**
     * 判断本卡是否适用于指定研发目标。
     *
     * @param goalType 目标类型
     * @return 精确匹配或本卡为 GENERAL 时返回 true
     */
    public boolean appliesTo(String goalType) {
        if (GOAL_GENERAL.equals(this.goalType)) {
            return true;
        }
        return this.goalType != null && this.goalType.equals(goalType);
    }

    /**
     * 附加触发条件是否满足。
     *
     * @param allergens 本次分析的过敏原/禁用清单
     * @return 条件满足返回 true
     */
    public boolean triggerSatisfied(java.util.List<String> allergens) {
        if (TRIGGER_ALLERGEN_ONLY.equals(triggerConstraint)) {
            return allergens != null && !allergens.isEmpty();
        }
        return true;
    }

    /**
     * 本卡是否与用户的过敏原/禁用清单冲突。
     *
     * <p>两处都要检查，缺一不可：</p>
     * <ul>
     *   <li><b>候选配料名</b>：覆盖「花生油」「芝麻酱」这类名字里直接带过敏原的情况；</li>
     *   <li><b>过敏原标签</b>：覆盖「酱油」标注「大豆,小麦」这类名字里看不出来的情况。
     *       若只查名字，用户勾选「大豆」时仍会被推荐使用酱油，而酱油正是大豆制品。</li>
     * </ul>
     *
     * @param allergens 过敏原清单
     * @return 冲突返回 true
     */
    public boolean hitsAllergen(java.util.List<String> allergens) {
        if (allergens == null || allergens.isEmpty()) {
            return false;
        }
        for (String allergen : allergens) {
            if (allergen == null || allergen.trim().isEmpty()) {
                continue;
            }
            String trimmed = allergen.trim();
            if (matches(trimmed, candidateIngredient)) {
                return true;
            }
            for (String tag : splitTags(allergenTags)) {
                if (matches(trimmed, tag)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 单次包含匹配：任一为空时不认为命中。
     *
     * @param allergen 过敏原词
     * @param text     待检查文本
     * @return 命中返回 true
     */
    private static boolean matches(String allergen, String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String value = text.trim();
        return value.contains(allergen) || allergen.contains(value);
    }

    /**
     * 拆分逗号分隔的标签字段（中英文逗号都支持）。
     *
     * @param text 原文本
     * @return 去空后的标签数组
     */
    private static String[] splitTags(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new String[0];
        }
        return text.split("[,，]");
    }

    /**
     * 数值去尾零输出，避免出现 0.0500 这类展示。
     *
     * @param value 数值
     * @return 文本
     */
    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getKnowledgeCode() { return knowledgeCode; }
    public void setKnowledgeCode(String knowledgeCode) { this.knowledgeCode = knowledgeCode; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public String getTriggerConstraint() { return triggerConstraint; }
    public void setTriggerConstraint(String triggerConstraint) { this.triggerConstraint = triggerConstraint; }

    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getCandidateIngredient() { return candidateIngredient; }
    public void setCandidateIngredient(String candidateIngredient) { this.candidateIngredient = candidateIngredient; }

    public String getCandidateCategory() { return candidateCategory; }
    public void setCandidateCategory(String candidateCategory) { this.candidateCategory = candidateCategory; }

    public BigDecimal getAmountMin() { return amountMin; }
    public void setAmountMin(BigDecimal amountMin) { this.amountMin = amountMin; }

    public BigDecimal getAmountMax() { return amountMax; }
    public void setAmountMax(BigDecimal amountMax) { this.amountMax = amountMax; }

    public String getAmountUnit() { return amountUnit; }
    public void setAmountUnit(String amountUnit) { this.amountUnit = amountUnit; }

    public String getRatioNote() { return ratioNote; }
    public void setRatioNote(String ratioNote) { this.ratioNote = ratioNote; }

    public String getMechanism() { return mechanism; }
    public void setMechanism(String mechanism) { this.mechanism = mechanism; }

    public String getRiskNote() { return riskNote; }
    public void setRiskNote(String riskNote) { this.riskNote = riskNote; }

    public String getAllergenTags() { return allergenTags; }
    public void setAllergenTags(String allergenTags) { this.allergenTags = allergenTags; }

    public String getEvidenceSource() { return evidenceSource; }
    public void setEvidenceSource(String evidenceSource) { this.evidenceSource = evidenceSource; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
