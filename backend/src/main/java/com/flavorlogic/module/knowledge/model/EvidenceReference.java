package com.flavorlogic.module.knowledge.model;

import java.io.Serializable;

/**
 * 分析结果中展示的审核证据引用。
 *
 * <p>该对象只携带面向用户的追溯信息，不把审核表内部字段暴露到结果页。</p>
 */
public class EvidenceReference implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long claimId;
    private String claimText;
    private Long documentId;
    private String documentTitle;
    private String documentUrl;
    private String relationType;

    public Long getClaimId() { return claimId; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }
    public String getClaimText() { return claimText; }
    public void setClaimText(String claimText) { this.claimText = claimText; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getDocumentTitle() { return documentTitle; }
    public void setDocumentTitle(String documentTitle) { this.documentTitle = documentTitle; }
    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }
}
