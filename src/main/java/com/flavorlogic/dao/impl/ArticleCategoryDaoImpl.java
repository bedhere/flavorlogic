package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.ArticleCategoryDao;
import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.model.ArticleCategory;
import com.flavorlogic.util.DateTimeUtil;

import java.sql.Connection;
import java.util.List;

/**
 * 文章分类数据访问实现（表 {@code article_category}）。
 *
 * <p>本类只负责持久化：分类名是否重复、能否停用等判断属于 Service 层。</p>
 *
 * <p>所有 SQL 均通过 {@link JdbcHelper} 以 {@code ?} 参数化执行；
 * 传入的 {@link Connection} 由 Service 层负责关闭与事务，本类不做任何事务操作。</p>
 */
public class ArticleCategoryDaoImpl implements ArticleCategoryDao {

    /** 列表与详情共用的列清单 */
    private static final String COLUMNS =
            "c.id, c.name, c.description, c.sort_no, c.status, c.created_at, c.updated_at";

    /** 基础查询语句 */
    private static final String SELECT_BASE = "SELECT " + COLUMNS + " FROM article_category c";

    /** 统一排序：排序号升序，同序号按编号升序，保证结果稳定 */
    private static final String ORDER_BY = " ORDER BY c.sort_no, c.id";

    /** 行映射：article_category 一行 → ArticleCategory */
    private static final JdbcHelper.RowMapper<ArticleCategory> ROW_MAPPER = rs -> {
        ArticleCategory category = new ArticleCategory();
        category.setId(JdbcHelper.getLong(rs, "id"));
        category.setName(rs.getString("name"));
        category.setDescription(rs.getString("description"));
        category.setSortNo(JdbcHelper.getInteger(rs, "sort_no"));
        category.setStatus(JdbcHelper.getInteger(rs, "status"));
        category.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        category.setUpdatedAt(DateTimeUtil.format(rs.getTimestamp("updated_at")));
        return category;
    };

    /**
     * 查询全部分类（按排序号升序，含停用分类）。
     *
     * @param conn 数据库连接
     * @return 分类列表
     */
    @Override
    public List<ArticleCategory> listAll(Connection conn) {
        String sql = SELECT_BASE + ORDER_BY;
        return JdbcHelper.queryList(conn, sql, ROW_MAPPER);
    }

    /**
     * 查询启用中的分类（status = 1）。
     *
     * @param conn 数据库连接
     * @return 分类列表
     */
    @Override
    public List<ArticleCategory> listEnabled(Connection conn) {
        String sql = SELECT_BASE + " WHERE c.status = 1" + ORDER_BY;
        return JdbcHelper.queryList(conn, sql, ROW_MAPPER);
    }

    /**
     * 按主键查询分类。
     *
     * @param conn 数据库连接
     * @param id   分类编号
     * @return 分类或 null
     */
    @Override
    public ArticleCategory findById(Connection conn, Long id) {
        String sql = SELECT_BASE + " WHERE c.id = ?";
        return JdbcHelper.queryOne(conn, sql, ROW_MAPPER, id);
    }

    /**
     * 按名称查询分类，用于新增前的重名提示。
     *
     * @param conn 数据库连接
     * @param name 分类名称
     * @return 分类或 null
     */
    @Override
    public ArticleCategory findByName(Connection conn, String name) {
        String sql = SELECT_BASE + " WHERE c.name = ? LIMIT 1";
        return JdbcHelper.queryOne(conn, sql, ROW_MAPPER, name);
    }

    /**
     * 新增分类，created_at / updated_at 交给数据库默认值。
     *
     * @param conn     数据库连接
     * @param category 分类
     * @return 新分类编号
     */
    @Override
    public long insert(Connection conn, ArticleCategory category) {
        String sql = "INSERT INTO article_category (name, description, sort_no, status) "
                + "VALUES (?, ?, ?, ?)";
        // sort_no 与 status 列 NOT NULL，未赋值时按建表默认值（0 / 1）写入
        Integer sortNo = category.getSortNo() == null ? Integer.valueOf(0) : category.getSortNo();
        Integer status = category.getStatus() == null ? Integer.valueOf(1) : category.getStatus();
        return JdbcHelper.insertAndReturnKey(conn, sql,
                category.getName(), category.getDescription(), sortNo, status);
    }

    /**
     * 更新分类，updated_at 由数据库自动维护。
     *
     * @param conn     数据库连接
     * @param category 分类（需含编号）
     * @return 影响行数
     */
    @Override
    public int update(Connection conn, ArticleCategory category) {
        String sql = "UPDATE article_category SET name = ?, description = ?, sort_no = ?, status = ? "
                + "WHERE id = ?";
        Integer sortNo = category.getSortNo() == null ? Integer.valueOf(0) : category.getSortNo();
        Integer status = category.getStatus() == null ? Integer.valueOf(1) : category.getStatus();
        return JdbcHelper.update(conn, sql, category.getName(), category.getDescription(),
                sortNo, status, category.getId());
    }

    /**
     * 统计分类下的文章数量（不含已删除：status &lt;&gt; 3）。
     *
     * <p>categoryId 为 null 时按 {@code category_id = NULL} 处理，结果为 0，
     * 不会误统计到无分类文章。</p>
     *
     * @param conn       数据库连接
     * @param categoryId 分类编号
     * @return 文章数量
     */
    @Override
    public long countArticles(Connection conn, Long categoryId) {
        String sql = "SELECT COUNT(*) FROM knowledge_article WHERE category_id = ? AND status <> 3";
        return JdbcHelper.queryLong(conn, sql, categoryId);
    }
}
