package com.flavorlogic.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 前端提交的分析请求。
 *
 * <p>包含：任务名称、基准配方、目标调整方案（配料列表）、分析类型、
 * 目标区域与成本/健康约束。</p>
 */
public class AnalysisRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务名称，可为空（为空时由系统按配方名与目标类型生成） */
    private String taskName;
    /** 基准配方编号 */
    private Long recipeId;
    /** 分析类型：REPLACE / REDUCE_SUGAR / REDUCE_FAT / ADJUST_STIMULATION / REGION_ADAPT / GENERAL */
    private String goalType;
    /** 目标区域画像编号，可为空 */
    private Long regionProfileId;
    /** 目标方案配料（用户调整后的用量或替换后的食材） */
    private List<RecipeItem> targetItems = new ArrayList<>();
    /** 成本与健康约束 */
    private AnalysisConstraints constraints;
    /** 本次调整说明 */
    private String remark;

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public Long getRegionProfileId() { return regionProfileId; }
    public void setRegionProfileId(Long regionProfileId) { this.regionProfileId = regionProfileId; }

    public List<RecipeItem> getTargetItems() { return targetItems; }
    public void setTargetItems(List<RecipeItem> targetItems) { this.targetItems = targetItems; }

    public AnalysisConstraints getConstraints() { return constraints; }
    public void setConstraints(AnalysisConstraints constraints) { this.constraints = constraints; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
