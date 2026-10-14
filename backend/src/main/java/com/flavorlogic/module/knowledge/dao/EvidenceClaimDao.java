package com.flavorlogic.module.knowledge.dao;

import com.flavorlogic.module.knowledge.model.EvidenceClaim;

import java.sql.Connection;
import java.util.List;

/** 证据声明数据访问接口。 */
public interface EvidenceClaimDao {

    List<EvidenceClaim> list(Connection conn, Long documentId, Long ingredientId, String reviewStatus);

    EvidenceClaim findById(Connection conn, Long id);

    long insert(Connection conn, EvidenceClaim claim);

    int updateReview(Connection conn, Long id, String status, Long reviewerId, String note);
}
