package com.flavorlogic.service;

import com.flavorlogic.model.RuleEvidence;

import java.util.List;

/** 规则/知识卡证据关联业务。 */
public interface RuleEvidenceService {

    List<RuleEvidence> list(Long ruleId, Long knowledgeId, Long claimId);

    RuleEvidence link(RuleEvidence link);

    void unlink(Long id);
}
