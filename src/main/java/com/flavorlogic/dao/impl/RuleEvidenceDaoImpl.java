package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.dao.RuleEvidenceDao;
import com.flavorlogic.model.RuleEvidence;
import com.flavorlogic.util.DateTimeUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** 规则证据关联 JDBC 实现。 */
public class RuleEvidenceDaoImpl implements RuleEvidenceDao {

    private static final String COLUMNS =
            "r.id, r.rule_id, f.rule_code, r.knowledge_id, k.knowledge_code, r.evidence_claim_id, "
                    + "c.claim_text, c.document_id, d.title AS document_title, d.url AS document_url, "
                    + "c.review_status AS claim_review_status, d.review_status AS document_review_status, "
                    + "r.relation_type, r.created_at";
    private static final String FROM = " FROM rule_evidence r "
            + "LEFT JOIN flavor_rule f ON r.rule_id = f.id "
            + "LEFT JOIN flavor_knowledge k ON r.knowledge_id = k.id "
            + "JOIN evidence_claim c ON r.evidence_claim_id = c.id "
            + "JOIN source_document d ON c.document_id = d.id";
    private static final JdbcHelper.RowMapper<RuleEvidence> MAPPER = RuleEvidenceDaoImpl::map;

    @Override
    public List<RuleEvidence> list(Connection conn, Long ruleId, Long knowledgeId, Long claimId) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (ruleId != null) { where.append(" AND r.rule_id = ?"); params.add(ruleId); }
        if (knowledgeId != null) { where.append(" AND r.knowledge_id = ?"); params.add(knowledgeId); }
        if (claimId != null) { where.append(" AND r.evidence_claim_id = ?"); params.add(claimId); }
        return JdbcHelper.queryList(conn, "SELECT " + COLUMNS + FROM + where
                + " ORDER BY r.created_at DESC, r.id DESC", MAPPER, params.toArray());
    }

    @Override
    public long insert(Connection conn, RuleEvidence link) {
        return JdbcHelper.insertAndReturnKey(conn,
                "INSERT INTO rule_evidence (rule_id, knowledge_id, evidence_claim_id, relation_type) VALUES (?, ?, ?, ?)",
                link.getRuleId(), link.getKnowledgeId(), link.getEvidenceClaimId(), link.getRelationType());
    }

    @Override
    public int delete(Connection conn, Long id) {
        return JdbcHelper.update(conn, "DELETE FROM rule_evidence WHERE id = ?", id);
    }

    private static RuleEvidence map(ResultSet rs) throws SQLException {
        RuleEvidence link = new RuleEvidence();
        link.setId(JdbcHelper.getLong(rs, "id"));
        link.setRuleId(JdbcHelper.getLong(rs, "rule_id"));
        link.setRuleCode(rs.getString("rule_code"));
        link.setKnowledgeId(JdbcHelper.getLong(rs, "knowledge_id"));
        link.setKnowledgeCode(rs.getString("knowledge_code"));
        link.setEvidenceClaimId(JdbcHelper.getLong(rs, "evidence_claim_id"));
        link.setClaimText(rs.getString("claim_text"));
        link.setDocumentId(JdbcHelper.getLong(rs, "document_id"));
        link.setDocumentTitle(rs.getString("document_title"));
        link.setDocumentUrl(rs.getString("document_url"));
        link.setClaimReviewStatus(rs.getString("claim_review_status"));
        link.setDocumentReviewStatus(rs.getString("document_review_status"));
        link.setRelationType(rs.getString("relation_type"));
        link.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        return link;
    }
}
