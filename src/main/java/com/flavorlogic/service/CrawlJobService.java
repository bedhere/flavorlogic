package com.flavorlogic.service;

import com.flavorlogic.model.CrawlJob;

import java.util.List;

/** 受控资料采集任务业务接口。 */
public interface CrawlJobService {

    List<CrawlJob> list(Long siteId, String status);

    CrawlJob detail(Long id);

    CrawlJob create(Long operatorId, Long siteId, String seedUrl);

    CrawlJob run(Long operatorId, Long id);
}
