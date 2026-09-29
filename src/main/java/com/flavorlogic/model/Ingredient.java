package com.flavorlogic.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 食材及其八维风味属性（对应表 {@code ingredient}）。
 *
 * <p>八个维度的取值均为 0-100 的课设演示相对评分，
 * 用于验证“配方 → 风味向量 → 偏移 → 补偿建议”的完整闭环，
 * 不等同于实验室检测结果。</p>
 */
public class Ingredient implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String category;
    private String defaultUnit;
    private BigDecimal sweet;
    private BigDecimal salty;
    private BigDecimal sour;
    private BigDecimal bitter;
    private BigDecimal umami;
    private BigDecimal spicy;
    private BigDecimal numbing;
    private BigDecimal fatAroma;
    private String description;
    private String dataSource;
    private Integer status;
    private String createdAt;
    private String updatedAt;

    /**
     * 取指定维度的属性值。
     *
     * @param dimension 维度编码（sweet/salty/...）
     * @return 属性值，缺失时返回 0
     */
    public BigDecimal valueOf(String dimension) {
        BigDecimal value;
        switch (dimension) {
            case FlavorDimensions.SWEET: value = sweet; break;
            case FlavorDimensions.SALTY: value = salty; break;
            case FlavorDimensions.SOUR: value = sour; break;
            case FlavorDimensions.BITTER: value = bitter; break;
            case FlavorDimensions.UMAMI: value = umami; break;
            case FlavorDimensions.SPICY: value = spicy; break;
            case FlavorDimensions.NUMBING: value = numbing; break;
            case FlavorDimensions.FAT_AROMA: value = fatAroma; break;
            default: value = null;
        }
        return value == null ? BigDecimal.ZERO : value;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDefaultUnit() { return defaultUnit; }
    public void setDefaultUnit(String defaultUnit) { this.defaultUnit = defaultUnit; }

    public BigDecimal getSweet() { return sweet; }
    public void setSweet(BigDecimal sweet) { this.sweet = sweet; }

    public BigDecimal getSalty() { return salty; }
    public void setSalty(BigDecimal salty) { this.salty = salty; }

    public BigDecimal getSour() { return sour; }
    public void setSour(BigDecimal sour) { this.sour = sour; }

    public BigDecimal getBitter() { return bitter; }
    public void setBitter(BigDecimal bitter) { this.bitter = bitter; }

    public BigDecimal getUmami() { return umami; }
    public void setUmami(BigDecimal umami) { this.umami = umami; }

    public BigDecimal getSpicy() { return spicy; }
    public void setSpicy(BigDecimal spicy) { this.spicy = spicy; }

    public BigDecimal getNumbing() { return numbing; }
    public void setNumbing(BigDecimal numbing) { this.numbing = numbing; }

    public BigDecimal getFatAroma() { return fatAroma; }
    public void setFatAroma(BigDecimal fatAroma) { this.fatAroma = fatAroma; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
