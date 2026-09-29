package com.flavorlogic.model;

import java.io.Serializable;

/**
 * 风味补偿规则（对应表 {@code flavor_rule}）。
 *
 * <p>{@code conditionJson} 与 {@code actionJson} 保存规则原文，
 * 由 {@code RuleEngine} 解析执行，便于在后台查看“建议为什么产生”。</p>
 */
public class FlavorRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则适用目标：全部 */
    public static final String GOAL_GENERAL = "GENERAL";

    private Long id;
    private String ruleCode;
    private String ruleName;
    private String goalType;
    private Integer priority;
    private String conditionJson;
    private String actionJson;
    private String explanation;
    private Integer status;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public String getConditionJson() { return conditionJson; }
    public void setConditionJson(String conditionJson) { this.conditionJson = conditionJson; }

    public String getActionJson() { return actionJson; }
    public void setActionJson(String actionJson) { this.actionJson = actionJson; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
