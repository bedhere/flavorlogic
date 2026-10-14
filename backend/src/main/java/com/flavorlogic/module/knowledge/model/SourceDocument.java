package com.flavorlogic.module.knowledge.model;

import java.io.Serializable;

/**
 * 外部来源文档及其审核状态（对应表 {@code source_document}）。
 *
 * <p>文档正文是检索素材，不等于已批准的业务知识。只有审核状态为
 * {@code APPROVED} 的文档才允许进入默认检索结果；结构化规则仍需引用
 * {@link EvidenceItem} 对应的证据声明。</p>
 */
public class SourceDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String REVIEW_PENDING = "REVIEW_PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String ARCHIVED = "ARCHIVED";

    private Long id;
    private Long siteId;
    private Long crawlJobId;
    private String siteName;
    private String url;
    private String title;
    private String summary;
    private String contentText;
    private String contentHash;
    private String sourceType;
    private String licenseType;
    private String evidenceLevel;
    private String publishedAt;
    private String fetchedAt;
    private String reviewStatus;
    private Long reviewerId;
    private String reviewNote;
    private String filePath;
    private String yuxiKbId;
    private String yuxiFileId;
    private String yuxiStatus;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public Long getCrawlJobId() { return crawlJobId; }
    public void setCrawlJobId(Long crawlJobId) { this.crawlJobId = crawlJobId; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }
    public String getEvidenceLevel() { return evidenceLevel; }
    public void setEvidenceLevel(String evidenceLevel) { this.evidenceLevel = evidenceLevel; }
    public String getPublishedAt() { return publishedAt; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
    public String getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(String fetchedAt) { this.fetchedAt = fetchedAt; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getYuxiKbId() { return yuxiKbId; }
    public void setYuxiKbId(String yuxiKbId) { this.yuxiKbId = yuxiKbId; }
    public String getYuxiFileId() { return yuxiFileId; }
    public void setYuxiFileId(String yuxiFileId) { this.yuxiFileId = yuxiFileId; }
    public String getYuxiStatus() { return yuxiStatus; }
    public void setYuxiStatus(String yuxiStatus) { this.yuxiStatus = yuxiStatus; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
