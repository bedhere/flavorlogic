package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.dao.RecipeDao;
import com.flavorlogic.model.Recipe;
import com.flavorlogic.util.DateTimeUtil;
import com.flavorlogic.util.TextUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 配方主表数据访问实现（表 {@code recipe}）。
 *
 * <p>列表查询统一带出明细条数 {@code item_count}，避免 Service 层逐条再查；
 * 删除采用软删除（{@code status = 0}），保证历史分析快照仍可回看。</p>
 */
public class RecipeDaoImpl implements RecipeDao {

    /** 查询列：显式列出并带表别名，不使用通配符取列。 */
    private static final String COLUMNS = "r.id, r.user_id, r.name, r.product_type, r.process_note, "
            + "r.remark, r.version_no, r.status, r.created_at, r.updated_at";

    /** 明细条数子查询，列表与详情共用。 */
    private static final String ITEM_COUNT = "(SELECT COUNT(*) FROM recipe_item ri WHERE ri.recipe_id = r.id) AS item_count";

    /** 默认版本号，与建表语句的 DEFAULT 1 保持一致。 */
    private static final int DEFAULT_VERSION_NO = 1;

    /** 默认状态：1 正常，与建表语句的 DEFAULT 1 保持一致。 */
    private static final int DEFAULT_STATUS = Recipe.STATUS_ACTIVE;

    @Override
    public List<Recipe> pageByUser(Connection conn, Long userId, String keyword, int offset, int limit) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT " + COLUMNS + ", " + ITEM_COUNT + " FROM recipe r"
                + buildUserCondition(userId, keyword, params)
                + " ORDER BY r.updated_at DESC, r.id DESC LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        return JdbcHelper.queryList(conn, sql, RecipeDaoImpl::mapRow, params.toArray());
    }

    @Override
    public long countByUser(Connection conn, Long userId, String keyword) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM recipe r" + buildUserCondition(userId, keyword, params);
        return JdbcHelper.queryLong(conn, sql, params.toArray());
    }

    @Override
    public Recipe findById(Connection conn, Long id) {
        // 不过滤 status：已软删除的配方仍需支撑历史分析回看
        String sql = "SELECT " + COLUMNS + ", " + ITEM_COUNT + " FROM recipe r WHERE r.id = ?";
        return JdbcHelper.queryOne(conn, sql, RecipeDaoImpl::mapRow, id);
    }

    @Override
    public long insert(Connection conn, Recipe recipe) {
        String sql = "INSERT INTO recipe (user_id, name, product_type, process_note, remark, version_no, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        return JdbcHelper.insertAndReturnKey(conn, sql,
                recipe.getUserId(),
                recipe.getName(),
                recipe.getProductType(),
                recipe.getProcessNote(),
                recipe.getRemark(),
                recipe.getVersionNo() == null ? DEFAULT_VERSION_NO : recipe.getVersionNo(),
                recipe.getStatus() == null ? DEFAULT_STATUS : recipe.getStatus());
    }

    @Override
    public int update(Connection conn, Recipe recipe) {
        // 归属 user_id 不可变更，因此不参与更新
        String sql = "UPDATE recipe SET name = ?, product_type = ?, process_note = ?, remark = ?, "
                + "version_no = ?, status = ? WHERE id = ?";
        return JdbcHelper.update(conn, sql,
                recipe.getName(),
                recipe.getProductType(),
                recipe.getProcessNote(),
                recipe.getRemark(),
                recipe.getVersionNo() == null ? DEFAULT_VERSION_NO : recipe.getVersionNo(),
                recipe.getStatus() == null ? DEFAULT_STATUS : recipe.getStatus(),
                recipe.getId());
    }

    @Override
    public int softDelete(Connection conn, Long id) {
        String sql = "UPDATE recipe SET status = 0 WHERE id = ?";
        return JdbcHelper.update(conn, sql, id);
    }

    @Override
    public List<Recipe> listRecentByUser(Connection conn, Long userId, int limit) {
        String sql = "SELECT " + COLUMNS + ", " + ITEM_COUNT + " FROM recipe r "
                + "WHERE r.user_id = ? AND r.status = 1 ORDER BY r.updated_at DESC, r.id DESC LIMIT ?";
        return JdbcHelper.queryList(conn, sql, RecipeDaoImpl::mapRow, userId, limit);
    }

    @Override
    public long countActiveByUser(Connection conn, Long userId) {
        String sql = "SELECT COUNT(*) FROM recipe WHERE user_id = ? AND status = 1";
        return JdbcHelper.queryLong(conn, sql, userId);
    }

    @Override
    public long countAll(Connection conn) {
        String sql = "SELECT COUNT(*) FROM recipe WHERE status = 1";
        return JdbcHelper.queryLong(conn, sql);
    }

    /**
     * 组装“某用户的启用配方”筛选条件，供分页与统计共用。
     *
     * @param userId  用户编号
     * @param keyword 配方名称关键字，可为空
     * @param params  出参：按占位符顺序收集的绑定参数
     * @return WHERE 子句
     */
    private static String buildUserCondition(Long userId, String keyword, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE r.user_id = ? AND r.status = 1");
        params.add(userId);
        if (!TextUtil.isBlank(keyword)) {
            where.append(" AND r.name LIKE ?");
            params.add(TextUtil.likePattern(keyword));
        }
        return where.toString();
    }

    /**
     * 将结果集当前行映射为配方对象。
     *
     * @param rs 结果集
     * @return 配方对象
     * @throws SQLException 读取失败
     */
    private static Recipe mapRow(ResultSet rs) throws SQLException {
        Recipe recipe = new Recipe();
        recipe.setId(rs.getLong("id"));
        recipe.setUserId(JdbcHelper.getLong(rs, "user_id"));
        recipe.setName(rs.getString("name"));
        recipe.setProductType(rs.getString("product_type"));
        recipe.setProcessNote(rs.getString("process_note"));
        recipe.setRemark(rs.getString("remark"));
        recipe.setVersionNo(JdbcHelper.getInteger(rs, "version_no"));
        recipe.setStatus(JdbcHelper.getInteger(rs, "status"));
        recipe.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        recipe.setUpdatedAt(DateTimeUtil.format(rs.getTimestamp("updated_at")));
        recipe.setItemCount(JdbcHelper.getInteger(rs, "item_count"));
        return recipe;
    }
}
