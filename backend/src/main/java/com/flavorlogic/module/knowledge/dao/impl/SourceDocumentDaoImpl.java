package com.flavorlogic.module.knowledge.dao.impl;

import com.flavorlogic.common.persistence.JdbcHelper;
import com.flavorlogic.module.knowledge.dao.SourceDocumentDao;
import com.flavorlogic.module.knowledge.model.EvidenceItem;
import com.flavorlogic.module.knowledge.model.SourceDocument;
import com.flavorlogic.common.util.DateTimeUtil;
import com.flavorlogic.common.util.TextUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** MySQL 实现：审核文档使用 FULLTEXT ngram 检索，避免引入额外搜索服务。 */
public class SourceDocumentDaoImpl implements SourceDocumentDao {

    private static final String COLUMNS =
            "d.id, d.site_id, d.crawl_job_id, s.site_name, d.url, d.title, d.summary, "
                    + "d.content_text, d.content_hash, d.source_type, d.license_type, d.evidence_level, "
                    + "d.published_at, d.fetched_at, d.review_status, d.reviewer_id, d.review_note, "
                    + "d.file_path, d.yuxi_kb_id, d.yuxi_file_id, d.yuxi_status, d.created_at, d.updated_at";

    private static final String FROM = " FROM source_document d LEFT JOIN source_site s ON d.site_id = s.id";

    private static final JdbcHelper.RowMapper<SourceDocument> MAPPER = SourceDocumentDaoImpl::mapDocument;

    @Override
    public List<SourceDocument> page(Connection conn, String keyword, String reviewStatus, int offset, int limit) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(keyword, reviewStatus, params);
        String sql = "SELECT " + COLUMNS + FROM + where
                + " ORDER BY d.updated_at DESC, d.id DESC LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        return JdbcHelper.queryList(conn, sql, MAPPER, params.toArray());
    }

    @Override
    public long count(Connection conn, String keyword, String reviewStatus) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(keyword, reviewStatus, params);
        return JdbcHelper.queryLong(conn, "SELECT COUNT(*) FROM source_document d" + where, params.toArray());
    }

    @Override
    public SourceDocument findById(Connection conn, Long id) {
        return JdbcHelper.queryOne(conn, "SELECT " + COLUMNS + FROM + " WHERE d.id = ?", MAPPER, id);
    }

    @Override
    public SourceDocument findByUrl(Connection conn, String url) {
        return JdbcHelper.queryOne(conn, "SELECT " + COLUMNS + FROM + " WHERE d.url = ? LIMIT 1", MAPPER, url);
    }

    @Override
    public SourceDocument findByHash(Connection conn, String contentHash) {
        return JdbcHelper.queryOne(conn, "SELECT " + COLUMNS + FROM + " WHERE d.content_hash = ? LIMIT 1",
                MAPPER, contentHash);
    }

    @Override
    public long insert(Connection conn, SourceDocument document) {
        String sql = "INSERT INTO source_document (site_id, crawl_job_id, url, title, summary, content_text, "
                + "content_hash, source_type, license_type, evidence_level, published_at, fetched_at, "
                + "review_status, reviewer_id, review_note, file_path, yuxi_kb_id, yuxi_file_id, yuxi_status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return JdbcHelper.insertAndReturnKey(conn, sql,
                document.getSiteId(), document.getCrawlJobId(), document.getUrl(), document.getTitle(),
                document.getSummary(), document.getContentText(), document.getContentHash(), document.getSourceType(),
                document.getLicenseType(), document.getEvidenceLevel(), DateTimeUtil.toTimestamp(document.getPublishedAt()),
                DateTimeUtil.toTimestamp(document.getFetchedAt()), document.getReviewStatus(), document.getReviewerId(),
                document.getReviewNote(), document.getFilePath(), document.getYuxiKbId(), document.getYuxiFileId(),
                document.getYuxiStatus());
    }

    @Override
    public int updateReview(Connection conn, Long id, String status, Long reviewerId, String note) {
        return JdbcHelper.update(conn,
                "UPDATE source_document SET review_status = ?, reviewer_id = ?, review_note = ? WHERE id = ?",
                status, reviewerId, note, id);
    }

    @Override
    public int updateYuxiStatus(Connection conn, Long id, String kbId, String fileId, String status) {
        return JdbcHelper.update(conn,
                "UPDATE source_document SET yuxi_kb_id = ?, yuxi_file_id = ?, yuxi_status = ? WHERE id = ?",
                kbId, fileId, status, id);
    }

    @Override
    public List<EvidenceItem> searchApproved(Connection conn, String query, int limit) {
        String text = query == null ? "" : query.trim();
        if (text.isEmpty()) {
            return new ArrayList<>();
        }
        String sql = "SELECT d.id AS document_id, d.title, d.url, d.summary, "
                + "SUBSTRING(d.content_text, 1, 800) AS excerpt, d.source_type, d.license_type, "
                + "d.evidence_level, MATCH(d.title, d.summary, d.content_text) "
                + "AGAINST (? IN NATURAL LANGUAGE MODE) AS score "
                + "FROM source_document d WHERE d.review_status = 'APPROVED' "
                + "AND MATCH(d.title, d.summary, d.content_text) AGAINST (? IN NATURAL LANGUAGE MODE) "
                + "ORDER BY score DESC, d.updated_at DESC LIMIT ?";
        return JdbcHelper.queryList(conn, sql, rs -> mapEvidence(rs), text, text, limit);
    }

    private String buildWhere(String keyword, String reviewStatus, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (!TextUtil.isBlank(keyword)) {
            where.append(" AND MATCH(d.title, d.summary, d.content_text) AGAINST (? IN NATURAL LANGUAGE MODE)");
            params.add(keyword.trim());
        }
        if (!TextUtil.isBlank(reviewStatus)) {
            where.append(" AND d.review_status = ?");
            params.add(reviewStatus.trim());
        }
        return where.toString();
    }

    private static SourceDocument mapDocument(ResultSet rs) throws SQLException {
        SourceDocument document = new SourceDocument();
        document.setId(JdbcHelper.getLong(rs, "id"));
        document.setSiteId(JdbcHelper.getLong(rs, "site_id"));
        document.setCrawlJobId(JdbcHelper.getLong(rs, "crawl_job_id"));
        document.setSiteName(rs.getString("site_name"));
        document.setUrl(rs.getString("url"));
        document.setTitle(rs.getString("title"));
        document.setSummary(rs.getString("summary"));
        document.setContentText(rs.getString("content_text"));
        document.setContentHash(rs.getString("content_hash"));
        document.setSourceType(rs.getString("source_type"));
        document.setLicenseType(rs.getString("license_type"));
        document.setEvidenceLevel(rs.getString("evidence_level"));
        document.setPublishedAt(DateTimeUtil.format(rs.getTimestamp("published_at")));
        document.setFetchedAt(DateTimeUtil.format(rs.getTimestamp("fetched_at")));
        document.setReviewStatus(rs.getString("review_status"));
        document.setReviewerId(JdbcHelper.getLong(rs, "reviewer_id"));
        document.setReviewNote(rs.getString("review_note"));
        document.setFilePath(rs.getString("file_path"));
        document.setYuxiKbId(rs.getString("yuxi_kb_id"));
        document.setYuxiFileId(rs.getString("yuxi_file_id"));
        document.setYuxiStatus(rs.getString("yuxi_status"));
        document.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        document.setUpdatedAt(DateTimeUtil.format(rs.getTimestamp("updated_at")));
        return document;
    }

    private static EvidenceItem mapEvidence(ResultSet rs) throws SQLException {
        EvidenceItem item = new EvidenceItem();
        item.setDocumentId(JdbcHelper.getLong(rs, "document_id"));
        item.setTitle(rs.getString("title"));
        item.setUrl(rs.getString("url"));
        item.setSummary(rs.getString("summary"));
        item.setExcerpt(rs.getString("excerpt"));
        item.setSourceType(rs.getString("source_type"));
        item.setLicenseType(rs.getString("license_type"));
        item.setEvidenceLevel(rs.getString("evidence_level"));
        item.setScore(rs.getBigDecimal("score"));
        return item;
    }
}
