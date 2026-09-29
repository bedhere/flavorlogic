package com.flavorlogic.dao;

import com.flavorlogic.model.RecipeItem;

import java.sql.Connection;
import java.util.List;

/**
 * 配方食材明细数据访问接口（表 {@code recipe_item}）。
 */
public interface RecipeItemDao {

    /**
     * 查询配方下的全部明细（按显示顺序）。
     *
     * @param conn     数据库连接
     * @param recipeId 配方编号
     * @return 明细列表（带出食材分类与属性是否可用）
     */
    List<RecipeItem> listByRecipe(Connection conn, Long recipeId);

    /**
     * 批量查询多个配方下的全部明细。
     *
     * <p>用于配方列表一次性计算所有配方的风味画像，避免逐条配方发 SQL。
     * 集合为空时不发 SQL，直接返回空列表。</p>
     *
     * @param conn      数据库连接
     * @param recipeIds 配方编号集合
     * @return 明细列表，按配方编号与显示顺序排列
     */
    List<RecipeItem> listByRecipeIds(Connection conn, List<Long> recipeIds);

    /**
     * 删除配方下的全部明细（编辑配方时先删后插）。
     *
     * @param conn     数据库连接
     * @param recipeId 配方编号
     * @return 删除行数
     */
    int deleteByRecipe(Connection conn, Long recipeId);

    /**
     * 新增单条明细。
     *
     * @param conn 数据库连接
     * @param item 明细
     * @return 新明细编号
     */
    long insert(Connection conn, RecipeItem item);

    /**
     * 统计配方明细条数。
     *
     * @param conn     数据库连接
     * @param recipeId 配方编号
     * @return 明细条数
     */
    long countByRecipe(Connection conn, Long recipeId);
}
