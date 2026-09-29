package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.dao.RegionProfileDao;
import com.flavorlogic.model.RegionProfile;
import com.flavorlogic.util.DateTimeUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 区域口味画像数据访问实现（表 {@code region_profile}）。
 *
 * <p>权重列 DECIMAL(6,3)，1.000 表示与全国基准一致；写入时对空权重按 1.000 兜底，
 * 与建表语句的默认值保持同一语义。</p>
 */
public class RegionProfileDaoImpl implements RegionProfileDao {

    /** 查询列：显式列出，不使用通配符取列。 */
    private static final String COLUMNS = "id, region_code, region_name, "
            + "sweet_weight, salty_weight, sour_weight, bitter_weight, "
            + "umami_weight, spicy_weight, numbing_weight, fat_aroma_weight, "
            + "description, data_source, status, created_at, updated_at";

    /** 默认权重，与建表语句的 DEFAULT 1.000 保持一致。 */
    private static final BigDecimal DEFAULT_WEIGHT = BigDecimal.ONE;

    /** 默认状态：1 启用，与建表语句的 DEFAULT 1 保持一致。 */
    private static final int DEFAULT_STATUS = 1;

    @Override
    public List<RegionProfile> listEnabled(Connection conn) {
        String sql = "SELECT " + COLUMNS + " FROM region_profile WHERE status = 1 ORDER BY id";
        return JdbcHelper.queryList(conn, sql, RegionProfileDaoImpl::mapRow);
    }

    @Override
    public List<RegionProfile> listAll(Connection conn) {
        String sql = "SELECT " + COLUMNS + " FROM region_profile ORDER BY id";
        return JdbcHelper.queryList(conn, sql, RegionProfileDaoImpl::mapRow);
    }

    @Override
    public RegionProfile findById(Connection conn, Long id) {
        String sql = "SELECT " + COLUMNS + " FROM region_profile WHERE id = ?";
        return JdbcHelper.queryOne(conn, sql, RegionProfileDaoImpl::mapRow, id);
    }

    @Override
    public RegionProfile findByCode(Connection conn, String code) {
        String sql = "SELECT " + COLUMNS + " FROM region_profile WHERE region_code = ?";
        return JdbcHelper.queryOne(conn, sql, RegionProfileDaoImpl::mapRow, code);
    }

    @Override
    public long insert(Connection conn, RegionProfile profile) {
        String sql = "INSERT INTO region_profile (region_code, region_name, "
                + "sweet_weight, salty_weight, sour_weight, bitter_weight, "
                + "umami_weight, spicy_weight, numbing_weight, fat_aroma_weight, "
                + "description, data_source, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return JdbcHelper.insertAndReturnKey(conn, sql,
                profile.getRegionCode(),
                profile.getRegionName(),
                weightOrDefault(profile.getSweetWeight()),
                weightOrDefault(profile.getSaltyWeight()),
                weightOrDefault(profile.getSourWeight()),
                weightOrDefault(profile.getBitterWeight()),
                weightOrDefault(profile.getUmamiWeight()),
                weightOrDefault(profile.getSpicyWeight()),
                weightOrDefault(profile.getNumbingWeight()),
                weightOrDefault(profile.getFatAromaWeight()),
                profile.getDescription(),
                profile.getDataSource(),
                profile.getStatus() == null ? DEFAULT_STATUS : profile.getStatus());
    }

    @Override
    public int update(Connection conn, RegionProfile profile) {
        String sql = "UPDATE region_profile SET region_code = ?, region_name = ?, "
                + "sweet_weight = ?, salty_weight = ?, sour_weight = ?, bitter_weight = ?, "
                + "umami_weight = ?, spicy_weight = ?, numbing_weight = ?, fat_aroma_weight = ?, "
                + "description = ?, data_source = ?, status = ? WHERE id = ?";
        return JdbcHelper.update(conn, sql,
                profile.getRegionCode(),
                profile.getRegionName(),
                weightOrDefault(profile.getSweetWeight()),
                weightOrDefault(profile.getSaltyWeight()),
                weightOrDefault(profile.getSourWeight()),
                weightOrDefault(profile.getBitterWeight()),
                weightOrDefault(profile.getUmamiWeight()),
                weightOrDefault(profile.getSpicyWeight()),
                weightOrDefault(profile.getNumbingWeight()),
                weightOrDefault(profile.getFatAromaWeight()),
                profile.getDescription(),
                profile.getDataSource(),
                profile.getStatus() == null ? DEFAULT_STATUS : profile.getStatus(),
                profile.getId());
    }

    /**
     * 将结果集当前行映射为区域画像对象。
     *
     * @param rs 结果集
     * @return 区域画像对象
     * @throws SQLException 读取失败
     */
    private static RegionProfile mapRow(ResultSet rs) throws SQLException {
        RegionProfile profile = new RegionProfile();
        profile.setId(rs.getLong("id"));
        profile.setRegionCode(rs.getString("region_code"));
        profile.setRegionName(rs.getString("region_name"));
        // 权重列读取原值；模型侧 weightOf 对 null 按 1 处理，语义与建表默认值一致
        profile.setSweetWeight(rs.getBigDecimal("sweet_weight"));
        profile.setSaltyWeight(rs.getBigDecimal("salty_weight"));
        profile.setSourWeight(rs.getBigDecimal("sour_weight"));
        profile.setBitterWeight(rs.getBigDecimal("bitter_weight"));
        profile.setUmamiWeight(rs.getBigDecimal("umami_weight"));
        profile.setSpicyWeight(rs.getBigDecimal("spicy_weight"));
        profile.setNumbingWeight(rs.getBigDecimal("numbing_weight"));
        profile.setFatAromaWeight(rs.getBigDecimal("fat_aroma_weight"));
        profile.setDescription(rs.getString("description"));
        profile.setDataSource(rs.getString("data_source"));
        profile.setStatus(JdbcHelper.getInteger(rs, "status"));
        profile.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        profile.setUpdatedAt(DateTimeUtil.format(rs.getTimestamp("updated_at")));
        return profile;
    }

    /**
     * 权重空值兜底（写入 NOT NULL 列时使用）。
     *
     * @param weight 原权重
     * @return 原权重或 1.000
     */
    private static BigDecimal weightOrDefault(BigDecimal weight) {
        return weight == null ? DEFAULT_WEIGHT : weight;
    }
}
