package com.flavorlogic.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配方快照：分析任务保存的“当时配方”，包含食材属性，保证历史结果可复现。
 *
 * <p>功能介绍 8.5 要求：从历史记录进入时仍能看到当时的配方数据，
 * 不因基础食材属性后来调整而丢失原结果。因此快照中直接固化八维属性。</p>
 */
public class RecipeSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long recipeId;
    private String recipeName;
    private String productType;
    private String processNote;
    /** 快照生成时间 */
    private String snapshotAt;
    /** 配方总用量（同单位口径下的演示值） */
    private BigDecimal totalAmount;
    private List<SnapshotItem> items = new ArrayList<>();

    /**
     * 快照中的单条配料。
     */
    public static class SnapshotItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long ingredientId;
        private String ingredientName;
        private String ingredientCategory;
        private BigDecimal amount;
        private String unit;
        /** 该食材在分析时刻的八维属性 */
        private Map<String, BigDecimal> attributes = new LinkedHashMap<>();
        /** 是否具备可用属性数据 */
        private Boolean attributeAvailable = Boolean.TRUE;

        public Long getIngredientId() { return ingredientId; }
        public void setIngredientId(Long ingredientId) { this.ingredientId = ingredientId; }

        public String getIngredientName() { return ingredientName; }
        public void setIngredientName(String ingredientName) { this.ingredientName = ingredientName; }

        public String getIngredientCategory() { return ingredientCategory; }
        public void setIngredientCategory(String ingredientCategory) { this.ingredientCategory = ingredientCategory; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public Map<String, BigDecimal> getAttributes() { return attributes; }
        public void setAttributes(Map<String, BigDecimal> attributes) { this.attributes = attributes; }

        public Boolean getAttributeAvailable() { return attributeAvailable; }
        public void setAttributeAvailable(Boolean attributeAvailable) { this.attributeAvailable = attributeAvailable; }
    }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public String getRecipeName() { return recipeName; }
    public void setRecipeName(String recipeName) { this.recipeName = recipeName; }

    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }

    public String getProcessNote() { return processNote; }
    public void setProcessNote(String processNote) { this.processNote = processNote; }

    public String getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(String snapshotAt) { this.snapshotAt = snapshotAt; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public List<SnapshotItem> getItems() { return items; }
    public void setItems(List<SnapshotItem> items) { this.items = items; }
}
