package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.dao.RecipeItemDao;
import com.flavorlogic.model.RecipeItem;
import com.flavorlogic.util.DateTimeUtil;
import com.flavorlogic.util.TextUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 配方食材明细数据访问实现（表 {@code recipe_item}）。
 *
 * <p>明细同时保存食材名称与单位成本快照，即使基础食材数据后续调整，
 * 历史配方与历史分析结果仍能还原当时的输入。</p>
 */
public class RecipeItemDaoImpl implements RecipeItemDao {

    /** 查询列：明细全列 + 食材分类 + 属性是否可用，显式列出不使用通配符。 */
    private static final String COLUMNS = "ri.id, ri.recipe_id, ri.ingredient_id, ri.ingredient_name, "
            + "ri.amount, ri.unit, ri.cost_per_unit, ri.sort_no, ri.created_at, ri.updated_at, "
            + "i.category AS ingredient_category, "
            + "CASE WHEN i.id IS NULL THEN 0 ELSE 1 END AS attribute_available";

    /** 默认用量单位，与建表语句的 DEFAULT 'g' 保持一致。 */
    private static final String DEFAULT_UNIT = "g";

    /** 默认显示顺序，与建表语句的 DEFAULT 0 保持一致。 */
    private static final int DEFAULT_SORT_NO = 0;

    @Override
    public List<RecipeItem> listByRecipe(Connection conn, Long recipeId) {
        // LEFT JOIN：食材被停用或已删除时仍保留明细行，用 attribute_available 标记属性缺失
        String sql = "SELECT " + COLUMNS + " FROM recipe_item ri "
                + "LEFT JOIN ingredient i ON ri.ingredient_id = i.id "
                + "WHERE ri.recipe_id = ? ORDER BY ri.sort_no, ri.id";
        return JdbcHelper.queryList(conn, sql, RecipeItemDaoImpl::mapRow, recipeId);
    }

    @Override
    public List<RecipeItem> listByRecipeIds(Connection conn, List<Long> recipeIds) {
        // 集合为空时不发 SQL，避免生成非法的 IN () 语句
        if (recipeIds == null || recipeIds.isEmpty()) {
            return new ArrayList<>();
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < recipeIds.size(); i++) {
            placeholders.append(i == 0 ? "?" : ", ?");
        }
        String sql = "SELECT " + COLUMNS + " FROM recipe_item ri "
                + "LEFT JOIN ingredient i ON ri.ingredient_id = i.id "
                + "WHERE ri.recipe_id IN (" + placeholders + ") "
                + "ORDER BY ri.recipe_id, ri.sort_no, ri.id";
        return JdbcHelper.queryList(conn, sql, RecipeItemDaoImpl::mapRow, recipeIds.toArray());
    }

    @Override
    public int deleteByRecipe(Connection conn, Long recipeId) {
        String sql = "DELETE FROM recipe_item WHERE recipe_id = ?";
        return JdbcHelper.update(conn, sql, recipeId);
    }

    @Override
    public long insert(Connection conn, RecipeItem item) {
        String sql = "INSERT INTO recipe_item (recipe_id, ingredient_id, ingredient_name, "
                + "amount, unit, cost_per_unit, sort_no) VALUES (?, ?, ?, ?, ?, ?, ?)";
        return JdbcHelper.insertAndReturnKey(conn, sql,
                item.getRecipeId(),
                item.getIngredientId(),
                item.getIngredientName(),
                item.getAmount(),
                TextUtil.defaultIfBlank(item.getUnit(), DEFAULT_UNIT),
                item.getCostPerUnit(),
                item.getSortNo() == null ? DEFAULT_SORT_NO : item.getSortNo());
    }

    @Override
    public long countByRecipe(Connection conn, Long recipeId) {
        String sql = "SELECT COUNT(*) FROM recipe_item WHERE recipe_id = ?";
        return JdbcHelper.queryLong(conn, sql, recipeId);
    }

    /**
     * 将结果集当前行映射为明细对象。
     *
     * @param rs 结果集
     * @return 明细对象
     * @throws SQLException 读取失败
     */
    private static RecipeItem mapRow(ResultSet rs) throws SQLException {
        RecipeItem item = new RecipeItem();
        item.setId(rs.getLong("id"));
        item.setRecipeId(JdbcHelper.getLong(rs, "recipe_id"));
        item.setIngredientId(JdbcHelper.getLong(rs, "ingredient_id"));
        item.setIngredientName(rs.getString("ingredient_name"));
        item.setAmount(rs.getBigDecimal("amount"));
        item.setUnit(rs.getString("unit"));
        item.setCostPerUnit(rs.getBigDecimal("cost_per_unit"));
        item.setSortNo(JdbcHelper.getInteger(rs, "sort_no"));
        item.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        item.setUpdatedAt(DateTimeUtil.format(rs.getTimestamp("updated_at")));
        // 关联查询带出的冗余字段：食材分类与属性可用标记
        item.setIngredientCategory(rs.getString("ingredient_category"));
        item.setAttributeAvailable(rs.getInt("attribute_available") == 1);
        return item;
    }
}
