package com.flavorlogic.dao;

import com.flavorlogic.model.RuleEvidence;

import java.sql.Connection;
import java.util.List;

/** 规则证据关联数据访问接口。 */
public interface RuleEvidenceDao {

    List<RuleEvidence> list(Connection conn, Long ruleId, Long knowledgeId, Long claimId);

    long insert(Connection conn, RuleEvidence link);

    int delete(Connection conn, Long id);
}
