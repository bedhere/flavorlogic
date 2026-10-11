package com.flavorlogic.model;

import java.io.Serializable;

/** 外部资料来源站点白名单（对应表 {@code source_site}）。 */
public class SourceSite implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String siteName;
    private String baseUrl;
    private String robotsUrl;
    private String termsUrl;
    private String licenseType;
    private String sourceType;
    private Integer crawlAllowed;
    private Integer requestIntervalMs;
    private Integer status;
    private String notes;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getRobotsUrl() { return robotsUrl; }
    public void setRobotsUrl(String robotsUrl) { this.robotsUrl = robotsUrl; }
    public String getTermsUrl() { return termsUrl; }
    public void setTermsUrl(String termsUrl) { this.termsUrl = termsUrl; }
    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Integer getCrawlAllowed() { return crawlAllowed; }
    public void setCrawlAllowed(Integer crawlAllowed) { this.crawlAllowed = crawlAllowed; }
    public Integer getRequestIntervalMs() { return requestIntervalMs; }
    public void setRequestIntervalMs(Integer requestIntervalMs) { this.requestIntervalMs = requestIntervalMs; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
