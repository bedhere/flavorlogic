package com.flavorlogic.dao;

import com.flavorlogic.model.RegionProfile;

import java.sql.Connection;
import java.util.List;

/**
 * 区域口味画像数据访问接口（表 {@code region_profile}）。
 */
public interface RegionProfileDao {

    /**
     * 查询启用中的区域画像。
     *
     * @param conn 数据库连接
     * @return 区域画像列表
     */
    List<RegionProfile> listEnabled(Connection conn);

    /**
     * 查询全部区域画像。
     *
     * @param conn 数据库连接
     * @return 区域画像列表
     */
    List<RegionProfile> listAll(Connection conn);

    /**
     * 按主键查询。
     *
     * @param conn 数据库连接
     * @param id   区域画像编号
     * @return 区域画像或 null
     */
    RegionProfile findById(Connection conn, Long id);

    /**
     * 按区域编码查询。
     *
     * @param conn 数据库连接
     * @param code 区域编码
     * @return 区域画像或 null
     */
    RegionProfile findByCode(Connection conn, String code);

    /**
     * 新增区域画像。
     *
     * @param conn    数据库连接
     * @param profile 区域画像
     * @return 新记录编号
     */
    long insert(Connection conn, RegionProfile profile);

    /**
     * 更新区域画像。
     *
     * @param conn    数据库连接
     * @param profile 区域画像（需含编号）
     * @return 影响行数
     */
    int update(Connection conn, RegionProfile profile);
}
