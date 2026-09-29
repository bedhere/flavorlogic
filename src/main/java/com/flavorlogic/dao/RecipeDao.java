package com.flavorlogic.dao;

import com.flavorlogic.model.Recipe;

import java.sql.Connection;
import java.util.List;

/**
 * 配方主表数据访问接口（表 {@code recipe}）。
 */
public interface RecipeDao {

    /**
     * 分页查询某用户的配方。
     *
     * @param conn    数据库连接
     * @param userId  用户编号
     * @param keyword 配方名称关键字，可为空
     * @param offset  偏移量
     * @param limit   每页条数
     * @return 配方列表（含明细条数）
     */
    List<Recipe> pageByUser(Connection conn, Long userId, String keyword, int offset, int limit);

    /**
     * 统计某用户的配方数。
     *
     * @param conn    数据库连接
     * @param userId  用户编号
     * @param keyword 关键字，可为空
     * @return 记录数
     */
    long countByUser(Connection conn, Long userId, String keyword);

    /**
     * 按主键查询配方。
     *
     * @param conn 数据库连接
     * @param id   配方编号
     * @return 配方或 null
     */
    Recipe findById(Connection conn, Long id);

    /**
     * 新增配方。
     *
     * @param conn   数据库连接
     * @param recipe 配方
     * @return 新配方编号
     */
    long insert(Connection conn, Recipe recipe);

    /**
     * 更新配方主体信息。
     *
     * @param conn   数据库连接
     * @param recipe 配方（需含编号）
     * @return 影响行数
     */
    int update(Connection conn, Recipe recipe);

    /**
     * 软删除配方（status = 0），保留历史分析可回看。
     *
     * @param conn 数据库连接
     * @param id   配方编号
     * @return 影响行数
     */
    int softDelete(Connection conn, Long id);

    /**
     * 查询用户最近的配方。
     *
     * @param conn   数据库连接
     * @param userId 用户编号
     * @param limit  条数
     * @return 配方列表
     */
    List<Recipe> listRecentByUser(Connection conn, Long userId, int limit);

    /**
     * 统计用户的有效配方数。
     *
     * @param conn   数据库连接
     * @param userId 用户编号
     * @return 配方数
     */
    long countActiveByUser(Connection conn, Long userId);

    /**
     * 统计全部有效配方数（管理端）。
     *
     * @param conn 数据库连接
     * @return 配方总数
     */
    long countAll(Connection conn);
}
