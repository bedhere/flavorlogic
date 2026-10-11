package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.CrawlJobDao;
import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.model.CrawlJob;
import com.flavorlogic.util.DateTimeUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** 受控采集任务 JDBC 实现。 */
public class CrawlJobDaoImpl implements CrawlJobDao {

    private static final String COLUMNS =
            "j.id, j.site_id, s.site_name, j.seed_url, j.status, j.started_at, j.finished_at, "
                    + "j.fetched_count, j.success_count, j.failed_count, j.error_message, j.created_by, "
                    + "j.created_at, j.updated_at";
    private static final String FROM = " FROM crawl_job j JOIN source_site s ON j.site_id = s.id";
    private static final JdbcHelper.RowMapper<CrawlJob> MAPPER = CrawlJobDaoImpl::map;

    @Override
    public List<CrawlJob> list(Connection conn, Long siteId, String status) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (siteId != null) {
            where.append(" AND j.site_id = ?");
            params.add(siteId);
        }
        if (status != null && !status.trim().isEmpty()) {
            where.append(" AND j.status = ?");
            params.add(status.trim());
        }
        return JdbcHelper.queryList(conn, "SELECT " + COLUMNS + FROM + where
                + " ORDER BY j.created_at DESC, j.id DESC", MAPPER, params.toArray());
    }

    @Override
    public CrawlJob findById(Connection conn, Long id) {
        return JdbcHelper.queryOne(conn, "SELECT " + COLUMNS + FROM + " WHERE j.id = ?", MAPPER, id);
    }

    @Override
    public long insert(Connection conn, CrawlJob job) {
        String sql = "INSERT INTO crawl_job (site_id, seed_url, status, fetched_count, success_count, "
                + "failed_count, created_by) VALUES (?, ?, ?, 0, 0, 0, ?)";
        return JdbcHelper.insertAndReturnKey(conn, sql, job.getSiteId(), job.getSeedUrl(),
                CrawlJob.PENDING, job.getCreatedBy());
    }

    @Override
    public int markRunning(Connection conn, Long id, String startedAt) {
        return JdbcHelper.update(conn,
                "UPDATE crawl_job SET status = ?, started_at = ?, finished_at = NULL, error_message = NULL "
                        + "WHERE id = ? AND status = ?",
                CrawlJob.RUNNING, DateTimeUtil.toTimestamp(startedAt), id, CrawlJob.PENDING);
    }

    @Override
    public int finish(Connection conn, Long id, String status, String finishedAt,
                      int fetchedCount, int successCount, int failedCount, String errorMessage) {
        return JdbcHelper.update(conn,
                "UPDATE crawl_job SET status = ?, finished_at = ?, fetched_count = ?, success_count = ?, "
                        + "failed_count = ?, error_message = ? WHERE id = ?",
                status, DateTimeUtil.toTimestamp(finishedAt), fetchedCount, successCount, failedCount,
                errorMessage, id);
    }

    private static CrawlJob map(ResultSet rs) throws SQLException {
        CrawlJob job = new CrawlJob();
        job.setId(JdbcHelper.getLong(rs, "id"));
        job.setSiteId(JdbcHelper.getLong(rs, "site_id"));
        job.setSiteName(rs.getString("site_name"));
        job.setSeedUrl(rs.getString("seed_url"));
        job.setStatus(rs.getString("status"));
        job.setStartedAt(DateTimeUtil.format(rs.getTimestamp("started_at")));
        job.setFinishedAt(DateTimeUtil.format(rs.getTimestamp("finished_at")));
        job.setFetchedCount(JdbcHelper.getInteger(rs, "fetched_count"));
        job.setSuccessCount(JdbcHelper.getInteger(rs, "success_count"));
        job.setFailedCount(JdbcHelper.getInteger(rs, "failed_count"));
        job.setErrorMessage(rs.getString("error_message"));
        job.setCreatedBy(JdbcHelper.getLong(rs, "created_by"));
        job.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        job.setUpdatedAt(DateTimeUtil.format(rs.getTimestamp("updated_at")));
        return job;
    }
}
