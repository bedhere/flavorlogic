package com.flavorlogic.service.impl;

import com.flavorlogic.dao.EvidenceClaimDao;
import com.flavorlogic.dao.IngredientDao;
import com.flavorlogic.dao.SourceDocumentDao;
import com.flavorlogic.dao.impl.EvidenceClaimDaoImpl;
import com.flavorlogic.dao.impl.IngredientDaoImpl;
import com.flavorlogic.dao.impl.SourceDocumentDaoImpl;
import com.flavorlogic.model.EvidenceClaim;
import com.flavorlogic.model.SourceDocument;
import com.flavorlogic.service.BaseService;
import com.flavorlogic.service.EvidenceClaimService;
import com.flavorlogic.util.BizException;
import com.flavorlogic.util.TextUtil;
import com.flavorlogic.util.ValidationUtil;

import java.math.BigDecimal;
import java.util.List;

/** 证据声明业务：声明可以先草拟，只有来源文档已通过时才可批准。 */
public class EvidenceClaimServiceImpl extends BaseService implements EvidenceClaimService {

    private final EvidenceClaimDao claimDao = new EvidenceClaimDaoImpl();
    private final SourceDocumentDao documentDao = new SourceDocumentDaoImpl();
    private final IngredientDao ingredientDao = new IngredientDaoImpl();

    @Override
    public List<EvidenceClaim> list(Long documentId, Long ingredientId, String reviewStatus) {
        return read(conn -> claimDao.list(conn, documentId, ingredientId,
                TextUtil.isBlank(reviewStatus) ? null : reviewStatus.trim()));
    }

    @Override
    public EvidenceClaim create(EvidenceClaim claim) {
        normalize(claim);
        return write(conn -> {
            SourceDocument document = documentDao.findById(conn, claim.getDocumentId());
            if (document == null) {
                throw BizException.notFound("来源文档不存在");
            }
            if (claim.getIngredientId() != null && ingredientDao.findById(conn, claim.getIngredientId()) == null) {
                throw BizException.notFound("关联食材不存在");
            }
            claim.setId(claimDao.insert(conn, claim));
            return claimDao.findById(conn, claim.getId());
        });
    }

    @Override
    public EvidenceClaim review(Long operatorId, Long id, String status, String note) {
        if (operatorId == null) {
            throw BizException.unauthorized("请先登录");
        }
        String safeStatus = ValidationUtil.requireOneOf(status, "审核状态",
                EvidenceClaim.REVIEW_PENDING, EvidenceClaim.APPROVED,
                EvidenceClaim.REJECTED, EvidenceClaim.ARCHIVED);
        String safeNote = ValidationUtil.optionalText(note, 1000, "审核意见");
        return write(conn -> {
            EvidenceClaim claim = claimDao.findById(conn, id);
            if (claim == null) {
                throw BizException.notFound("证据声明不存在");
            }
            SourceDocument document = documentDao.findById(conn, claim.getDocumentId());
            if (EvidenceClaim.APPROVED.equals(safeStatus)
                    && (document == null || !SourceDocument.APPROVED.equals(document.getReviewStatus()))) {
                throw BizException.conflict("来源文档尚未审核通过，证据声明不能批准");
            }
            if (EvidenceClaim.APPROVED.equals(safeStatus) && TextUtil.isBlank(claim.getQuoteText())) {
                throw BizException.conflict("证据声明批准前必须保留原文短摘录");
            }
            if (EvidenceClaim.APPROVED.equals(safeStatus) && "MEASUREMENT".equals(claim.getClaimType())
                    && TextUtil.isBlank(claim.getMethod())) {
                throw BizException.conflict("测量类声明批准前必须填写测量方法");
            }
            claimDao.updateReview(conn, id, safeStatus, operatorId, safeNote);
            return claimDao.findById(conn, id);
        });
    }

    private void normalize(EvidenceClaim claim) {
        if (claim == null) {
            throw BizException.badRequest("缺少证据声明内容");
        }
        if (claim.getDocumentId() == null) {
            throw BizException.badRequest("缺少来源文档编号");
        }
        if (claim.getIngredientId() != null && TextUtil.isBlank(claim.getDimension())) {
            throw BizException.badRequest("关联食材时必须填写风味维度");
        }
        if (claim.getIngredientId() == null && TextUtil.isBlank(claim.getDimension())) {
            // 背景知识可以不关联食材；但此时必须明确不是可直接改属性的声明。
            claim.setDimension(null);
        } else if (!TextUtil.isBlank(claim.getDimension())
                && !com.flavorlogic.model.FlavorDimensions.isDimension(claim.getDimension())) {
            throw BizException.badRequest("关联维度必须是八维之一");
        }
        claim.setClaimType(ValidationUtil.requireOneOf(TextUtil.defaultIfBlank(claim.getClaimType(), "KNOWLEDGE"),
                "声明类型", "KNOWLEDGE", "MEASUREMENT", "LEGAL", "PAIRING", "PRODUCT_LABEL"));
        claim.setClaimText(ValidationUtil.requireText(claim.getClaimText(), "结构化结论", 2000));
        claim.setQuoteText(ValidationUtil.optionalText(claim.getQuoteText(), 3000, "原文摘录"));
        claim.setAmountUnit(ValidationUtil.optionalText(claim.getAmountUnit(), 30, "用量单位"));
        claim.setMethod(ValidationUtil.optionalText(claim.getMethod(), 255, "测量方法"));
        claim.setEvidenceLevel(ValidationUtil.requireOneOf(
                TextUtil.defaultIfBlank(claim.getEvidenceLevel(), "UNVERIFIED"), "证据等级",
                "UNVERIFIED", "LITERATURE", "STANDARD", "EXPERIMENT", "EXPERIENCE", "DEMO"));
        if (claim.getAmountMin() != null || claim.getAmountMax() != null) {
            if (claim.getAmountMin() == null || claim.getAmountMax() == null) {
                throw BizException.badRequest("用量范围必须同时填写最小值和最大值");
            }
            if (claim.getAmountMin().compareTo(BigDecimal.ZERO) < 0
                    || claim.getAmountMax().compareTo(claim.getAmountMin()) < 0) {
                throw BizException.badRequest("用量范围不合法");
            }
            if (TextUtil.isBlank(claim.getAmountUnit())) {
                throw BizException.badRequest("填写用量范围时必须填写单位");
            }
        }
    }
}
