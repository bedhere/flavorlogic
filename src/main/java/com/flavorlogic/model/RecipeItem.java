package com.flavorlogic.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 配方食材明细（对应表 {@code recipe_item}）。
 */
public class RecipeItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long recipeId;
    private Long ingredientId;
    private String ingredientName;
    private BigDecimal amount;
    private String unit;
    private BigDecimal costPerUnit;
    private Integer sortNo;
    private String createdAt;
    private String updatedAt;

    /** 冗余字段：食材分类（列表与规则匹配使用） */
    private String ingredientCategory;
    /** 冗余字段：该食材是否具备完整八维属性 */
    private Boolean attributeAvailable;

    public RecipeItem() {
    }

    public RecipeItem(Long ingredientId, String ingredientName, BigDecimal amount, String unit) {
        this.ingredientId = ingredientId;
        this.ingredientName = ingredientName;
        this.amount = amount;
        this.unit = unit;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public Long getIngredientId() { return ingredientId; }
    public void setIngredientId(Long ingredientId) { this.ingredientId = ingredientId; }

    public String getIngredientName() { return ingredientName; }
    public void setIngredientName(String ingredientName) { this.ingredientName = ingredientName; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getCostPerUnit() { return costPerUnit; }
    public void setCostPerUnit(BigDecimal costPerUnit) { this.costPerUnit = costPerUnit; }

    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getIngredientCategory() { return ingredientCategory; }
    public void setIngredientCategory(String ingredientCategory) { this.ingredientCategory = ingredientCategory; }

    public Boolean getAttributeAvailable() { return attributeAvailable; }
    public void setAttributeAvailable(Boolean attributeAvailable) { this.attributeAvailable = attributeAvailable; }
}
