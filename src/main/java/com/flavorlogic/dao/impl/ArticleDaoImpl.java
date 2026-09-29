package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.ArticleDao;
import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.model.KnowledgeArticle;
import com.flavorlogic.util.DateTimeUtil;
import com.flavorlogic.util.TextUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 研发知识文章数据访问实现（表 {@code knowledge_article}）。
 *
 * <p>投影约定：除 {@link #findById} 外，其余查询一律 <b>不选 content 正文</b>，
 * 并使用统一的分页/列表投影（联表带出分类名称与作者昵称），以降低网络与内存开销。</p>
 *
 * <p>本类只负责持久化：状态流转是否合法、标题是否重复等判断属于 Service 层。</p>
 *
 * <p>所有 SQL 均通过 {@link JdbcHelper} 以 {@code ?} 参数化执行（含 LIMIT / OFFSET），
 * 用户输入永不拼接进 SQL 文本；传入的 {@link Connection} 由 Service 层负责
 * 关闭与事务，本类不调用 close / commit / rollback / setAutoCommit。</p>
 */
public class ArticleDaoImpl implements ArticleDao {

    /** 状态取值说明（与建表语句一致）：0 草稿，1 发布，2 下架，3 删除 */
    private static final int STATUS_DELETED = KnowledgeArticle.STATUS_DELETED;

    /** 列表投影列清单：不含 content，联表带出分类名称与作者昵称 */
    private static final String LIST_COLUMNS =
            "a.id, a.category_id, c.name AS category_name, a.author_id, u.nickname AS author_name, "
                    + "a.title, a.summary, a.cover_url, a.source_name, a.source_url, a.content_hash, "
                    + "a.status, a.view_count, a.published_at, a.created_at, a.updated_at";

    /** 列表查询的 FROM 与联表（LEFT JOIN 保证分类被删、作者被删时文章仍可查出） */
    private static final String LIST_FROM =
            " FROM knowledge_article a"
                    + " LEFT JOIN article_category c ON a.category_id = c.id"
                    + " LEFT JOIN sys_user u ON a.author_id = u.id";

    /** 列表基础查询（page / listRecent / listRelated / findSimilarTitles 共用） */
    private static final String SELECT_LIST = "SELECT " + LIST_COLUMNS + LIST_FROM;

    /** 详情查询：全部列（含 content） */
    private static final String SELECT_DETAIL =
            "SELECT a.id, a.category_id, a.author_id, a.title, a.summary, a.content, a.cover_url, "
                    + "a.source_name, a.source_url, a.content_hash, a.status, a.view_count, "
                    + "a.published_at, a.created_at, a.updated_at"
                    + " FROM knowledge_article a";

    /** 统计查询：count 的筛选条件只用到 knowledge_article 自身列，故无需联表 */
    private static final String COUNT_BASE = "SELECT COUNT(*) FROM knowledge_article a";

    /** 列表行映射：不含正文，带出分类名称与作者昵称 */
    private static final JdbcHelper.RowMapper<KnowledgeArticle> LIST_MAPPER = rs -> {
        KnowledgeArticle article = new KnowledgeArticle();
        fillCommon(article, rs);
        article.setCategoryName(rs.getString("category_name"));
        article.setAuthorName(rs.getString("author_name"));
        return article;
    };

    /** 详情行映射：含正文 */
    private static final JdbcHelper.RowMapper<KnowledgeArticle> DETAIL_MAPPER = rs -> {
        KnowledgeArticle article = new KnowledgeArticle();
        fillCommon(article, rs);
        article.setContent(rs.getString("content"));
        return article;
    };

    /**
     * 分页查询文章（联表带出分类名称与作者昵称，不含正文）。
     *
     * <p>排序：发布时间倒序、编号倒序（未发布文章的 published_at 为 null，排在最后）。</p>
     *
     * @param conn       数据库连接
     * @param categoryId 分类筛选，可为空
     * @param keyword    标题或摘要关键字，可为空
     * @param status     状态筛选，可为空（用户端固定传 1）
     * @param offset     偏移量
     * @param limit      每页条数
     * @return 文章列表（不含正文，减少传输量）
     */
    @Override
    public List<KnowledgeArticle> page(Connection conn, Long categoryId, String keyword, Integer status,
                                       int offset, int limit) {
        List<Object> params = new ArrayList<>();
        String sql = SELECT_LIST + buildFilter(categoryId, keyword, status, params)
                + " ORDER BY a.published_at DESC, a.id DESC LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        return JdbcHelper.queryList(conn, sql, LIST_MAPPER, params.toArray());
    }

    /**
     * 统计满足条件的文章数，筛选条件与 {@link #page} 完全一致。
     *
     * @param conn       数据库连接
     * @param categoryId 分类筛选，可为空
     * @param keyword    关键字，可为空
     * @param status     状态筛选，可为空
     * @return 记录数
     */
    @Override
    public long count(Connection conn, Long categoryId, String keyword, Integer status) {
        List<Object> params = new ArrayList<>();
        String sql = COUNT_BASE + buildFilter(categoryId, keyword, status, params);
        return JdbcHelper.queryLong(conn, sql, params.toArray());
    }

    /**
     * 按主键查询文章（含正文）。
     *
     * @param conn 数据库连接
     * @param id   文章编号
     * @return 文章或 null
     */
    @Override
    public KnowledgeArticle findById(Connection conn, Long id) {
        String sql = SELECT_DETAIL + " WHERE a.id = ?";
        return JdbcHelper.queryOne(conn, sql, DETAIL_MAPPER, id);
    }

    /**
     * 按来源地址查询文章，用于同步与人工录入去重（只取列表投影，不加载正文）。
     *
     * @param conn      数据库连接
     * @param sourceUrl 来源地址
     * @return 文章或 null
     */
    @Override
    public KnowledgeArticle findBySourceUrl(Connection conn, String sourceUrl) {
        String sql = SELECT_LIST + " WHERE a.source_url = ? LIMIT 1";
        return JdbcHelper.queryOne(conn, sql, LIST_MAPPER, sourceUrl);
    }

    /**
     * 按正文内容指纹查询文章（只取列表投影，不加载正文）。
     *
     * @param conn        数据库连接
     * @param contentHash SHA-256 指纹
     * @return 文章或 null
     */
    @Override
    public KnowledgeArticle findByContentHash(Connection conn, String contentHash) {
        String sql = SELECT_LIST + " WHERE a.content_hash = ? LIMIT 1";
        return JdbcHelper.queryOne(conn, sql, LIST_MAPPER, contentHash);
    }

    /**
     * 标题模糊匹配，用于“疑似重复”提示（已删除文章（status = 3）不参与匹配）。
     *
     * @param conn      数据库连接
     * @param title     标题关键字
     * @param excludeId 需排除的文章编号，可为空（为空时不追加排除条件）
     * @param limit     最多返回条数
     * @return 疑似重复文章列表
     */
    @Override
    public List<KnowledgeArticle> findSimilarTitles(Connection conn, String title, Long excludeId, int limit) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_LIST)
                .append(" WHERE a.title LIKE ? AND a.status <> ").append(STATUS_DELETED);
        params.add(TextUtil.likePattern(title));
        if (excludeId != null) {
            sql.append(" AND a.id <> ?");
            params.add(excludeId);
        }
        sql.append(" ORDER BY a.id DESC LIMIT ?");
        params.add(limit);
        return JdbcHelper.queryList(conn, sql.toString(), LIST_MAPPER, params.toArray());
    }

    /**
     * 新增文章，created_at / updated_at 交给数据库默认值。
     *
     * <p>调用前 Service 需保证 title 与 content 非空（两列 NOT NULL）；
     * source_url 与 content_hash 为唯一键，未提供时传 null 即可（MySQL 唯一索引允许多个 null）。</p>
     *
     * @param conn    数据库连接
     * @param article 文章
     * @return 新文章编号
     */
    @Override
    public long insert(Connection conn, KnowledgeArticle article) {
        String sql = "INSERT INTO knowledge_article (category_id, author_id, title, summary, content, "
                + "cover_url, source_name, source_url, content_hash, status, view_count, published_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        // status 与 view_count 列 NOT NULL，未赋值时按建表默认值（0 草稿 / 0 次浏览）写入
        Integer status = article.getStatus() == null
                ? Integer.valueOf(KnowledgeArticle.STATUS_DRAFT) : article.getStatus();
        Integer viewCount = article.getViewCount() == null ? Integer.valueOf(0) : article.getViewCount();
        return JdbcHelper.insertAndReturnKey(conn, sql,
                article.getCategoryId(), article.getAuthorId(), article.getTitle(), article.getSummary(),
                article.getContent(), article.getCoverUrl(), article.getSourceName(), article.getSourceUrl(),
                article.getContentHash(), status, viewCount,
                DateTimeUtil.toTimestamp(article.getPublishedAt()));
    }

    /**
     * 更新文章（updated_at 由数据库自动维护）。
     *
     * <p>不更新 view_count：该列由 {@link #incrementViewCount} 维护，
     * 避免编辑时用陈旧快照覆盖并发计数。</p>
     *
     * <p>调用前请用 {@link #findById} 取回含正文的完整对象，否则 content 列会因 null 写入失败。</p>
     *
     * @param conn    数据库连接
     * @param article 文章（需含编号）
     * @return 影响行数
     */
    @Override
    public int update(Connection conn, KnowledgeArticle article) {
        String sql = "UPDATE knowledge_article SET category_id = ?, author_id = ?, title = ?, summary = ?, "
                + "content = ?, cover_url = ?, source_name = ?, source_url = ?, content_hash = ?, "
                + "status = ?, published_at = ? WHERE id = ?";
        return JdbcHelper.update(conn, sql,
                article.getCategoryId(), article.getAuthorId(), article.getTitle(), article.getSummary(),
                article.getContent(), article.getCoverUrl(), article.getSourceName(), article.getSourceUrl(),
                article.getContentHash(), article.getStatus(),
                DateTimeUtil.toTimestamp(article.getPublishedAt()), article.getId());
    }

    /**
     * 更新文章状态（发布 / 下架 / 删除）。
     *
     * @param conn   数据库连接
     * @param id     文章编号
     * @param status 目标状态
     * @return 影响行数
     */
    @Override
    public int updateStatus(Connection conn, Long id, int status) {
        String sql = "UPDATE knowledge_article SET status = ? WHERE id = ?";
        return JdbcHelper.update(conn, sql, status, id);
    }

    /**
     * 浏览次数 +1（数据库端自增，避免读改写丢失更新）。
     *
     * @param conn 数据库连接
     * @param id   文章编号
     * @return 影响行数
     */
    @Override
    public int incrementViewCount(Connection conn, Long id) {
        String sql = "UPDATE knowledge_article SET view_count = view_count + 1 WHERE id = ?";
        return JdbcHelper.update(conn, sql, id);
    }

    /**
     * 查询最新发布的文章（status = 1，按发布时间倒序，不含正文）。
     *
     * @param conn  数据库连接
     * @param limit 条数
     * @return 文章列表
     */
    @Override
    public List<KnowledgeArticle> listRecent(Connection conn, int limit) {
        String sql = SELECT_LIST + " WHERE a.status = " + KnowledgeArticle.STATUS_PUBLISHED
                + " ORDER BY a.published_at DESC, a.id DESC LIMIT ?";
        return JdbcHelper.queryList(conn, sql, LIST_MAPPER, limit);
    }

    /**
     * 查询同分类的相关文章（status = 1，不含正文）。
     *
     * <p>categoryId 为 null 时不追加分类条件，退化为“最新发布的其他文章”，
     * 便于无分类文章展示相关推荐。</p>
     *
     * @param conn       数据库连接
     * @param categoryId 分类编号，可为空
     * @param excludeId  需排除的文章编号
     * @param limit      条数
     * @return 文章列表
     */
    @Override
    public List<KnowledgeArticle> listRelated(Connection conn, Long categoryId, Long excludeId, int limit) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_LIST)
                .append(" WHERE a.status = ").append(KnowledgeArticle.STATUS_PUBLISHED)
                .append(" AND a.id <> ?");
        params.add(excludeId);
        if (categoryId != null) {
            sql.append(" AND a.category_id = ?");
            params.add(categoryId);
        }
        sql.append(" ORDER BY a.published_at DESC, a.id DESC LIMIT ?");
        params.add(limit);
        return JdbcHelper.queryList(conn, sql.toString(), LIST_MAPPER, params.toArray());
    }

    /**
     * 统计文章总数（不含已删除：status &lt;&gt; 3）。
     *
     * @param conn 数据库连接
     * @return 文章总数
     */
    @Override
    public long countAll(Connection conn) {
        String sql = "SELECT COUNT(*) FROM knowledge_article WHERE status <> " + STATUS_DELETED;
        return JdbcHelper.queryLong(conn, sql);
    }

    /**
     * 统计已发布文章数（status = 1）。
     *
     * @param conn 数据库连接
     * @return 已发布文章数
     */
    @Override
    public long countPublished(Connection conn) {
        String sql = "SELECT COUNT(*) FROM knowledge_article WHERE status = "
                + KnowledgeArticle.STATUS_PUBLISHED;
        return JdbcHelper.queryLong(conn, sql);
    }

    /**
     * 拼接可选筛选条件（条件值一律 {@code ?} 绑定，关键字走 {@link TextUtil#likePattern}）。
     *
     * <p>本方法生成的片段使用别名 a，故 page（联表）与 count（单表加别名 a）可共用。</p>
     *
     * @param categoryId 分类编号，为 null 时不追加条件
     * @param keyword    标题/摘要关键字，为空时不追加条件
     * @param status     状态，为 null 时不追加条件
     * @param params     参数收集器，按拼接顺序写入绑定值
     * @return WHERE 子句（始终以 {@code WHERE 1 = 1} 开头，便于追加 AND）
     */
    private static String buildFilter(Long categoryId, String keyword, Integer status, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (categoryId != null) {
            where.append(" AND a.category_id = ?");
            params.add(categoryId);
        }
        if (!TextUtil.isBlank(keyword)) {
            // MySQL 5.7 的 LIKE 默认转义符即反斜杠，无需 ESCAPE 子句
            where.append(" AND (a.title LIKE ? OR a.summary LIKE ?)");
            String pattern = TextUtil.likePattern(keyword);
            params.add(pattern);
            params.add(pattern);
        }
        if (status != null) {
            where.append(" AND a.status = ?");
            params.add(status);
        }
        return where.toString();
    }

    /**
     * 填充列表与详情共有的字段（不含 content、categoryName、authorName）。
     *
     * @param article 目标对象
     * @param rs      结果集，游标已定位到当前行
     * @throws SQLException 读取失败
     */
    private static void fillCommon(KnowledgeArticle article, ResultSet rs) throws SQLException {
        article.setId(JdbcHelper.getLong(rs, "id"));
        article.setCategoryId(JdbcHelper.getLong(rs, "category_id"));
        article.setAuthorId(JdbcHelper.getLong(rs, "author_id"));
        article.setTitle(rs.getString("title"));
        article.setSummary(rs.getString("summary"));
        article.setCoverUrl(rs.getString("cover_url"));
        article.setSourceName(rs.getString("source_name"));
        article.setSourceUrl(rs.getString("source_url"));
        article.setContentHash(rs.getString("content_hash"));
        article.setStatus(JdbcHelper.getInteger(rs, "status"));
        article.setViewCount(JdbcHelper.getInteger(rs, "view_count"));
        article.setPublishedAt(DateTimeUtil.format(rs.getTimestamp("published_at")));
        article.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        article.setUpdatedAt(DateTimeUtil.format(rs.getTimestamp("updated_at")));
    }
}
