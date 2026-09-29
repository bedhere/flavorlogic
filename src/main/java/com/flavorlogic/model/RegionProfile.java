package com.flavorlogic.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 区域口味画像（对应表 {@code region_profile}）。
 *
 * <p>权重为示例画像数据：1.000 表示该区域对该维度的偏好与全国基准一致，
 * 大于 1 表示偏好更强，小于 1 表示偏好更弱。</p>
 */
public class RegionProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String regionCode;
    private String regionName;
    private BigDecimal sweetWeight;
    private BigDecimal saltyWeight;
    private BigDecimal sourWeight;
    private BigDecimal bitterWeight;
    private BigDecimal umamiWeight;
    private BigDecimal spicyWeight;
    private BigDecimal numbingWeight;
    private BigDecimal fatAromaWeight;
    private String description;
    private String dataSource;
    private Integer status;
    private String createdAt;
    private String updatedAt;

    /**
     * 取指定维度的偏好权重。
     *
     * @param dimension 维度编码
     * @return 权重，缺失时返回 1
     */
    public BigDecimal weightOf(String dimension) {
        BigDecimal value;
        switch (dimension) {
            case FlavorDimensions.SWEET: value = sweetWeight; break;
            case FlavorDimensions.SALTY: value = saltyWeight; break;
            case FlavorDimensions.SOUR: value = sourWeight; break;
            case FlavorDimensions.BITTER: value = bitterWeight; break;
            case FlavorDimensions.UMAMI: value = umamiWeight; break;
            case FlavorDimensions.SPICY: value = spicyWeight; break;
            case FlavorDimensions.NUMBING: value = numbingWeight; break;
            case FlavorDimensions.FAT_AROMA: value = fatAromaWeight; break;
            default: value = null;
        }
        return value == null ? BigDecimal.ONE : value;
    }

    /**
     * 导出为“维度 → 权重”的映射，便于引擎加权计算。
     *
     * @return 有序权重映射
     */
    public Map<String, BigDecimal> toWeightMap() {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (String dimension : FlavorDimensions.ALL) {
            map.put(dimension, weightOf(dimension));
        }
        return map;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }

    public BigDecimal getSweetWeight() { return sweetWeight; }
    public void setSweetWeight(BigDecimal sweetWeight) { this.sweetWeight = sweetWeight; }

    public BigDecimal getSaltyWeight() { return saltyWeight; }
    public void setSaltyWeight(BigDecimal saltyWeight) { this.saltyWeight = saltyWeight; }

    public BigDecimal getSourWeight() { return sourWeight; }
    public void setSourWeight(BigDecimal sourWeight) { this.sourWeight = sourWeight; }

    public BigDecimal getBitterWeight() { return bitterWeight; }
    public void setBitterWeight(BigDecimal bitterWeight) { this.bitterWeight = bitterWeight; }

    public BigDecimal getUmamiWeight() { return umamiWeight; }
    public void setUmamiWeight(BigDecimal umamiWeight) { this.umamiWeight = umamiWeight; }

    public BigDecimal getSpicyWeight() { return spicyWeight; }
    public void setSpicyWeight(BigDecimal spicyWeight) { this.spicyWeight = spicyWeight; }

    public BigDecimal getNumbingWeight() { return numbingWeight; }
    public void setNumbingWeight(BigDecimal numbingWeight) { this.numbingWeight = numbingWeight; }

    public BigDecimal getFatAromaWeight() { return fatAromaWeight; }
    public void setFatAromaWeight(BigDecimal fatAromaWeight) { this.fatAromaWeight = fatAromaWeight; }

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
