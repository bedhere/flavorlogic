package com.flavorlogic.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 风味分析结果（对应表 {@code analysis_result}）。
 *
 * <p>持久化时写入 JSON 列；接口输出时使用解析后的结构化字段。</p>
 */
public class AnalysisResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 引擎版本，写入手工可追溯的结果快照 */
    public static final String ENGINE_VERSION = "RULE-1.0";

    private Long id;
    private Long taskId;
    private BigDecimal confidence = BigDecimal.ZERO;
    private BigDecimal dataCompleteness = BigDecimal.ZERO;
    private String summary;
    private String explanation;
    private String engineVersion = ENGINE_VERSION;
    private String createdAt;

    /** 数据库 JSON 列原文，不参与接口输出 */
    private transient String baselineJson;
    private transient String targetJson;
    private transient String deltaJson;
    private transient String suggestionJson;

    /** 基准八维向量 */
    private FlavorVector baseline;
    /** 目标八维向量 */
    private FlavorVector target;
    /** 各维度偏移明细 */
    private List<FlavorDelta> deltas = new ArrayList<>();
    /** 补偿建议 */
    private List<Suggestion> suggestions = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public BigDecimal getDataCompleteness() { return dataCompleteness; }
    public void setDataCompleteness(BigDecimal dataCompleteness) { this.dataCompleteness = dataCompleteness; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getBaselineJson() { return baselineJson; }
    public void setBaselineJson(String baselineJson) { this.baselineJson = baselineJson; }

    public String getTargetJson() { return targetJson; }
    public void setTargetJson(String targetJson) { this.targetJson = targetJson; }

    public String getDeltaJson() { return deltaJson; }
    public void setDeltaJson(String deltaJson) { this.deltaJson = deltaJson; }

    public String getSuggestionJson() { return suggestionJson; }
    public void setSuggestionJson(String suggestionJson) { this.suggestionJson = suggestionJson; }

    public FlavorVector getBaseline() { return baseline; }
    public void setBaseline(FlavorVector baseline) { this.baseline = baseline; }

    public FlavorVector getTarget() { return target; }
    public void setTarget(FlavorVector target) { this.target = target; }

    public List<FlavorDelta> getDeltas() { return deltas; }
    public void setDeltas(List<FlavorDelta> deltas) { this.deltas = deltas; }

    public List<Suggestion> getSuggestions() { return suggestions; }
    public void setSuggestions(List<Suggestion> suggestions) { this.suggestions = suggestions; }
}
