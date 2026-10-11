package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.IngredientAttributeVersionDao;
import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.model.IngredientAttributeVersion;
import com.flavorlogic.util.DateTimeUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** 食材属性版本 JDBC 实现。 */
public class IngredientAttributeVersionDaoImpl implements IngredientAttributeVersionDao {

    private static final String COLUMNS =
            "v.id, v.ingredient_id, i.name AS ingredient_name, v.dimension, v.value, v.value_unit, v.basis, "
                    + "v.concentration, v.temperature, v.measurement_method, v.uncertainty, v.source_document_id, "
                    + "d.title AS source_document_title, v.evidence_level, v.review_status, v.version_no, "
                    + "v.reviewer_id, v.review_note, v.created_at, v.updated_at";
    private static final String FROM = " FROM ingredient_attribute_version v JOIN ingredient i ON v.ingredient_id = i.id "
            + "LEFT JOIN source_document d ON v.source_document_id = d.id";
    private static final JdbcHelper.RowMapper<IngredientAttributeVersion> MAPPER = IngredientAttributeVersionDaoImpl::map;

    @Override
    public List<IngredientAttributeVersion> list(Connection conn, Long ingredientId, String dimension,
                                                  String reviewStatus) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (ingredientId != null) {
            where.append(" AND v.ingredient_id = ?");
            params.add(ingredientId);
        }
        if (dimension != null && !dimension.trim().isEmpty()) {
            where.append(" AND v.dimension = ?");
            params.add(dimension.trim());
        }
        if (reviewStatus != null && !reviewStatus.trim().isEmpty()) {
            where.append(" AND v.review_status = ?");
            params.add(reviewStatus.trim());
        }
        return JdbcHelper.queryList(conn, "SELECT " + COLUMNS + FROM + where
                + " ORDER BY v.ingredient_id, v.dimension, v.version_no DESC", MAPPER, params.toArray());
    }

    @Override
    public IngredientAttributeVersion findById(Connection conn, Long id) {
        return JdbcHelper.queryOne(conn, "SELECT " + COLUMNS + FROM + " WHERE v.id = ?", MAPPER, id);
    }

    @Override
    public int nextVersionNo(Connection conn, Long ingredientId, String dimension) {
        long current = JdbcHelper.queryLong(conn,
                "SELECT COALESCE(MAX(version_no), 0) FROM ingredient_attribute_version "
                        + "WHERE ingredient_id = ? AND dimension = ?", ingredientId, dimension);
        return (int) current + 1;
    }

    @Override
    public long insert(Connection conn, IngredientAttributeVersion version) {
        String sql = "INSERT INTO ingredient_attribute_version (ingredient_id, dimension, value, value_unit, basis, "
                + "concentration, temperature, measurement_method, uncertainty, source_document_id, evidence_level, "
                + "review_status, version_no) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return JdbcHelper.insertAndReturnKey(conn, sql, version.getIngredientId(), version.getDimension(),
                version.getValue(), version.getValueUnit(), version.getBasis(), version.getConcentration(),
                version.getTemperature(), version.getMeasurementMethod(), version.getUncertainty(),
                version.getSourceDocumentId(), version.getEvidenceLevel(), IngredientAttributeVersion.REVIEW_PENDING,
                version.getVersionNo());
    }

    @Override
    public int updateReview(Connection conn, Long id, String status, Long reviewerId, String note) {
        return JdbcHelper.update(conn,
                "UPDATE ingredient_attribute_version SET review_status = ?, reviewer_id = ?, review_note = ? WHERE id = ?",
                status, reviewerId, note, id);
    }

    private static IngredientAttributeVersion map(ResultSet rs) throws SQLException {
        IngredientAttributeVersion version = new IngredientAttributeVersion();
        version.setId(JdbcHelper.getLong(rs, "id"));
        version.setIngredientId(JdbcHelper.getLong(rs, "ingredient_id"));
        version.setIngredientName(rs.getString("ingredient_name"));
        version.setDimension(rs.getString("dimension"));
        version.setValue(rs.getBigDecimal("value"));
        version.setValueUnit(rs.getString("value_unit"));
        version.setBasis(rs.getString("basis"));
        version.setConcentration(rs.getString("concentration"));
        version.setTemperature(rs.getString("temperature"));
        version.setMeasurementMethod(rs.getString("measurement_method"));
        version.setUncertainty(rs.getString("uncertainty"));
        version.setSourceDocumentId(JdbcHelper.getLong(rs, "source_document_id"));
        version.setSourceDocumentTitle(rs.getString("source_document_title"));
        version.setEvidenceLevel(rs.getString("evidence_level"));
        version.setReviewStatus(rs.getString("review_status"));
        version.setVersionNo(JdbcHelper.getInteger(rs, "version_no"));
        version.setReviewerId(JdbcHelper.getLong(rs, "reviewer_id"));
        version.setReviewNote(rs.getString("review_note"));
        version.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        version.setUpdatedAt(DateTimeUtil.format(rs.getTimestamp("updated_at")));
        return version;
    }
}
