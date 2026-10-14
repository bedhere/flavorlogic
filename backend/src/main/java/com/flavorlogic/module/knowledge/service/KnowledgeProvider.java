package com.flavorlogic.module.knowledge.service;

import com.flavorlogic.module.knowledge.model.EvidenceItem;

import java.util.List;

/**
 * 知识检索抽象。当前默认实现使用 MySQL FULLTEXT；以后接入 Yuxi 时只替换实现，
 * 不改变 Servlet、规则引擎和审核数据模型。
 */
public interface KnowledgeProvider {

    /**
     * 检索审核通过的证据片段。
     *
     * @param query 检索词
     * @param limit 最大返回条数
     * @return 带来源的证据片段
     */
    List<EvidenceItem> retrieve(String query, int limit);
}
