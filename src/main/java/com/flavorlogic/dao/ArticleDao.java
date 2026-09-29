package com.flavorlogic.dao;

import com.flavorlogic.model.KnowledgeArticle;

import java.sql.Connection;
import java.util.List;

/**
 * 研发知识文章数据访问接口（表 {@code knowledge_article}）。
 */
public interface ArticleDao {

    /**
     * 分页查询文章（联表带出分类名称与作者昵称）。
     *
     * @param conn       数据库连接
     * @param categoryId 分类筛选，可为空
     * @param keyword    标题或摘要关键字，可为空
     * @param status     状态筛选，可为空（用户端固定传 1）
     * @param offset     偏移量
     * @param limit      每页条数
     * @return 文章列表（不含正文，减少传输量）
     */
    List<KnowledgeArticle> page(Connection conn, Long categoryId, String keyword, Integer status, int offset, int limit);

    /**
     * 统计满足条件的文章数。
     *
     * @param conn       数据库连接
     * @param categoryId 分类筛选，可为空
     * @param keyword    关键字，可为空
     * @param status     状态筛选，可为空
     * @return 记录数
     */
    long count(Connection conn, Long categoryId, String keyword, Integer status);

    /**
     * 按主键查询文章（含正文）。
     *
     * @param conn 数据库连接
     * @param id   文章编号
     * @return 文章或 null
     */
    KnowledgeArticle findById(Connection conn, Long id);

    /**
     * 按来源地址查询文章，用于同步与人工录入去重。
     *
     * @param conn      数据库连接
     * @param sourceUrl 来源地址
     * @return 文章或 null
     */
    KnowledgeArticle findBySourceUrl(Connection conn, String sourceUrl);

    /**
     * 按正文内容指纹查询文章。
     *
     * @param conn        数据库连接
     * @param contentHash SHA-256 指纹
     * @return 文章或 null
     */
    KnowledgeArticle findByContentHash(Connection conn, String contentHash);

    /**
     * 标题模糊匹配，用于“疑似重复”提示。
     *
     * @param conn      数据库连接
     * @param title     标题关键字
     * @param excludeId 需排除的文章编号，可为空
     * @param limit     最多返回条数
     * @return 疑似重复文章列表
     */
    List<KnowledgeArticle> findSimilarTitles(Connection conn, String title, Long excludeId, int limit);

    /**
     * 新增文章。
     *
     * @param conn    数据库连接
     * @param article 文章
     * @return 新文章编号
     */
    long insert(Connection conn, KnowledgeArticle article);

    /**
     * 更新文章。
     *
     * @param conn    数据库连接
     * @param article 文章（需含编号）
     * @return 影响行数
     */
    int update(Connection conn, KnowledgeArticle article);

    /**
     * 更新文章状态（发布 / 下架 / 删除）。
     *
     * @param conn   数据库连接
     * @param id     文章编号
     * @param status 目标状态
     * @return 影响行数
     */
    int updateStatus(Connection conn, Long id, int status);

    /**
     * 浏览次数 +1。
     *
     * @param conn 数据库连接
     * @param id   文章编号
     * @return 影响行数
     */
    int incrementViewCount(Connection conn, Long id);

    /**
     * 查询最新发布的文章。
     *
     * @param conn  数据库连接
     * @param limit 条数
     * @return 文章列表
     */
    List<KnowledgeArticle> listRecent(Connection conn, int limit);

    /**
     * 查询同分类的相关文章。
     *
     * @param conn       数据库连接
     * @param categoryId 分类编号，可为空
     * @param excludeId  需排除的文章编号
     * @param limit      条数
     * @return 文章列表
     */
    List<KnowledgeArticle> listRelated(Connection conn, Long categoryId, Long excludeId, int limit);

    /**
     * 统计文章总数（不含已删除）。
     *
     * @param conn 数据库连接
     * @return 文章总数
     */
    long countAll(Connection conn);

    /**
     * 统计已发布文章数。
     *
     * @param conn 数据库连接
     * @return 已发布文章数
     */
    long countPublished(Connection conn);
}
