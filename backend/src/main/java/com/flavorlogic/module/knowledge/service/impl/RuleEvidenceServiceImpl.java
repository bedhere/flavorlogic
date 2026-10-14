package com.flavorlogic.module.knowledge.service.impl;

import com.flavorlogic.module.knowledge.dao.EvidenceClaimDao;
import com.flavorlogic.module.analysis.dao.FlavorKnowledgeDao;
import com.flavorlogic.module.analysis.dao.FlavorRuleDao;
import com.flavorlogic.module.knowledge.dao.RuleEvidenceDao;
import com.flavorlogic.module.knowledge.dao.impl.EvidenceClaimDaoImpl;
import com.flavorlogic.module.analysis.dao.impl.FlavorKnowledgeDaoImpl;
import com.flavorlogic.module.analysis.dao.impl.FlavorRuleDaoImpl;
import com.flavorlogic.module.knowledge.dao.impl.RuleEvidenceDaoImpl;
import com.flavorlogic.module.knowledge.model.EvidenceClaim;
import com.flavorlogic.module.knowledge.model.RuleEvidence;
import com.flavorlogic.common.service.BaseService;
import com.flavorlogic.module.knowledge.service.RuleEvidenceService;
import com.flavorlogic.common.util.BizException;
import com.flavorlogic.common.util.TextUtil;
import com.flavorlogic.common.util.ValidationUtil;

import java.util.List;

/** 规则证据关联业务：只允许关联已批准声明。 */
public class RuleEvidenceServiceImpl extends BaseService implements RuleEvidenceService {

    private final RuleEvidenceDao linkDao = new RuleEvidenceDaoImpl();
    private final EvidenceClaimDao claimDao = new EvidenceClaimDaoImpl();
    private final FlavorRuleDao ruleDao = new FlavorRuleDaoImpl();
    private final FlavorKnowledgeDao knowledgeDao = new FlavorKnowledgeDaoImpl();

    @Override
    public List<RuleEvidence> list(Long ruleId, Long knowledgeId, Long claimId) {
        return read(conn -> linkDao.list(conn, ruleId, knowledgeId, claimId));
    }

    @Override
    public RuleEvidence link(RuleEvidence link) {
        if (link == null || link.getEvidenceClaimId() == null) {
            throw BizException.badRequest("缺少证据声明编号");
        }
        if ((link.getRuleId() == null) == (link.getKnowledgeId() == null)) {
            throw BizException.badRequest("必须且只能关联一个规则或知识卡");
        }
        link.setRelationType(ValidationUtil.requireOneOf(
                TextUtil.defaultIfBlank(link.getRelationType(), "SUPPORTS"), "关联类型",
                "SUPPORTS", "LIMITS", "CONTEXT_ONLY"));
        return write(conn -> {
            EvidenceClaim claim = claimDao.findById(conn, link.getEvidenceClaimId());
            if (claim == null || !EvidenceClaim.APPROVED.equals(claim.getReviewStatus())) {
                throw BizException.conflict("只有 APPROVED 证据声明才能关联规则");
            }
            if (link.getRuleId() != null && ruleDao.findById(conn, link.getRuleId()) == null) {
                throw BizException.notFound("风味规则不存在");
            }
            if (link.getKnowledgeId() != null && knowledgeDao.findById(conn, link.getKnowledgeId()) == null) {
                throw BizException.notFound("知识卡不存在");
            }
            link.setId(linkDao.insert(conn, link));
            return linkDao.list(conn, link.getRuleId(), link.getKnowledgeId(), link.getEvidenceClaimId())
                    .stream().filter(item -> link.getId().equals(item.getId())).findFirst().orElse(link);
        });
    }

    @Override
    public void unlink(Long id) {
        if (id == null) {
            throw BizException.badRequest("缺少关联编号");
        }
        writeVoid(conn -> {
            if (linkDao.delete(conn, id) == 0) {
                throw BizException.notFound("规则证据关联不存在");
            }
        });
    }
}
