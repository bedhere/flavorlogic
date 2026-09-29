package com.flavorlogic.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 配方主表（对应表 {@code recipe}）。
 */
public class Recipe implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：正常 */
    public static final int STATUS_ACTIVE = 1;
    /** 状态：已删除（软删除，保证历史分析仍可回看） */
    public static final int STATUS_DELETED = 0;

    private Long id;
    private Long userId;
    private String name;
    private String productType;
    private String processNote;
    private String remark;
    private Integer versionNo;
    private Integer status;
    private String createdAt;
    private String updatedAt;

    /** 关联的配料明细 */
    private List<RecipeItem> items = new ArrayList<>();
    /** 明细数量（列表展示用） */
    private Integer itemCount;
    /** 是否有食材缺少风味属性数据 */
    private Boolean hasMissingAttribute;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }

    public String getProcessNote() { return processNote; }
    public void setProcessNote(String processNote) { this.processNote = processNote; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public List<RecipeItem> getItems() { return items; }
    public void setItems(List<RecipeItem> items) { this.items = items; }

    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }

    public Boolean getHasMissingAttribute() { return hasMissingAttribute; }
    public void setHasMissingAttribute(Boolean hasMissingAttribute) { this.hasMissingAttribute = hasMissingAttribute; }
}
