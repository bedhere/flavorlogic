package com.flavorlogic.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 面向检索调用方的证据片段。它只暴露审核通过的来源信息，避免把原始抓取记录
 * 直接当成规则或建议输出。
 */
public class EvidenceItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long documentId;
    private String title;
    private String url;
    private String summary;
    private String excerpt;
    private String sourceType;
    private String licenseType;
    private String evidenceLevel;
    private BigDecimal score;

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }
    public String getEvidenceLevel() { return evidenceLevel; }
    public void setEvidenceLevel(String evidenceLevel) { this.evidenceLevel = evidenceLevel; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
}
