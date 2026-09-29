package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.IngredientDao;
import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.model.Ingredient;
import com.flavorlogic.util.DateTimeUtil;
import com.flavorlogic.util.TextUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 食材与风味属性数据访问实现（表 {@code ingredient}）。
 *
 * <p>全部 SQL 通过 {@link JdbcHelper} 参数化执行；连接、事务由 Service 层管理，
 * 本类不关闭、不提交、不回滚传入的 {@link Connection}。</p>
 */
public class IngredientDaoImpl implements IngredientDao {

    /** 查询列：显式列出，不使用通配符取列。 */
    private static final String COLUMNS = "id, name, category, default_unit, "
            + "sweet, salty, sour, bitter, umami, spicy, numbing, fat_aroma, "
            + "description, data_source, status, created_at, updated_at";

    /** 默认用量单位，与建表语句的 DEFAULT 'g' 保持一致。 */
    private static final String DEFAULT_UNIT = "g";

    /** 默认状态：1 启用，与建表语句的 DEFAULT 1 保持一致。 */
    private static final int DEFAULT_STATUS = 1;

    @Override
    public List<Ingredient> listEnabled(Connection conn) {
        String sql = "SELECT " + COLUMNS + " FROM ingredient WHERE status = 1 ORDER BY category, id";
        return JdbcHelper.queryList(conn, sql, IngredientDaoImpl::mapRow);
    }

    @Override
    public List<Ingredient> findByIds(Connection conn, List<Long> ids) {
        // 集合为空时不发 SQL，直接返回空列表，避免生成非法的 IN () 语句
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM ingredient WHERE id IN (");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(") ORDER BY id");
        return JdbcHelper.queryList(conn, sql.toString(), IngredientDaoImpl::mapRow, ids.toArray());
    }

    @Override
    public Ingredient findById(Connection conn, Long id) {
        String sql = "SELECT " + COLUMNS + " FROM ingredient WHERE id = ?";
        return JdbcHelper.queryOne(conn, sql, IngredientDaoImpl::mapRow, id);
    }

    @Override
    public Ingredient findByName(Connection conn, String name) {
        String sql = "SELECT " + COLUMNS + " FROM ingredient WHERE name = ?";
        return JdbcHelper.queryOne(conn, sql, IngredientDaoImpl::mapRow, name);
    }

    @Override
    public List<Ingredient> page(Connection conn, String keyword, String category, Integer status, int offset, int limit) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT " + COLUMNS + " FROM ingredient" + buildCondition(keyword, category, status, params)
                + " ORDER BY id DESC LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        return JdbcHelper.queryList(conn, sql, IngredientDaoImpl::mapRow, params.toArray());
    }

    @Override
    public long count(Connection conn, String keyword, String category, Integer status) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM ingredient" + buildCondition(keyword, category, status, params);
        return JdbcHelper.queryLong(conn, sql, params.toArray());
    }

    @Override
    public List<String> listCategories(Connection conn) {
        String sql = "SELECT DISTINCT category FROM ingredient WHERE category IS NOT NULL ORDER BY category";
        return JdbcHelper.<String>queryList(conn, sql, rs -> rs.getString(1));
    }

    @Override
    public long insert(Connection conn, Ingredient ingredient) {
        String sql = "INSERT INTO ingredient (name, category, default_unit, "
                + "sweet, salty, sour, bitter, umami, spicy, numbing, fat_aroma, "
                + "description, data_source, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return JdbcHelper.insertAndReturnKey(conn, sql,
                ingredient.getName(),
                ingredient.getCategory(),
                TextUtil.defaultIfBlank(ingredient.getDefaultUnit(), DEFAULT_UNIT),
                zeroIfNull(ingredient.getSweet()),
                zeroIfNull(ingredient.getSalty()),
                zeroIfNull(ingredient.getSour()),
                zeroIfNull(ingredient.getBitter()),
                zeroIfNull(ingredient.getUmami()),
                zeroIfNull(ingredient.getSpicy()),
                zeroIfNull(ingredient.getNumbing()),
                zeroIfNull(ingredient.getFatAroma()),
                ingredient.getDescription(),
                ingredient.getDataSource(),
                ingredient.getStatus() == null ? DEFAULT_STATUS : ingredient.getStatus());
    }

    @Override
    public int update(Connection conn, Ingredient ingredient) {
        String sql = "UPDATE ingredient SET name = ?, category = ?, default_unit = ?, "
                + "sweet = ?, salty = ?, sour = ?, bitter = ?, umami = ?, spicy = ?, numbing = ?, fat_aroma = ?, "
                + "description = ?, data_source = ?, status = ? WHERE id = ?";
        return JdbcHelper.update(conn, sql,
                ingredient.getName(),
                ingredient.getCategory(),
                TextUtil.defaultIfBlank(ingredient.getDefaultUnit(), DEFAULT_UNIT),
                zeroIfNull(ingredient.getSweet()),
                zeroIfNull(ingredient.getSalty()),
                zeroIfNull(ingredient.getSour()),
                zeroIfNull(ingredient.getBitter()),
                zeroIfNull(ingredient.getUmami()),
                zeroIfNull(ingredient.getSpicy()),
                zeroIfNull(ingredient.getNumbing()),
                zeroIfNull(ingredient.getFatAroma()),
                ingredient.getDescription(),
                ingredient.getDataSource(),
                ingredient.getStatus() == null ? DEFAULT_STATUS : ingredient.getStatus(),
                ingredient.getId());
    }

    @Override
    public int updateStatus(Connection conn, Long id, int status) {
        String sql = "UPDATE ingredient SET status = ? WHERE id = ?";
        return JdbcHelper.update(conn, sql, status, id);
    }

    @Override
    public long countAll(Connection conn) {
        String sql = "SELECT COUNT(*) FROM ingredient";
        return JdbcHelper.queryLong(conn, sql);
    }

    @Override
    public long countEnabled(Connection conn) {
        String sql = "SELECT COUNT(*) FROM ingredient WHERE status = 1";
        return JdbcHelper.queryLong(conn, sql);
    }

    /**
     * 组装管理端分页与统计共用的筛选条件。
     *
     * @param keyword  名称关键字，可为空
     * @param category 分类，可为空
     * @param status   状态，可为空
     * @param params   出参：按占位符顺序收集的绑定参数
     * @return WHERE 子句，无条件时返回空串
     */
    private static String buildCondition(String keyword, String category, Integer status, List<Object> params) {
        List<String> conditions = new ArrayList<>();
        if (!TextUtil.isBlank(keyword)) {
            conditions.add("name LIKE ?");
            params.add(TextUtil.likePattern(keyword));
        }
        if (!TextUtil.isBlank(category)) {
            conditions.add("category = ?");
            params.add(category.trim());
        }
        if (status != null) {
            conditions.add("status = ?");
            params.add(status);
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    /**
     * 将结果集当前行映射为食材对象。
     *
     * @param rs 结果集
     * @return 食材对象
     * @throws SQLException 读取失败
     */
    private static Ingredient mapRow(ResultSet rs) throws SQLException {
        Ingredient ingredient = new Ingredient();
        ingredient.setId(rs.getLong("id"));
        ingredient.setName(rs.getString("name"));
        ingredient.setCategory(rs.getString("category"));
        ingredient.setDefaultUnit(rs.getString("default_unit"));
        // 八维属性列不允许为 NULL，但读取时仍做空值兜底，保证分析引擎拿到确定的数值
        ingredient.setSweet(zeroIfNull(rs.getBigDecimal("sweet")));
        ingredient.setSalty(zeroIfNull(rs.getBigDecimal("salty")));
        ingredient.setSour(zeroIfNull(rs.getBigDecimal("sour")));
        ingredient.setBitter(zeroIfNull(rs.getBigDecimal("bitter")));
        ingredient.setUmami(zeroIfNull(rs.getBigDecimal("umami")));
        ingredient.setSpicy(zeroIfNull(rs.getBigDecimal("spicy")));
        ingredient.setNumbing(zeroIfNull(rs.getBigDecimal("numbing")));
        ingredient.setFatAroma(zeroIfNull(rs.getBigDecimal("fat_aroma")));
        ingredient.setDescription(rs.getString("description"));
        ingredient.setDataSource(rs.getString("data_source"));
        ingredient.setStatus(JdbcHelper.getInteger(rs, "status"));
        ingredient.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        ingredient.setUpdatedAt(DateTimeUtil.format(rs.getTimestamp("updated_at")));
        return ingredient;
    }

    /**
     * 数值空值兜底。
     *
     * @param value 原值
     * @return 原值或 {@link BigDecimal#ZERO}
     */
    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
