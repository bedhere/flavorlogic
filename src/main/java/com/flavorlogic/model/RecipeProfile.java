package com.flavorlogic.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配方风味画像（纯计算结果，不落库）。
 *
 * <p>用于配方库列表的风味标签与「查看画像」弹窗：把某个配方的八维风味向量算出来交给前端，
 * 但<b>不生成分析任务、不写分析历史</b>。分析历史只应记录「有对比」的分析，
 * 否则会被大量无信息量的记录挤占，可追溯性反而被削弱。</p>
 *
 * <p>数值由 {@code FlavorEngine.compute()} 计算，与正式分析完全同一套口径，
 * 因此列表页看到的画像与结果页顶部的「味觉打分」必然一致，不会出现两个说法。</p>
 */
public class RecipeProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配方编号 */
    private Long recipeId;
    /** 配方名称快照 */
    private String recipeName;
    /** 配方版本号 */
    private Integer versionNo;
    /** 八维风味得分：维度编码 → 0-100 相对评分 */
    private Map<String, BigDecimal> scores = new LinkedHashMap<>();
    /** 数据完整度：有属性数据的用量占配方总用量的百分比，0-100 */
    private BigDecimal completeness;
    /** 参与计算的有效配料条数 */
    private Integer itemCount;
    /** 配方总量（演示口径：直接累加各配料用量数值，不做单位换算） */
    private BigDecimal totalAmount;
    /** 是否存在缺少风味属性数据的配料；为 true 时前端应给出提示 */
    private Boolean hasMissingAttribute;

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public String getRecipeName() { return recipeName; }
    public void setRecipeName(String recipeName) { this.recipeName = recipeName; }

    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }

    public Map<String, BigDecimal> getScores() { return scores; }
    public void setScores(Map<String, BigDecimal> scores) { this.scores = scores; }

    public BigDecimal getCompleteness() { return completeness; }
    public void setCompleteness(BigDecimal completeness) { this.completeness = completeness; }

    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public Boolean getHasMissingAttribute() { return hasMissingAttribute; }
    public void setHasMissingAttribute(Boolean hasMissingAttribute) {
        this.hasMissingAttribute = hasMissingAttribute;
    }
}
