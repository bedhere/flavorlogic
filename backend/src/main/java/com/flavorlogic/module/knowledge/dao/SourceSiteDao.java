package com.flavorlogic.module.knowledge.dao;

import com.flavorlogic.module.knowledge.model.SourceSite;

import java.sql.Connection;
import java.util.List;

/** 来源站点白名单数据访问接口。 */
public interface SourceSiteDao {

    List<SourceSite> list(Connection conn, boolean enabledOnly);

    SourceSite findById(Connection conn, Long id);

    SourceSite findByBaseUrl(Connection conn, String baseUrl);

    long insert(Connection conn, SourceSite site);

    int update(Connection conn, SourceSite site);
}
