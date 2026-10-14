package com.flavorlogic.module.knowledge.dao.impl;

import com.flavorlogic.common.persistence.JdbcHelper;
import com.flavorlogic.module.knowledge.dao.SourceSiteDao;
import com.flavorlogic.module.knowledge.model.SourceSite;
import com.flavorlogic.common.util.DateTimeUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** 来源站点白名单 JDBC 实现。 */
public class SourceSiteDaoImpl implements SourceSiteDao {

    private static final String COLUMNS =
            "s.id, s.site_name, s.base_url, s.robots_url, s.terms_url, s.license_type, "
                    + "s.source_type, s.crawl_allowed, s.request_interval_ms, s.status, s.notes, "
                    + "s.created_at, s.updated_at";
    private static final String FROM = " FROM source_site s";
    private static final JdbcHelper.RowMapper<SourceSite> MAPPER = SourceSiteDaoImpl::map;

    @Override
    public List<SourceSite> list(Connection conn, boolean enabledOnly) {
        String where = enabledOnly ? " WHERE s.status = 1" : "";
        return JdbcHelper.queryList(conn, "SELECT " + COLUMNS + FROM + where
                + " ORDER BY s.status DESC, s.site_name, s.id", MAPPER);
    }

    @Override
    public SourceSite findById(Connection conn, Long id) {
        return JdbcHelper.queryOne(conn, "SELECT " + COLUMNS + FROM + " WHERE s.id = ?", MAPPER, id);
    }

    @Override
    public SourceSite findByBaseUrl(Connection conn, String baseUrl) {
        return JdbcHelper.queryOne(conn, "SELECT " + COLUMNS + FROM + " WHERE s.base_url = ? LIMIT 1",
                MAPPER, baseUrl);
    }

    @Override
    public long insert(Connection conn, SourceSite site) {
        String sql = "INSERT INTO source_site (site_name, base_url, robots_url, terms_url, license_type, "
                + "source_type, crawl_allowed, request_interval_ms, status, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return JdbcHelper.insertAndReturnKey(conn, sql, site.getSiteName(), site.getBaseUrl(), site.getRobotsUrl(),
                site.getTermsUrl(), site.getLicenseType(), site.getSourceType(), site.getCrawlAllowed(),
                site.getRequestIntervalMs(), site.getStatus(), site.getNotes());
    }

    @Override
    public int update(Connection conn, SourceSite site) {
        String sql = "UPDATE source_site SET site_name = ?, base_url = ?, robots_url = ?, terms_url = ?, "
                + "license_type = ?, source_type = ?, crawl_allowed = ?, request_interval_ms = ?, status = ?, notes = ? "
                + "WHERE id = ?";
        return JdbcHelper.update(conn, sql, site.getSiteName(), site.getBaseUrl(), site.getRobotsUrl(),
                site.getTermsUrl(), site.getLicenseType(), site.getSourceType(), site.getCrawlAllowed(),
                site.getRequestIntervalMs(), site.getStatus(), site.getNotes(), site.getId());
    }

    private static SourceSite map(ResultSet rs) throws SQLException {
        SourceSite site = new SourceSite();
        site.setId(JdbcHelper.getLong(rs, "id"));
        site.setSiteName(rs.getString("site_name"));
        site.setBaseUrl(rs.getString("base_url"));
        site.setRobotsUrl(rs.getString("robots_url"));
        site.setTermsUrl(rs.getString("terms_url"));
        site.setLicenseType(rs.getString("license_type"));
        site.setSourceType(rs.getString("source_type"));
        site.setCrawlAllowed(JdbcHelper.getInteger(rs, "crawl_allowed"));
        site.setRequestIntervalMs(JdbcHelper.getInteger(rs, "request_interval_ms"));
        site.setStatus(JdbcHelper.getInteger(rs, "status"));
        site.setNotes(rs.getString("notes"));
        site.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        site.setUpdatedAt(DateTimeUtil.format(rs.getTimestamp("updated_at")));
        return site;
    }
}
