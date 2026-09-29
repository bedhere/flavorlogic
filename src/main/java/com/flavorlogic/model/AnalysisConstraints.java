package com.flavorlogic.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 分析约束：成本上限、健康化目标、过敏原排除等。
 *
 * <p>对应表 {@code analysis_task.constraint_json}。</p>
 */
public class AnalysisConstraints implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成本上限（元/份，演示口径） */
    private BigDecimal costLimit;
    /** 健康化目标：NONE / LOW_SUGAR / LOW_FAT / LOW_SODIUM */
    private String healthGoal;
    /** 需要排除的过敏原或禁用原料关键字 */
    private List<String> allergens = new ArrayList<>();
    /** 补充说明 */
    private String note;

    public BigDecimal getCostLimit() { return costLimit; }
    public void setCostLimit(BigDecimal costLimit) { this.costLimit = costLimit; }

    public String getHealthGoal() { return healthGoal; }
    public void setHealthGoal(String healthGoal) { this.healthGoal = healthGoal; }

    public List<String> getAllergens() { return allergens; }
    public void setAllergens(List<String> allergens) { this.allergens = allergens; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
