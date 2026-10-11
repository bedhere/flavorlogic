package com.flavorlogic.model;

import java.io.Serializable;

/** 规则/知识卡与审核证据声明的关联。 */
public class RuleEvidence implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long ruleId;
    private String ruleCode;
    private Long knowledgeId;
    private String knowledgeCode;
    private Long evidenceClaimId;
    private String claimText;
    private Long documentId;
    private String documentTitle;
    private String documentUrl;
    private String claimReviewStatus;
    private String documentReviewStatus;
    private String relationType;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public Long getKnowledgeId() { return knowledgeId; }
    public void setKnowledgeId(Long knowledgeId) { this.knowledgeId = knowledgeId; }
    public String getKnowledgeCode() { return knowledgeCode; }
    public void setKnowledgeCode(String knowledgeCode) { this.knowledgeCode = knowledgeCode; }
    public Long getEvidenceClaimId() { return evidenceClaimId; }
    public void setEvidenceClaimId(Long evidenceClaimId) { this.evidenceClaimId = evidenceClaimId; }
    public String getClaimText() { return claimText; }
    public void setClaimText(String claimText) { this.claimText = claimText; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getDocumentTitle() { return documentTitle; }
    public void setDocumentTitle(String documentTitle) { this.documentTitle = documentTitle; }
    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }
    public String getClaimReviewStatus() { return claimReviewStatus; }
    public void setClaimReviewStatus(String claimReviewStatus) { this.claimReviewStatus = claimReviewStatus; }
    public String getDocumentReviewStatus() { return documentReviewStatus; }
    public void setDocumentReviewStatus(String documentReviewStatus) { this.documentReviewStatus = documentReviewStatus; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
