package com.flavorlogic.module.knowledge.service;

import com.flavorlogic.module.knowledge.model.SourceSite;

import java.util.List;

/** 来源站点白名单业务接口。 */
public interface SourceSiteService {

    List<SourceSite> list(boolean enabledOnly);

    SourceSite detail(Long id);

    SourceSite create(SourceSite site);

    SourceSite update(Long id, SourceSite site);
}
