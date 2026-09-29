package com.flavorlogic.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 目标风味向量：研发目标展开后的结果，**配方生成器的输入契约**（见《项目介绍》4.3）。
 *
 * <p>由 {@code GoalTemplateService.expand()} 产出：把「研发目标 ＋ 基准风味 ＋ 区域画像」
 * 展开成八个维度各自的意图（{@code KEEP} 保持 / {@code CHANGE} 定向 / {@code ALLOW} 放行），
 * 以及若干条成分层动作。</p>
 *
 * <p>注意它描述的是「<b>想要什么</b>」，不是「现在是什么」——后者是 {@link FlavorVector} 的职责。
 * 配方生成器的工作，就是找一组配料用量，让实际风味尽量贴近本对象描述的目标。</p>
 */
public class GoalVector implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 意图：保持基准值（软目标） */
    public static final String INTENT_KEEP = "KEEP";
    /** 意图：定向到目标值 */
    public static final String INTENT_CHANGE = "CHANGE";
    /** 意图：允许自由偏离（不约束） */
    public static final String INTENT_ALLOW = "ALLOW";

    /** 研发目标类型 */
    private String goalType;
    /** 所选区域编码，未选区域时为 null */
    private String regionCode;
    /** 所选区域名称，未选区域时为 null */
    private String regionName;
    /** 八维目标，顺序与 {@link FlavorDimensions#ALL} 一致 */
    private List<DimensionGoal> dimensions = new ArrayList<>();
    /** 成分层动作（如「甜味料需减量」） */
    private List<ComponentGoal> components = new ArrayList<>();

    /**
     * 单个风味维度的目标。
     */
    public static class DimensionGoal implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 维度键 */
        private String key;
        /** 维度中文名 */
        private String label;
        /** 意图：KEEP / CHANGE / ALLOW */
        private String intent;
        /** 基准值（0-100），供生成器判断偏差 */
        private BigDecimal baseline;
        /** 目标值（0-100）；仅 intent = CHANGE 时非空 */
        private BigDecimal target;
        /** 优化权重，0 表示不参与优化 */
        private BigDecimal weight;
        /** 该维度目标的口径说明 */
        private String note;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getIntent() { return intent; }
        public void setIntent(String intent) { this.intent = intent; }

        public BigDecimal getBaseline() { return baseline; }
        public void setBaseline(BigDecimal baseline) { this.baseline = baseline; }

        public BigDecimal getTarget() { return target; }
        public void setTarget(BigDecimal target) { this.target = target; }

        public BigDecimal getWeight() { return weight; }
        public void setWeight(BigDecimal weight) { this.weight = weight; }

        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    /**
     * 成分层动作：告诉配方合成器「哪一类原料要动」。
     *
     * <p>为什么需要它：控糖与控脂的风味层是 {@code KEEP} / {@code ALLOW}，本身没有“要减少”的信号，
     * 若不显式给出成分层动作，合成器不知道该去减哪类原料。而 {@code ADJUST_STIMULATION}
     * 与 {@code REGION_ADAPT} 的风味层已经是 {@code CHANGE}，可以由合成器自行推导，无需重复声明。</p>
     */
    public static class ComponentGoal implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 食材类目名（与 {@code ingredient.category} 取值一致） */
        private String category;
        /** 动作意图：REDUCE 减少 / INCREASE 增加 */
        private String intent;
        /**
         * 用量倍率：{@code REDUCE} 时按此倍数缩放该类目下的用量（0.5 表示减半）。
         *
         * <p>为 null 表示模板未配置倍率——此时合成器会跳过该动作并给出提示，
         * 而不是擅自猜一个值：凭空定一个减量幅度，会让生成出来的配方失去依据。</p>
         */
        private BigDecimal magnitude;
        /** 优化权重 */
        private BigDecimal weight;
        /** 口径说明 */
        private String note;

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getIntent() { return intent; }
        public void setIntent(String intent) { this.intent = intent; }

        public BigDecimal getMagnitude() { return magnitude; }
        public void setMagnitude(BigDecimal magnitude) { this.magnitude = magnitude; }

        public BigDecimal getWeight() { return weight; }
        public void setWeight(BigDecimal weight) { this.weight = weight; }

        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }

    public List<DimensionGoal> getDimensions() { return dimensions; }
    public void setDimensions(List<DimensionGoal> dimensions) { this.dimensions = dimensions; }

    public List<ComponentGoal> getComponents() { return components; }
    public void setComponents(List<ComponentGoal> components) { this.components = components; }
}
