package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.FlavorRuleDao;
import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.model.FlavorRule;
import com.flavorlogic.util.DateTimeUtil;
import com.flavorlogic.util.TextUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 风味补偿规则数据访问实现（表 {@code flavor_rule}）。
 *
 * <p>{@code condition_json} / {@code action_json} 为 MySQL JSON 列，
 * 这里按字符串原样读出并写入，规则解析交由 {@code RuleEngine} 处理。</p>
 */
public class FlavorRuleDaoImpl implements FlavorRuleDao {

    /** 查询列：显式列出，不使用通配符取列。 */
    private static final String COLUMNS = "id, rule_code, rule_name, goal_type, priority, "
            + "condition_json, action_json, explanation, status, created_at, updated_at";

    /** 默认目标类型，与建表语句的 DEFAULT 'GENERAL' 保持一致。 */
    private static final String DEFAULT_GOAL_TYPE = FlavorRule.GOAL_GENERAL;

    /** 默认优先级，与建表语句的 DEFAULT 100 保持一致。 */
    private static final int DEFAULT_PRIORITY = 100;

    /** 默认状态：1 启用，与建表语句的 DEFAULT 1 保持一致。 */
    private static final int DEFAULT_STATUS = 1;

    @Override
    public List<FlavorRule> listEnabled(Connection conn) {
        String sql = "SELECT " + COLUMNS + " FROM flavor_rule WHERE status = 1 ORDER BY priority, id";
        return JdbcHelper.queryList(conn, sql, FlavorRuleDaoImpl::mapRow);
    }

    @Override
    public List<FlavorRule> listEnabledByGoal(Connection conn, String goalType) {
        // 'GENERAL' 为固定常量，不是用户输入，无需绑定参数
        String sql = "SELECT " + COLUMNS + " FROM flavor_rule "
                + "WHERE status = 1 AND (goal_type = ? OR goal_type = 'GENERAL') ORDER BY priority, id";
        return JdbcHelper.queryList(conn, sql, FlavorRuleDaoImpl::mapRow, goalType);
    }

    @Override
    public List<FlavorRule> page(Connection conn, String keyword, String goalType, Integer status, int offset, int limit) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT " + COLUMNS + " FROM flavor_rule" + buildCondition(keyword, goalType, status, params)
                + " ORDER BY id DESC LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        return JdbcHelper.queryList(conn, sql, FlavorRuleDaoImpl::mapRow, params.toArray());
    }

    @Override
    public long count(Connection conn, String keyword, String goalType, Integer status) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM flavor_rule" + buildCondition(keyword, goalType, status, params);
        return JdbcHelper.queryLong(conn, sql, params.toArray());
    }

    @Override
    public FlavorRule findById(Connection conn, Long id) {
        String sql = "SELECT " + COLUMNS + " FROM flavor_rule WHERE id = ?";
        return JdbcHelper.queryOne(conn, sql, FlavorRuleDaoImpl::mapRow, id);
    }

    @Override
    public FlavorRule findByCode(Connection conn, String ruleCode) {
        String sql = "SELECT " + COLUMNS + " FROM flavor_rule WHERE rule_code = ?";
        return JdbcHelper.queryOne(conn, sql, FlavorRuleDaoImpl::mapRow, ruleCode);
    }

    @Override
    public long insert(Connection conn, FlavorRule rule) {
        String sql = "INSERT INTO flavor_rule (rule_code, rule_name, goal_type, priority, "
                + "condition_json, action_json, explanation, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        return JdbcHelper.insertAndReturnKey(conn, sql,
                rule.getRuleCode(),
                rule.getRuleName(),
                TextUtil.defaultIfBlank(rule.getGoalType(), DEFAULT_GOAL_TYPE),
                rule.getPriority() == null ? DEFAULT_PRIORITY : rule.getPriority(),
                rule.getConditionJson(),
                rule.getActionJson(),
                rule.getExplanation(),
                rule.getStatus() == null ? DEFAULT_STATUS : rule.getStatus());
    }

    @Override
    public int update(Connection conn, FlavorRule rule) {
        String sql = "UPDATE flavor_rule SET rule_code = ?, rule_name = ?, goal_type = ?, priority = ?, "
                + "condition_json = ?, action_json = ?, explanation = ?, status = ? WHERE id = ?";
        return JdbcHelper.update(conn, sql,
                rule.getRuleCode(),
                rule.getRuleName(),
                TextUtil.defaultIfBlank(rule.getGoalType(), DEFAULT_GOAL_TYPE),
                rule.getPriority() == null ? DEFAULT_PRIORITY : rule.getPriority(),
                rule.getConditionJson(),
                rule.getActionJson(),
                rule.getExplanation(),
                rule.getStatus() == null ? DEFAULT_STATUS : rule.getStatus(),
                rule.getId());
    }

    @Override
    public int updateStatus(Connection conn, Long id, int status) {
        String sql = "UPDATE flavor_rule SET status = ? WHERE id = ?";
        return JdbcHelper.update(conn, sql, status, id);
    }

    @Override
    public long countEnabled(Connection conn) {
        String sql = "SELECT COUNT(*) FROM flavor_rule WHERE status = 1";
        return JdbcHelper.queryLong(conn, sql);
    }

    /**
     * 组装管理端分页与统计共用的筛选条件。
     *
     * @param keyword  规则名称或编码关键字，可为空
     * @param goalType 目标类型，可为空
     * @param status   状态，可为空
     * @param params   出参：按占位符顺序收集的绑定参数
     * @return WHERE 子句，无条件时返回空串
     */
    private static String buildCondition(String keyword, String goalType, Integer status, List<Object> params) {
        List<String> conditions = new ArrayList<>();
        if (!TextUtil.isBlank(keyword)) {
            conditions.add("(rule_name LIKE ? OR rule_code LIKE ?)");
            String pattern = TextUtil.likePattern(keyword);
            params.add(pattern);
            params.add(pattern);
        }
        if (!TextUtil.isBlank(goalType)) {
            conditions.add("goal_type = ?");
            params.add(goalType.trim());
        }
        if (status != null) {
            conditions.add("status = ?");
            params.add(status);
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    /**
     * 将结果集当前行映射为规则对象。
     *
     * @param rs 结果集
     * @return 规则对象
     * @throws SQLException 读取失败
     */
    private static FlavorRule mapRow(ResultSet rs) throws SQLException {
        FlavorRule rule = new FlavorRule();
        rule.setId(rs.getLong("id"));
        rule.setRuleCode(rs.getString("rule_code"));
        rule.setRuleName(rs.getString("rule_name"));
        rule.setGoalType(rs.getString("goal_type"));
        rule.setPriority(JdbcHelper.getInteger(rs, "priority"));
        // JSON 列按字符串原样读取，保持规则原文便于后台展示与引擎解析
        rule.setConditionJson(rs.getString("condition_json"));
        rule.setActionJson(rs.getString("action_json"));
        rule.setExplanation(rs.getString("explanation"));
        rule.setStatus(JdbcHelper.getInteger(rs, "status"));
        rule.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        rule.setUpdatedAt(DateTimeUtil.format(rs.getTimestamp("updated_at")));
        return rule;
    }
}
