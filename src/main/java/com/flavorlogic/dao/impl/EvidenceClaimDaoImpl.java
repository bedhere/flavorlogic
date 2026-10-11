package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.EvidenceClaimDao;
import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.model.EvidenceClaim;
import com.flavorlogic.util.DateTimeUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** 证据声明 JDBC 实现。 */
public class EvidenceClaimDaoImpl implements EvidenceClaimDao {

    private static final String COLUMNS =
            "c.id, c.document_id, d.title AS document_title, c.ingredient_id, i.name AS ingredient_name, "
                    + "c.dimension, c.claim_type, c.claim_text, c.quote_text, c.amount_min, c.amount_max, "
                    + "c.amount_unit, c.method, c.evidence_level, c.review_status, c.reviewer_id, "
                    + "c.review_note, c.created_at, c.updated_at";
    private static final String FROM = " FROM evidence_claim c JOIN source_document d ON c.document_id = d.id "
            + "LEFT JOIN ingredient i ON c.ingredient_id = i.id";
    private static final JdbcHelper.RowMapper<EvidenceClaim> MAPPER = EvidenceClaimDaoImpl::map;

    @Override
    public List<EvidenceClaim> list(Connection conn, Long documentId, Long ingredientId, String reviewStatus) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (documentId != null) {
            where.append(" AND c.document_id = ?");
            params.add(documentId);
        }
        if (ingredientId != null) {
            where.append(" AND c.ingredient_id = ?");
            params.add(ingredientId);
        }
        if (reviewStatus != null && !reviewStatus.trim().isEmpty()) {
            where.append(" AND c.review_status = ?");
            params.add(reviewStatus.trim());
        }
        return JdbcHelper.queryList(conn, "SELECT " + COLUMNS + FROM + where
                + " ORDER BY c.updated_at DESC, c.id DESC", MAPPER, params.toArray());
    }

    @Override
    public EvidenceClaim findById(Connection conn, Long id) {
        return JdbcHelper.queryOne(conn, "SELECT " + COLUMNS + FROM + " WHERE c.id = ?", MAPPER, id);
    }

    @Override
    public long insert(Connection conn, EvidenceClaim claim) {
        String sql = "INSERT INTO evidence_claim (document_id, ingredient_id, dimension, claim_type, claim_text, "
                + "quote_text, amount_min, amount_max, amount_unit, method, evidence_level, review_status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return JdbcHelper.insertAndReturnKey(conn, sql, claim.getDocumentId(), claim.getIngredientId(),
                claim.getDimension(), claim.getClaimType(), claim.getClaimText(), claim.getQuoteText(),
                claim.getAmountMin(), claim.getAmountMax(), claim.getAmountUnit(), claim.getMethod(),
                claim.getEvidenceLevel(), EvidenceClaim.REVIEW_PENDING);
    }

    @Override
    public int updateReview(Connection conn, Long id, String status, Long reviewerId, String note) {
        return JdbcHelper.update(conn,
                "UPDATE evidence_claim SET review_status = ?, reviewer_id = ?, review_note = ? WHERE id = ?",
                status, reviewerId, note, id);
    }

    private static EvidenceClaim map(ResultSet rs) throws SQLException {
        EvidenceClaim claim = new EvidenceClaim();
        claim.setId(JdbcHelper.getLong(rs, "id"));
        claim.setDocumentId(JdbcHelper.getLong(rs, "document_id"));
        claim.setDocumentTitle(rs.getString("document_title"));
        claim.setIngredientId(JdbcHelper.getLong(rs, "ingredient_id"));
        claim.setIngredientName(rs.getString("ingredient_name"));
        claim.setDimension(rs.getString("dimension"));
        claim.setClaimType(rs.getString("claim_type"));
        claim.setClaimText(rs.getString("claim_text"));
        claim.setQuoteText(rs.getString("quote_text"));
        claim.setAmountMin(rs.getBigDecimal("amount_min"));
        claim.setAmountMax(rs.getBigDecimal("amount_max"));
        claim.setAmountUnit(rs.getString("amount_unit"));
        claim.setMethod(rs.getString("method"));
        claim.setEvidenceLevel(rs.getString("evidence_level"));
        claim.setReviewStatus(rs.getString("review_status"));
        claim.setReviewerId(JdbcHelper.getLong(rs, "reviewer_id"));
        claim.setReviewNote(rs.getString("review_note"));
        claim.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        claim.setUpdatedAt(DateTimeUtil.format(rs.getTimestamp("updated_at")));
        return claim;
    }
}
