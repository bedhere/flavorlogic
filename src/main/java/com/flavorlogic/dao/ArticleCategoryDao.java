package com.flavorlogic.dao;

import com.flavorlogic.model.ArticleCategory;

import java.sql.Connection;
import java.util.List;

/**
 * 文章分类数据访问接口（表 {@code article_category}）。
 */
public interface ArticleCategoryDao {

    /**
     * 查询全部分类（按排序号升序）。
     *
     * @param conn 数据库连接
     * @return 分类列表
     */
    List<ArticleCategory> listAll(Connection conn);

    /**
     * 查询启用中的分类。
     *
     * @param conn 数据库连接
     * @return 分类列表
     */
    List<ArticleCategory> listEnabled(Connection conn);

    /**
     * 按主键查询分类。
     *
     * @param conn 数据库连接
     * @param id   分类编号
     * @return 分类或 null
     */
    ArticleCategory findById(Connection conn, Long id);

    /**
     * 按名称查询分类。
     *
     * @param conn 数据库连接
     * @param name 分类名称
     * @return 分类或 null
     */
    ArticleCategory findByName(Connection conn, String name);

    /**
     * 新增分类。
     *
     * @param conn     数据库连接
     * @param category 分类
     * @return 新分类编号
     */
    long insert(Connection conn, ArticleCategory category);

    /**
     * 更新分类。
     *
     * @param conn     数据库连接
     * @param category 分类（需含编号）
     * @return 影响行数
     */
    int update(Connection conn, ArticleCategory category);

    /**
     * 统计分类下的文章数量。
     *
     * @param conn       数据库连接
     * @param categoryId 分类编号
     * @return 文章数量
     */
    long countArticles(Connection conn, Long categoryId);
}
