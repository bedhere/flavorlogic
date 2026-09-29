package com.flavorlogic.dao;

import com.flavorlogic.model.Ingredient;

import java.sql.Connection;
import java.util.List;

/**
 * 食材与风味属性数据访问接口（表 {@code ingredient}）。
 */
public interface IngredientDao {

    /**
     * 查询启用中的食材（按分类、名称排序）。
     *
     * @param conn 数据库连接
     * @return 食材列表
     */
    List<Ingredient> listEnabled(Connection conn);

    /**
     * 按主键批量查询食材。
     *
     * @param conn 数据库连接
     * @param ids  食材编号集合
     * @return 食材列表
     */
    List<Ingredient> findByIds(Connection conn, List<Long> ids);

    /**
     * 按主键查询食材。
     *
     * @param conn 数据库连接
     * @param id   食材编号
     * @return 食材或 null
     */
    Ingredient findById(Connection conn, Long id);

    /**
     * 按名称查询食材。
     *
     * @param conn 数据库连接
     * @param name 食材名称
     * @return 食材或 null
     */
    Ingredient findByName(Connection conn, String name);

    /**
     * 分页查询食材（管理端）。
     *
     * @param conn     数据库连接
     * @param keyword  名称关键字，可为空
     * @param category 分类筛选，可为空
     * @param status   状态筛选，可为空
     * @param offset   偏移量
     * @param limit    每页条数
     * @return 食材列表
     */
    List<Ingredient> page(Connection conn, String keyword, String category, Integer status, int offset, int limit);

    /**
     * 统计满足条件的食材数。
     *
     * @param conn     数据库连接
     * @param keyword  关键字，可为空
     * @param category 分类筛选，可为空
     * @param status   状态筛选，可为空
     * @return 记录数
     */
    long count(Connection conn, String keyword, String category, Integer status);

    /**
     * 查询全部食材分类名称。
     *
     * @param conn 数据库连接
     * @return 分类名称集合
     */
    List<String> listCategories(Connection conn);

    /**
     * 新增食材。
     *
     * @param conn       数据库连接
     * @param ingredient 食材
     * @return 新食材编号
     */
    long insert(Connection conn, Ingredient ingredient);

    /**
     * 更新食材（含八个风味维度）。
     *
     * @param conn       数据库连接
     * @param ingredient 食材（需含编号）
     * @return 影响行数
     */
    int update(Connection conn, Ingredient ingredient);

    /**
     * 更新食材状态（启用 / 停用）。
     *
     * @param conn   数据库连接
     * @param id     食材编号
     * @param status 目标状态
     * @return 影响行数
     */
    int updateStatus(Connection conn, Long id, int status);

    /**
     * 统计食材总数。
     *
     * @param conn 数据库连接
     * @return 食材总数
     */
    long countAll(Connection conn);

    /**
     * 统计启用中的食材数。
     *
     * @param conn 数据库连接
     * @return 启用食材数
     */
    long countEnabled(Connection conn);
}
