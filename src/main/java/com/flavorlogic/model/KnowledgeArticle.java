package com.flavorlogic.model;

import java.io.Serializable;

/**
 * 研发知识文章（对应表 {@code knowledge_article}）。
 */
public class KnowledgeArticle implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：草稿 */
    public static final int STATUS_DRAFT = 0;
    /** 状态：已发布 */
    public static final int STATUS_PUBLISHED = 1;
    /** 状态：已下架 */
    public static final int STATUS_OFFLINE = 2;
    /** 状态：已删除（软删除） */
    public static final int STATUS_DELETED = 3;

    private Long id;
    private Long categoryId;
    /** 冗余字段：分类名称（列表展示用） */
    private String categoryName;
    private Long authorId;
    /** 冗余字段：发布人昵称（列表展示用） */
    private String authorName;
    private String title;
    private String summary;
    private String content;
    private String coverUrl;
    private String sourceName;
    private String sourceUrl;
    private String contentHash;
    private Integer status;
    private Integer viewCount;
    private String publishedAt;
    private String createdAt;
    private String updatedAt;

    /** 重复检查提示：admin 新增/编辑时返回给前端 */
    private transient String duplicateHint;

    public boolean isPublished() {
        return status != null && status == STATUS_PUBLISHED;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }

    public String getPublishedAt() { return publishedAt; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getDuplicateHint() { return duplicateHint; }
    public void setDuplicateHint(String duplicateHint) { this.duplicateHint = duplicateHint; }
}
