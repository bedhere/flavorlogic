package com.flavorlogic.model;

import java.io.Serializable;
import java.math.BigDecimal;

/** 可追溯的食材风味属性版本。 */
public class IngredientAttributeVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String REVIEW_PENDING = "REVIEW_PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String ARCHIVED = "ARCHIVED";

    private Long id;
    private Long ingredientId;
    private String ingredientName;
    private String dimension;
    private BigDecimal value;
    private String valueUnit;
    private String basis;
    private String concentration;
    private String temperature;
    private String measurementMethod;
    private String uncertainty;
    private Long sourceDocumentId;
    private String sourceDocumentTitle;
    private String evidenceLevel;
    private String reviewStatus;
    private Integer versionNo;
    private Long reviewerId;
    private String reviewNote;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIngredientId() { return ingredientId; }
    public void setIngredientId(Long ingredientId) { this.ingredientId = ingredientId; }
    public String getIngredientName() { return ingredientName; }
    public void setIngredientName(String ingredientName) { this.ingredientName = ingredientName; }
    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public String getValueUnit() { return valueUnit; }
    public void setValueUnit(String valueUnit) { this.valueUnit = valueUnit; }
    public String getBasis() { return basis; }
    public void setBasis(String basis) { this.basis = basis; }
    public String getConcentration() { return concentration; }
    public void setConcentration(String concentration) { this.concentration = concentration; }
    public String getTemperature() { return temperature; }
    public void setTemperature(String temperature) { this.temperature = temperature; }
    public String getMeasurementMethod() { return measurementMethod; }
    public void setMeasurementMethod(String measurementMethod) { this.measurementMethod = measurementMethod; }
    public String getUncertainty() { return uncertainty; }
    public void setUncertainty(String uncertainty) { this.uncertainty = uncertainty; }
    public Long getSourceDocumentId() { return sourceDocumentId; }
    public void setSourceDocumentId(Long sourceDocumentId) { this.sourceDocumentId = sourceDocumentId; }
    public String getSourceDocumentTitle() { return sourceDocumentTitle; }
    public void setSourceDocumentTitle(String sourceDocumentTitle) { this.sourceDocumentTitle = sourceDocumentTitle; }
    public String getEvidenceLevel() { return evidenceLevel; }
    public void setEvidenceLevel(String evidenceLevel) { this.evidenceLevel = evidenceLevel; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
