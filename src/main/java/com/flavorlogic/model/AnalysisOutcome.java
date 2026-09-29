package com.flavorlogic.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 分析引擎输出：基准向量、目标向量、偏移明细、补偿建议与解释。
 *
 * <p>引擎只负责计算，不直接访问数据库；由 Service 层负责落库。</p>
 */
public class AnalysisOutcome implements Serializable {

    private static final long serialVersionUID = 1L;

    private String goalType;
    private String goalText;
    private String regionName;
    private FlavorVector baseline = new FlavorVector();
    private FlavorVector target = new FlavorVector();
    private List<FlavorDelta> deltas = new ArrayList<>();
    private List<Suggestion> suggestions = new ArrayList<>();
    /** 主要影响因素说明 */
    private List<String> majorFactors = new ArrayList<>();
    /** 命中的规则编码 */
    private List<String> hitRules = new ArrayList<>();
    /** 结果置信度 0-100 */
    private BigDecimal confidence = BigDecimal.ZERO;
    /** 食材数据完整度 0-100 */
    private BigDecimal dataCompleteness = BigDecimal.ZERO;
    /** 结果摘要 */
    private String summary;
    /** 可解释说明与免责声明 */
    private String explanation;
    /** 引擎版本 */
    private String engineVersion = AnalysisResult.ENGINE_VERSION;

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public String getGoalText() { return goalText; }
    public void setGoalText(String goalText) { this.goalText = goalText; }

    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }

    public FlavorVector getBaseline() { return baseline; }
    public void setBaseline(FlavorVector baseline) { this.baseline = baseline; }

    public FlavorVector getTarget() { return target; }
    public void setTarget(FlavorVector target) { this.target = target; }

    public List<FlavorDelta> getDeltas() { return deltas; }
    public void setDeltas(List<FlavorDelta> deltas) { this.deltas = deltas; }

    public List<Suggestion> getSuggestions() { return suggestions; }
    public void setSuggestions(List<Suggestion> suggestions) { this.suggestions = suggestions; }

    public List<String> getMajorFactors() { return majorFactors; }
    public void setMajorFactors(List<String> majorFactors) { this.majorFactors = majorFactors; }

    public List<String> getHitRules() { return hitRules; }
    public void setHitRules(List<String> hitRules) { this.hitRules = hitRules; }

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
}
