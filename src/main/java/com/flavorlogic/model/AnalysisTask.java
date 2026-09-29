package com.flavorlogic.model;

import java.io.Serializable;

/**
 * 风味分析任务（对应表 {@code analysis_task}）。
 *
 * <p>JSON 列在实体中以 transient 字段承载，不参与接口 JSON 输出，
 * 对外统一输出解析后的结构化对象。</p>
 */
public class AnalysisTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标类型 */
    public static final String GOAL_REPLACE = "REPLACE";
    public static final String GOAL_REDUCE_SUGAR = "REDUCE_SUGAR";
    public static final String GOAL_REDUCE_FAT = "REDUCE_FAT";
    public static final String GOAL_ADJUST_STIMULATION = "ADJUST_STIMULATION";
    public static final String GOAL_REGION_ADAPT = "REGION_ADAPT";
    public static final String GOAL_GENERAL = "GENERAL";

    /** 状态 */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    private Long id;
    private Long userId;
    private Long recipeId;
    private Long regionProfileId;
    private String taskName;
    private String goalType;
    private String targetRegion;
    private String status;
    private String errorMessage;
    private String createdAt;
    private String startedAt;
    private String finishedAt;

    /** 冗余字段：创建人昵称（管理端列表） */
    private String username;
    /** 冗余字段：基准配方名称（列表展示） */
    private String recipeName;
    /** 冗余字段：结果摘要（历史列表展示） */
    private String summary;
    /** 冗余字段：是否有分析结果 */
    private Boolean hasResult;
    /** 冗余字段：结果置信度 */
    private java.math.BigDecimal confidence;

    /** 数据库 JSON 列原文，不参与接口输出 */
    private transient String constraintJson;
    private transient String baselineSnapshotJson;
    private transient String targetSnapshotJson;

    /** 解析后的约束 */
    private AnalysisConstraints constraints;
    /** 解析后的基准配方快照 */
    private RecipeSnapshot baselineSnapshot;
    /** 解析后的目标配方快照 */
    private RecipeSnapshot targetSnapshot;
    /** 关联的分析结果（详情接口使用） */
    private AnalysisResult result;
    /** 当前用户对该任务的反馈（详情接口使用） */
    private AnalysisFeedback feedback;

    /**
     * 目标类型中文名。
     *
     * @return 中文名
     */
    public String goalText() {
        if (goalType == null) {
            return "综合分析";
        }
        switch (goalType) {
            case GOAL_REPLACE: return "原料替换";
            case GOAL_REDUCE_SUGAR: return "控糖调整";
            case GOAL_REDUCE_FAT: return "控脂调整";
            case GOAL_ADJUST_STIMULATION: return "刺激度调整";
            case GOAL_REGION_ADAPT: return "区域适配";
            default: return "综合分析";
        }
    }

    /**
     * 状态中文名。
     *
     * @return 中文名
     */
    public String statusText() {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case STATUS_PENDING: return "待处理";
            case STATUS_RUNNING: return "分析中";
            case STATUS_COMPLETED: return "已完成";
            case STATUS_FAILED: return "失败";
            default: return status;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public Long getRegionProfileId() { return regionProfileId; }
    public void setRegionProfileId(Long regionProfileId) { this.regionProfileId = regionProfileId; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public String getTargetRegion() { return targetRegion; }
    public void setTargetRegion(String targetRegion) { this.targetRegion = targetRegion; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRecipeName() { return recipeName; }
    public void setRecipeName(String recipeName) { this.recipeName = recipeName; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Boolean getHasResult() { return hasResult; }
    public void setHasResult(Boolean hasResult) { this.hasResult = hasResult; }

    public java.math.BigDecimal getConfidence() { return confidence; }
    public void setConfidence(java.math.BigDecimal confidence) { this.confidence = confidence; }

    public String getConstraintJson() { return constraintJson; }
    public void setConstraintJson(String constraintJson) { this.constraintJson = constraintJson; }

    public String getBaselineSnapshotJson() { return baselineSnapshotJson; }
    public void setBaselineSnapshotJson(String baselineSnapshotJson) { this.baselineSnapshotJson = baselineSnapshotJson; }

    public String getTargetSnapshotJson() { return targetSnapshotJson; }
    public void setTargetSnapshotJson(String targetSnapshotJson) { this.targetSnapshotJson = targetSnapshotJson; }

    public AnalysisConstraints getConstraints() { return constraints; }
    public void setConstraints(AnalysisConstraints constraints) { this.constraints = constraints; }

    public RecipeSnapshot getBaselineSnapshot() { return baselineSnapshot; }
    public void setBaselineSnapshot(RecipeSnapshot baselineSnapshot) { this.baselineSnapshot = baselineSnapshot; }

    public RecipeSnapshot getTargetSnapshot() { return targetSnapshot; }
    public void setTargetSnapshot(RecipeSnapshot targetSnapshot) { this.targetSnapshot = targetSnapshot; }

    public AnalysisResult getResult() { return result; }
    public void setResult(AnalysisResult result) { this.result = result; }

    public AnalysisFeedback getFeedback() { return feedback; }
    public void setFeedback(AnalysisFeedback feedback) { this.feedback = feedback; }
}
