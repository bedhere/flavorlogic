package com.flavorlogic.dao;

import com.flavorlogic.model.CrawlJob;

import java.sql.Connection;
import java.util.List;

/** 受控采集任务数据访问接口。 */
public interface CrawlJobDao {

    List<CrawlJob> list(Connection conn, Long siteId, String status);

    CrawlJob findById(Connection conn, Long id);

    long insert(Connection conn, CrawlJob job);

    int markRunning(Connection conn, Long id, String startedAt);

    int finish(Connection conn, Long id, String status, String finishedAt,
               int fetchedCount, int successCount, int failedCount, String errorMessage);
}
