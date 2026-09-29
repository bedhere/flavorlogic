package com.flavorlogic.model;

import java.io.Serializable;

/**
 * 分析效果与试产反馈（对应表 {@code analysis_feedback}）。
 *
 * <p>为后续“试产反馈 → 规则校准”的数据闭环预留入口。</p>
 */
public class AnalysisFeedback implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 试产结果 */
    public static final String TRIAL_NOT_TRIED = "NOT_TRIED";
    public static final String TRIAL_PASSED = "PASSED";
    public static final String TRIAL_PARTIAL = "PARTIAL";
    public static final String TRIAL_FAILED = "FAILED";

    private Long id;
    private Long taskId;
    private Long userId;
    private Integer helpful;
    private Integer rating;
    private String comment;
    private String trialResult;
    private String createdAt;
    private String updatedAt;

    /**
     * 试产结果中文名。
     *
     * @return 中文名
     */
    public String trialResultText() {
        if (trialResult == null) {
            return "未标注";
        }
        switch (trialResult) {
            case TRIAL_NOT_TRIED: return "尚未试产";
            case TRIAL_PASSED: return "试产通过";
            case TRIAL_PARTIAL: return "部分达成";
            case TRIAL_FAILED: return "试产未通过";
            default: return trialResult;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getHelpful() { return helpful; }
    public void setHelpful(Integer helpful) { this.helpful = helpful; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getTrialResult() { return trialResult; }
    public void setTrialResult(String trialResult) { this.trialResult = trialResult; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
