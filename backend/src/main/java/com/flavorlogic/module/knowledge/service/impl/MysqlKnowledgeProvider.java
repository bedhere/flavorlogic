package com.flavorlogic.module.knowledge.service.impl;

import com.flavorlogic.module.knowledge.dao.SourceDocumentDao;
import com.flavorlogic.module.knowledge.dao.impl.SourceDocumentDaoImpl;
import com.flavorlogic.module.knowledge.model.EvidenceItem;
import com.flavorlogic.common.service.BaseService;
import com.flavorlogic.module.knowledge.service.KnowledgeProvider;

import java.util.Collections;
import java.util.List;

/** MySQL 本地检索实现；不调用外部网络，也不依赖向量数据库。 */
public class MysqlKnowledgeProvider extends BaseService implements KnowledgeProvider {

    private final SourceDocumentDao documentDao = new SourceDocumentDaoImpl();

    @Override
    public List<EvidenceItem> retrieve(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        return read(conn -> documentDao.searchApproved(conn, query.trim(), safeLimit));
    }
}
