package com.flavorlogic.service.impl;

import com.flavorlogic.dao.ArticleCategoryDao;
import com.flavorlogic.dao.ArticleDao;
import com.flavorlogic.dao.UserDao;
import com.flavorlogic.dao.impl.ArticleCategoryDaoImpl;
import com.flavorlogic.dao.impl.ArticleDaoImpl;
import com.flavorlogic.dao.impl.UserDaoImpl;
import com.flavorlogic.model.ArticleCategory;
import com.flavorlogic.model.KnowledgeArticle;
import com.flavorlogic.model.User;
import com.flavorlogic.service.ArticleService;
import com.flavorlogic.service.BaseService;
import com.flavorlogic.util.BizException;
import com.flavorlogic.util.DateTimeUtil;
import com.flavorlogic.util.PageResult;
import com.flavorlogic.util.TextUtil;
import com.flavorlogic.util.ValidationUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 研发知识库业务实现。
 *
 * <p>重复文章处理分三层（见项目介绍 十三）：
 * 来源地址去重、正文内容指纹去重、标题相似度提示。
 * 前两层直接拦截，第三层只给出人工确认提示，不覆盖原文章。</p>
 */
public class ArticleServiceImpl extends BaseService implements ArticleService {

    /** 正文指纹计算时纳入标题，避免“同正文不同标题”被误判 */
    private static final int SIMILAR_TITLE_KEYWORD_LENGTH = 12;

    private final ArticleDao articleDao = new ArticleDaoImpl();
    private final ArticleCategoryDao categoryDao = new ArticleCategoryDaoImpl();
    private final UserDao userDao = new UserDaoImpl();

    @Override
    public List<ArticleCategory> categories() {
        return read(categoryDao::listEnabled);
    }

    @Override
    public PageResult<KnowledgeArticle> pageForUser(Long categoryId, String keyword, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        String trimmedKeyword = TextUtil.isBlank(keyword) ? null : keyword.trim();
        return read(conn -> {
            long total = articleDao.count(conn, categoryId, trimmedKeyword, KnowledgeArticle.STATUS_PUBLISHED);
            List<KnowledgeArticle> list = total == 0
                    ? Collections.emptyList()
                    : articleDao.page(conn, categoryId, trimmedKeyword, KnowledgeArticle.STATUS_PUBLISHED,
                            (safePage - 1) * safeSize, safeSize);
            return new PageResult<>(list, total, safePage, safeSize);
        });
    }

    @Override
    public KnowledgeArticle detailForUser(Long id) {
        if (id == null) {
            throw BizException.badRequest("缺少文章编号");
        }
        return write(conn -> {
            KnowledgeArticle article = articleDao.findById(conn, id);
            if (article == null || article.getStatus() == null
                    || article.getStatus() == KnowledgeArticle.STATUS_DELETED) {
                throw BizException.notFound("文章不存在或已删除");
            }
            if (!article.isPublished()) {
                throw BizException.forbidden("该文章尚未发布");
            }
            articleDao.incrementViewCount(conn, id);
            article.setViewCount((article.getViewCount() == null ? 0 : article.getViewCount()) + 1);
            article.setContent(TextUtil.sanitizeHtml(article.getContent()));
            fillNames(conn, article);
            return article;
        });
    }

    @Override
    public List<KnowledgeArticle> recent(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        return read(conn -> articleDao.listRecent(conn, safeLimit));
    }

    @Override
    public List<KnowledgeArticle> related(Long categoryId, Long excludeId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 10);
        return read(conn -> articleDao.listRelated(conn, categoryId, excludeId, safeLimit));
    }

    @Override
    public PageResult<KnowledgeArticle> adminPage(Long categoryId, String keyword, Integer status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        String trimmedKeyword = TextUtil.isBlank(keyword) ? null : keyword.trim();
        return read(conn -> {
            long total = articleDao.count(conn, categoryId, trimmedKeyword, status);
            List<KnowledgeArticle> list = total == 0
                    ? Collections.emptyList()
                    : articleDao.page(conn, categoryId, trimmedKeyword, status, (safePage - 1) * safeSize, safeSize);
            return new PageResult<>(list, total, safePage, safeSize);
        });
    }

    @Override
    public KnowledgeArticle adminCreate(User operator, KnowledgeArticle article) {
        normalize(article);
        article.setAuthorId(operator == null ? null : operator.getId());
        return write(conn -> {
            checkDuplicate(conn, article, null);
            article.setId(articleDao.insert(conn, article));
            article.setDuplicateHint(similarTitleHint(conn, article));
            return article;
        });
    }

    @Override
    public KnowledgeArticle adminUpdate(User operator, KnowledgeArticle article) {
        if (article.getId() == null) {
            throw BizException.badRequest("缺少文章编号");
        }
        normalize(article);
        return write(conn -> {
            KnowledgeArticle existing = articleDao.findById(conn, article.getId());
            if (existing == null) {
                throw BizException.notFound("文章不存在");
            }
            checkDuplicate(conn, article, article.getId());
            if (article.getAuthorId() == null) {
                article.setAuthorId(existing.getAuthorId());
            }
            if (article.getStatus() != null && article.getStatus() == KnowledgeArticle.STATUS_PUBLISHED
                    && TextUtil.isBlank(existing.getPublishedAt())) {
                article.setPublishedAt(DateTimeUtil.now());
            }
            articleDao.update(conn, article);
            article.setDuplicateHint(similarTitleHint(conn, article));
            return article;
        });
    }

    @Override
    public void adminUpdateStatus(Long id, int status) {
        if (status != KnowledgeArticle.STATUS_DRAFT && status != KnowledgeArticle.STATUS_PUBLISHED
                && status != KnowledgeArticle.STATUS_OFFLINE && status != KnowledgeArticle.STATUS_DELETED) {
            throw BizException.badRequest("文章状态取值不合法");
        }
        writeVoid(conn -> {
            KnowledgeArticle existing = articleDao.findById(conn, id);
            if (existing == null) {
                throw BizException.notFound("文章不存在");
            }
            articleDao.updateStatus(conn, id, status);
        });
    }

    @Override
    public long countAll() {
        return read(articleDao::countAll);
    }

    @Override
    public long countPublished() {
        return read(articleDao::countPublished);
    }

    /**
     * 规范化文章字段：标题必填、摘要自动生成、正文清洗、内容指纹重算、发布时间补齐。
     *
     * @param article 文章
     */
    private void normalize(KnowledgeArticle article) {
        article.setTitle(ValidationUtil.requireText(article.getTitle(), "文章标题", 200));
        article.setContent(ValidationUtil.requireText(article.getContent(), "文章正文", 60000));
        article.setSummary(TextUtil.defaultIfBlank(article.getSummary(),
                TextUtil.summarize(article.getContent(), 120)));
        article.setSourceName(ValidationUtil.optionalText(article.getSourceName(), 100, "来源名称"));
        article.setSourceUrl(ValidationUtil.optionalUrl(article.getSourceUrl(), "原文地址"));
        article.setCoverUrl(ValidationUtil.optionalText(article.getCoverUrl(), 500, "封面地址"));
        if (article.getStatus() == null) {
            article.setStatus(KnowledgeArticle.STATUS_DRAFT);
        }
        if (article.getStatus() == KnowledgeArticle.STATUS_PUBLISHED && TextUtil.isBlank(article.getPublishedAt())) {
            article.setPublishedAt(DateTimeUtil.now());
        }
        article.setContentHash(TextUtil.sha256Hex(article.getTitle() + "\n" + TextUtil.stripHtml(article.getContent())));
    }

    /**
     * 来源地址与内容指纹去重。
     *
     * @param conn      数据库连接
     * @param article   待保存文章
     * @param excludeId 编辑场景下需排除的自身编号
     */
    private void checkDuplicate(java.sql.Connection conn, KnowledgeArticle article, Long excludeId) {
        if (!TextUtil.isBlank(article.getSourceUrl())) {
            KnowledgeArticle byUrl = articleDao.findBySourceUrl(conn, article.getSourceUrl());
            if (byUrl != null && (excludeId == null || !excludeId.equals(byUrl.getId()))) {
                throw BizException.conflict("相同来源地址的文章已存在：「" + byUrl.getTitle() + "」（编号 "
                        + byUrl.getId() + "），本次未重复导入");
            }
        }
        if (!TextUtil.isBlank(article.getContentHash())) {
            KnowledgeArticle byHash = articleDao.findByContentHash(conn, article.getContentHash());
            if (byHash != null && (excludeId == null || !excludeId.equals(byHash.getId()))) {
                throw BizException.conflict("正文内容与已有文章「" + byHash.getTitle() + "」完全相同，已判定为重复");
            }
        }
    }

    /**
     * 标题相似度提示：仅提示，不拦截。
     *
     * @param conn    数据库连接
     * @param article 文章
     * @return 提示文本，无相似文章返回 null
     */
    private String similarTitleHint(java.sql.Connection conn, KnowledgeArticle article) {
        String title = article.getTitle();
        if (TextUtil.isBlank(title)) {
            return null;
        }
        String keyword = title.length() > SIMILAR_TITLE_KEYWORD_LENGTH
                ? title.substring(0, SIMILAR_TITLE_KEYWORD_LENGTH) : title;
        List<KnowledgeArticle> similar = articleDao.findSimilarTitles(conn, keyword, article.getId(), 3);
        if (similar.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (KnowledgeArticle item : similar) {
            names.add("「" + item.getTitle() + "」");
        }
        return "标题与已有文章 " + String.join("、", names) + " 相似，请人工确认是否为重复内容";
    }

    /**
     * 补齐分类名称与作者昵称。
     *
     * @param conn    数据库连接
     * @param article 文章
     */
    private void fillNames(java.sql.Connection conn, KnowledgeArticle article) {
        if (TextUtil.isBlank(article.getCategoryName()) && article.getCategoryId() != null) {
            ArticleCategory category = categoryDao.findById(conn, article.getCategoryId());
            if (category != null) {
                article.setCategoryName(category.getName());
            }
        }
        if (TextUtil.isBlank(article.getAuthorName()) && article.getAuthorId() != null) {
            User author = userDao.findById(conn, article.getAuthorId());
            if (author != null) {
                article.setAuthorName(TextUtil.defaultIfBlank(author.getNickname(), author.getUsername()));
            }
        }
    }
}
