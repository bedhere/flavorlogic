package com.flavorlogic.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 目标项定义（对应表 {@code goal_target}）：把研发目标展开成目标向量的一条规则。
 *
 * <p>这是配方生成器输入契约的数据载体（见《项目介绍》4.3）。**规则写在数据里而不是代码里**——
 * 新增研发目标、调整某个目标的维度意图，都只需要改表，不需要改 Java。</p>
 *
 * <p>两类目标项：</p>
 * <ul>
 *   <li>{@link #KIND_FLAVOR} —— 针对八个风味维度的意图，构成「目标风味向量」；</li>
 *   <li>{@link #KIND_COMPONENT} —— 针对某一类食材的成分层动作，告诉合成器“哪类原料要减”。</li>
 * </ul>
 */
public class GoalTarget implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标种类：风味维度 */
    public static final String KIND_FLAVOR = "FLAVOR";
    /** 目标种类：原料类目（成分层动作） */
    public static final String KIND_COMPONENT = "COMPONENT";

    /** 意图：保持基准值（软目标） */
    public static final String INTENT_KEEP = "KEEP";
    /** 意图：定向到目标值 */
    public static final String INTENT_CHANGE = "CHANGE";
    /** 意图：允许自由偏离（不约束） */
    public static final String INTENT_ALLOW = "ALLOW";

    /** 目标项编号 */
    private Long id;
    /** 研发目标类型 */
    private String goalType;
    /** 目标种类：FLAVOR / COMPONENT */
    private String targetKind;
    /** 目标键：FLAVOR 时为八维键，COMPONENT 时为食材类目名 */
    private String targetKey;
    /** 意图：KEEP / CHANGE / ALLOW */
    private String intent;
    /** CHANGE 时相对基准的倍数；为 null 表示按区域画像权重折算 */
    private BigDecimal magnitude;
    /** 优化权重，0 表示不参与优化 */
    private BigDecimal weight;
    /** 该行口径说明 */
    private String note;
    /** 状态：1 启用，0 停用 */
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public String getTargetKind() { return targetKind; }
    public void setTargetKind(String targetKind) { this.targetKind = targetKind; }

    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public BigDecimal getMagnitude() { return magnitude; }
    public void setMagnitude(BigDecimal magnitude) { this.magnitude = magnitude; }

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
