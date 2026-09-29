package com.flavorlogic.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 补偿建议：由规则引擎根据偏移方向与规则库生成。
 *
 * <p>功能介绍 7.5 要求建议包含“调整对象、建议方向、参考幅度、生成原因和注意事项”，
 * 不只输出一段笼统文字。</p>
 */
public class Suggestion implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 动作：增加 */
    public static final String ACTION_ADD = "ADD";
    /** 动作：减少 */
    public static final String ACTION_REDUCE = "REDUCE";
    /** 动作：替换 */
    public static final String ACTION_REPLACE = "REPLACE";
    /** 动作：仅提示 */
    public static final String ACTION_NOTICE = "NOTICE";

    /** 触发规则编码 */
    private String ruleCode;
    /** 触发规则名称 */
    private String ruleName;
    /** 来源知识卡编码（如 UK-UMAMI-001）；建议直接由规则生成时为 null */
    private String knowledgeCode;
    /** 来源知识卡标题 */
    private String knowledgeTitle;
    /** 来源知识卡的依据说明（文献/演示数据），用于结果页展示可信来源 */
    private String evidenceSource;
    /** 关联维度 */
    private String dimension;
    private String dimensionLabel;
    /** 建议动作 */
    private String actionType;
    private String actionText;
    /** 调整对象：类目或具体食材 */
    private String target;
    /** 建议方向描述 */
    private String direction;
    /** 参考幅度，例如 “0.05% - 0.30%” */
    private String referenceRange;
    /** 生成原因（规则解释） */
    private String reason;
    /** 注意事项与风险提示 */
    private String risk;
    /** 候选食材 */
    private List<String> candidates = new ArrayList<>();
    /** 该建议对应的偏移比例 */
    private String changeText;

    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public String getKnowledgeCode() { return knowledgeCode; }
    public void setKnowledgeCode(String knowledgeCode) { this.knowledgeCode = knowledgeCode; }

    public String getKnowledgeTitle() { return knowledgeTitle; }
    public void setKnowledgeTitle(String knowledgeTitle) { this.knowledgeTitle = knowledgeTitle; }

    public String getEvidenceSource() { return evidenceSource; }
    public void setEvidenceSource(String evidenceSource) { this.evidenceSource = evidenceSource; }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public String getDimensionLabel() { return dimensionLabel; }
    public void setDimensionLabel(String dimensionLabel) { this.dimensionLabel = dimensionLabel; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getActionText() { return actionText; }
    public void setActionText(String actionText) { this.actionText = actionText; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getReferenceRange() { return referenceRange; }
    public void setReferenceRange(String referenceRange) { this.referenceRange = referenceRange; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRisk() { return risk; }
    public void setRisk(String risk) { this.risk = risk; }

    public List<String> getCandidates() { return candidates; }
    public void setCandidates(List<String> candidates) { this.candidates = candidates; }

    public String getChangeText() { return changeText; }
    public void setChangeText(String changeText) { this.changeText = changeText; }
}
