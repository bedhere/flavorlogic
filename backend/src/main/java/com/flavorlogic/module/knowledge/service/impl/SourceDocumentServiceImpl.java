package com.flavorlogic.module.knowledge.service.impl;
import com.flavorlogic.common.util.DateTimeUtil;

import com.flavorlogic.module.knowledge.dao.SourceDocumentDao;
import com.flavorlogic.module.knowledge.dao.SourceSiteDao;
import com.flavorlogic.module.knowledge.dao.impl.SourceDocumentDaoImpl;
import com.flavorlogic.module.knowledge.dao.impl.SourceSiteDaoImpl;
import com.flavorlogic.module.knowledge.model.EvidenceItem;
import com.flavorlogic.module.knowledge.model.SourceDocument;
import com.flavorlogic.common.service.BaseService;
import com.flavorlogic.module.knowledge.service.KnowledgeProvider;
import com.flavorlogic.module.knowledge.service.SourceDocumentService;
import com.flavorlogic.common.util.BizException;
import com.flavorlogic.common.util.PageResult;
import com.flavorlogic.common.util.TextUtil;
import com.flavorlogic.common.util.ValidationUtil;

import java.util.Collections;
import java.util.List;

/** 来源文档业务：先入审核队列，再允许检索；Yuxi 字段仅作为可选同步状态。 */
public class SourceDocumentServiceImpl extends BaseService implements SourceDocumentService {

    private static final String DEFAULT_SOURCE_TYPE = "WEB";
    private static final String DEFAULT_EVIDENCE_LEVEL = "UNVERIFIED";

    private final SourceDocumentDao documentDao = new SourceDocumentDaoImpl();
    private final SourceSiteDao siteDao = new SourceSiteDaoImpl();
    private final KnowledgeProvider provider = new MysqlKnowledgeProvider();

    @Override
    public PageResult<SourceDocument> page(String keyword, String reviewStatus, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        String safeKeyword = TextUtil.isBlank(keyword) ? null : keyword.trim();
        String safeStatus = TextUtil.isBlank(reviewStatus) ? null : reviewStatus.trim();
        return read(conn -> {
            long total = documentDao.count(conn, safeKeyword, safeStatus);
            List<SourceDocument> rows = total == 0 ? Collections.emptyList()
                    : documentDao.page(conn, safeKeyword, safeStatus, (safePage - 1) * safeSize, safeSize);
            return new PageResult<>(rows, total, safePage, safeSize);
        });
    }

    @Override
    public SourceDocument detail(Long id) {
        if (id == null) {
            throw BizException.badRequest("缺少来源文档编号");
        }
        return read(conn -> {
            SourceDocument document = documentDao.findById(conn, id);
            if (document == null) {
                throw BizException.notFound("来源文档不存在");
            }
            return document;
        });
    }

    @Override
    public SourceDocument importDraft(Long operatorId, SourceDocument document) {
        if (document == null) {
            throw BizException.badRequest("缺少来源文档内容");
        }
        document.setUrl(ValidationUtil.optionalUrl(
                ValidationUtil.requireText(document.getUrl(), "原文地址", 500), "原文地址"));
        document.setTitle(ValidationUtil.requireText(document.getTitle(), "文档标题", 300));
        document.setContentText(ValidationUtil.requireText(document.getContentText(), "文档正文", 200000));
        document.setSummary(ValidationUtil.optionalText(document.getSummary(), 1000, "文档摘要"));
        document.setSourceType(TextUtil.defaultIfBlank(
                ValidationUtil.optionalText(document.getSourceType(), 30, "来源类型"), DEFAULT_SOURCE_TYPE));
        document.setLicenseType(ValidationUtil.optionalText(document.getLicenseType(), 80, "许可类型"));
        document.setEvidenceLevel(TextUtil.defaultIfBlank(
                ValidationUtil.optionalText(document.getEvidenceLevel(), 30, "证据等级"), DEFAULT_EVIDENCE_LEVEL));
        document.setReviewStatus(SourceDocument.REVIEW_PENDING);
        document.setReviewerId(null);
        document.setReviewNote(null);
        document.setContentHash(TextUtil.sha256Hex(document.getTitle() + "\n" + document.getContentText()));
        if (TextUtil.isBlank(document.getFetchedAt())) {
            document.setFetchedAt(com.flavorlogic.common.util.DateTimeUtil.now());
        }
        return write(conn -> {
            if (document.getSiteId() != null && siteDao.findById(conn, document.getSiteId()) == null) {
                throw BizException.notFound("来源站点不存在");
            }
            if (documentDao.findByUrl(conn, document.getUrl()) != null) {
                throw BizException.conflict("相同来源地址的文档已存在");
            }
            if (documentDao.findByHash(conn, document.getContentHash()) != null) {
                throw BizException.conflict("正文内容与已有来源文档重复");
            }
            document.setId(documentDao.insert(conn, document));
            return documentDao.findById(conn, document.getId());
        });
    }

    @Override
    public SourceDocument review(Long operatorId, Long id, String reviewStatus, String note) {
        if (operatorId == null) {
            throw BizException.unauthorized("请先登录");
        }
        String status = ValidationUtil.requireOneOf(reviewStatus, "审核状态",
                SourceDocument.APPROVED, SourceDocument.REJECTED, SourceDocument.ARCHIVED,
                SourceDocument.REVIEW_PENDING);
        String safeNote = ValidationUtil.optionalText(note, 1000, "审核意见");
        return write(conn -> {
            if (documentDao.findById(conn, id) == null) {
                throw BizException.notFound("来源文档不存在");
            }
            documentDao.updateReview(conn, id, status, operatorId, safeNote);
            return documentDao.findById(conn, id);
        });
    }

    @Override
    public List<EvidenceItem> search(String query, int limit) {
        return provider.retrieve(query, limit);
    }
}
