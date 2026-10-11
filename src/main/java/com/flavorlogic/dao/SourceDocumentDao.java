package com.flavorlogic.dao;

import com.flavorlogic.model.EvidenceItem;
import com.flavorlogic.model.SourceDocument;

import java.sql.Connection;
import java.util.List;

/** 外部来源文档、审核状态与全文检索数据访问接口。 */
public interface SourceDocumentDao {

    List<SourceDocument> page(Connection conn, String keyword, String reviewStatus, int offset, int limit);

    long count(Connection conn, String keyword, String reviewStatus);

    SourceDocument findById(Connection conn, Long id);

    SourceDocument findByUrl(Connection conn, String url);

    SourceDocument findByHash(Connection conn, String contentHash);

    long insert(Connection conn, SourceDocument document);

    int updateReview(Connection conn, Long id, String status, Long reviewerId, String note);

    int updateYuxiStatus(Connection conn, Long id, String kbId, String fileId, String status);

    /** 只返回审核通过的检索片段，默认走 MySQL FULLTEXT。 */
    List<EvidenceItem> searchApproved(Connection conn, String query, int limit);
}
