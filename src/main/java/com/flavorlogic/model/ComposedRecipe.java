package com.flavorlogic.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 配方合成结果：**本系统的核心交付物**——一份可直接用于打样的新配方。
 *
 * <p>与 {@link AnalysisOutcome} 的分工：{@code AnalysisOutcome} 回答「改了多少」（依据层），
 * 本对象回答「<b>改成什么样</b>」（交付层）。结果页应当以本对象为主要内容。</p>
 *
 * <p>它同时承担可追溯职责：{@link #adjustments} 里每一项都能回答
 * 「来自哪张知识卡、参考区间是多少、为什么取这个值」，因此生成出来的配方不是黑盒。</p>
 */
public class ComposedRecipe implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 被改造的源配方编号 */
    private Long sourceRecipeId;
    /** 源配方名称 */
    private String sourceRecipeName;
    /** 本次研发目标类型 */
    private String goalType;
    /** 所选区域名称，未选区域时为 null */
    private String regionName;
    /** 生成的新配方配料表 */
    private List<Item> items = new ArrayList<>();
    /** 本次改动清单：每一处都追溯到具体知识卡 */
    private List<Adjustment> adjustments = new ArrayList<>();
    /** 逐维达标情况：目标 vs 合成后的预测值 */
    private List<Check> checks = new ArrayList<>();
    /** 需要用户知晓的提示（复配体系未自动处理、模板缺倍率等） */
    private List<String> warnings = new ArrayList<>();
    /** 基准配方的风味向量 */
    private FlavorVector baselineVector;
    /** 合成后配方的预测风味向量 */
    private FlavorVector predictedVector;

    /**
     * 配料表的一行。
     */
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long ingredientId;
        private String ingredientName;
        private String category;
        private BigDecimal amount;
        private String unit;
        /** 占配方总用量的百分比 */
        private BigDecimal share;
        /** 基准配方中的原用量；本次新增时为 null */
        private BigDecimal baseAmount;
        /** 是否本次新增 */
        private Boolean added;
        /** 用量是否被本次改动过 */
        private Boolean changed;
        /** 依据的知识卡编码；未改动时为 null */
        private String knowledgeCode;
        /** 该行用量为什么这样定 */
        private String reason;

        public Long getIngredientId() { return ingredientId; }
        public void setIngredientId(Long ingredientId) { this.ingredientId = ingredientId; }

        public String getIngredientName() { return ingredientName; }
        public void setIngredientName(String ingredientName) { this.ingredientName = ingredientName; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public BigDecimal getShare() { return share; }
        public void setShare(BigDecimal share) { this.share = share; }

        public BigDecimal getBaseAmount() { return baseAmount; }
        public void setBaseAmount(BigDecimal baseAmount) { this.baseAmount = baseAmount; }

        public Boolean getAdded() { return added; }
        public void setAdded(Boolean added) { this.added = added; }

        public Boolean getChanged() { return changed; }
        public void setChanged(Boolean changed) { this.changed = changed; }

        public String getKnowledgeCode() { return knowledgeCode; }
        public void setKnowledgeCode(String knowledgeCode) { this.knowledgeCode = knowledgeCode; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /**
     * 一处调整。
     */
    public static class Adjustment implements Serializable {

        private static final long serialVersionUID = 1L;

        private String knowledgeCode;
        private String knowledgeTitle;
        private String dimension;
        private String dimensionLabel;
        private String candidate;
        private String actionType;
        private BigDecimal amount;
        private String unit;
        /** 知识卡给定的参考区间文本 */
        private String referenceRange;
        /** 取值理由：为什么从这个区间里取这个值 */
        private String reason;
        private String risk;
        private String evidenceSource;

        public String getKnowledgeCode() { return knowledgeCode; }
        public void setKnowledgeCode(String knowledgeCode) { this.knowledgeCode = knowledgeCode; }

        public String getKnowledgeTitle() { return knowledgeTitle; }
        public void setKnowledgeTitle(String knowledgeTitle) { this.knowledgeTitle = knowledgeTitle; }

        public String getDimension() { return dimension; }
        public void setDimension(String dimension) { this.dimension = dimension; }

        public String getDimensionLabel() { return dimensionLabel; }
        public void setDimensionLabel(String dimensionLabel) { this.dimensionLabel = dimensionLabel; }

        public String getCandidate() { return candidate; }
        public void setCandidate(String candidate) { this.candidate = candidate; }

        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public String getReferenceRange() { return referenceRange; }
        public void setReferenceRange(String referenceRange) { this.referenceRange = referenceRange; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getRisk() { return risk; }
        public void setRisk(String risk) { this.risk = risk; }

        public String getEvidenceSource() { return evidenceSource; }
        public void setEvidenceSource(String evidenceSource) { this.evidenceSource = evidenceSource; }
    }

    /**
     * 一个维度的达标情况。
     */
    public static class Check implements Serializable {

        private static final long serialVersionUID = 1L;

        private String dimension;
        private String label;
        /** 该维度的意图：KEEP / CHANGE / ALLOW */
        private String intent;
        private BigDecimal baseline;
        /** 目标值：KEEP 维度取基准值；ALLOW 维度为 null */
        private BigDecimal target;
        private BigDecimal predicted;
        /** 预测值 − 目标值；ALLOW 维度为 null */
        private BigDecimal gap;
        /** 偏差是否在容差内；ALLOW 维度恒为 true */
        private Boolean satisfied;

        public String getDimension() { return dimension; }
        public void setDimension(String dimension) { this.dimension = dimension; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getIntent() { return intent; }
        public void setIntent(String intent) { this.intent = intent; }

        public BigDecimal getBaseline() { return baseline; }
        public void setBaseline(BigDecimal baseline) { this.baseline = baseline; }

        public BigDecimal getTarget() { return target; }
        public void setTarget(BigDecimal target) { this.target = target; }

        public BigDecimal getPredicted() { return predicted; }
        public void setPredicted(BigDecimal predicted) { this.predicted = predicted; }

        public BigDecimal getGap() { return gap; }
        public void setGap(BigDecimal gap) { this.gap = gap; }

        public Boolean getSatisfied() { return satisfied; }
        public void setSatisfied(Boolean satisfied) { this.satisfied = satisfied; }
    }

    public Long getSourceRecipeId() { return sourceRecipeId; }
    public void setSourceRecipeId(Long sourceRecipeId) { this.sourceRecipeId = sourceRecipeId; }

    public String getSourceRecipeName() { return sourceRecipeName; }
    public void setSourceRecipeName(String sourceRecipeName) { this.sourceRecipeName = sourceRecipeName; }

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    public List<Adjustment> getAdjustments() { return adjustments; }
    public void setAdjustments(List<Adjustment> adjustments) { this.adjustments = adjustments; }

    public List<Check> getChecks() { return checks; }
    public void setChecks(List<Check> checks) { this.checks = checks; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public FlavorVector getBaselineVector() { return baselineVector; }
    public void setBaselineVector(FlavorVector baselineVector) { this.baselineVector = baselineVector; }

    public FlavorVector getPredictedVector() { return predictedVector; }
    public void setPredictedVector(FlavorVector predictedVector) { this.predictedVector = predictedVector; }
}
