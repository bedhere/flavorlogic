package com.flavorlogic.dao;

import com.flavorlogic.model.FlavorRule;

import java.sql.Connection;
import java.util.List;

/**
 * 风味补偿规则数据访问接口（表 {@code flavor_rule}）。
 */
public interface FlavorRuleDao {

    /**
     * 查询全部启用规则（按优先级升序）。
     *
     * @param conn 数据库连接
     * @return 规则列表
     */
    List<FlavorRule> listEnabled(Connection conn);

    /**
     * 查询适用于指定目标类型的启用规则（含 GENERAL 通用规则）。
     *
     * @param conn     数据库连接
     * @param goalType 目标类型
     * @return 规则列表
     */
    List<FlavorRule> listEnabledByGoal(Connection conn, String goalType);

    /**
     * 分页查询规则（管理端）。
     *
     * @param conn     数据库连接
     * @param keyword  规则名称或编码关键字，可为空
     * @param goalType 目标类型筛选，可为空
     * @param status   状态筛选，可为空
     * @param offset   偏移量
     * @param limit    每页条数
     * @return 规则列表
     */
    List<FlavorRule> page(Connection conn, String keyword, String goalType, Integer status, int offset, int limit);

    /**
     * 统计满足条件的规则数。
     *
     * @param conn     数据库连接
     * @param keyword  关键字，可为空
     * @param goalType 目标类型筛选，可为空
     * @param status   状态筛选，可为空
     * @return 记录数
     */
    long count(Connection conn, String keyword, String goalType, Integer status);

    /**
     * 按主键查询。
     *
     * @param conn 数据库连接
     * @param id   规则编号
     * @return 规则或 null
     */
    FlavorRule findById(Connection conn, Long id);

    /**
     * 按规则编码查询。
     *
     * @param conn     数据库连接
     * @param ruleCode 规则编码
     * @return 规则或 null
     */
    FlavorRule findByCode(Connection conn, String ruleCode);

    /**
     * 新增规则。
     *
     * @param conn 数据库连接
     * @param rule 规则
     * @return 新规则编号
     */
    long insert(Connection conn, FlavorRule rule);

    /**
     * 更新规则。
     *
     * @param conn 数据库连接
     * @param rule 规则（需含编号）
     * @return 影响行数
     */
    int update(Connection conn, FlavorRule rule);

    /**
     * 更新规则状态。
     *
     * @param conn   数据库连接
     * @param id     规则编号
     * @param status 目标状态
     * @return 影响行数
     */
    int updateStatus(Connection conn, Long id, int status);

    /**
     * 统计启用规则数。
     *
     * @param conn 数据库连接
     * @return 启用规则数
     */
    long countEnabled(Connection conn);
}
