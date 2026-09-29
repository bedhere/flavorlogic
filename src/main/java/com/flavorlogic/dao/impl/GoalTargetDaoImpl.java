package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.GoalTargetDao;
import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.model.GoalTarget;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 目标项定义数据访问实现（表 {@code goal_target}）。
 *
 * <p>全部 SQL 通过 {@link JdbcHelper} 参数化执行；连接、事务由 Service 层管理，
 * 本类不关闭、不提交、不回滚传入的 {@link Connection}。</p>
 */
public class GoalTargetDaoImpl implements GoalTargetDao {

    /** 查询列：显式列出，不使用通配符取列。 */
    private static final String COLUMNS = "id, goal_type, target_kind, target_key, intent, "
            + "magnitude, weight, note, status";

    @Override
    public List<GoalTarget> listByGoalType(Connection conn, String goalType) {
        // 先按 target_kind 再按 id：保证 FLAVOR 行在前、顺序稳定，便于排查模板问题
        String sql = "SELECT " + COLUMNS + " FROM goal_target "
                + "WHERE goal_type = ? AND status = 1 ORDER BY target_kind, id";
        return JdbcHelper.queryList(conn, sql, GoalTargetDaoImpl::mapRow, goalType);
    }

    /**
     * 将结果集当前行映射为目标项对象。
     *
     * @param rs 结果集
     * @return 目标项对象
     * @throws SQLException 读取失败
     */
    private static GoalTarget mapRow(ResultSet rs) throws SQLException {
        GoalTarget target = new GoalTarget();
        target.setId(rs.getLong("id"));
        target.setGoalType(rs.getString("goal_type"));
        target.setTargetKind(rs.getString("target_kind"));
        target.setTargetKey(rs.getString("target_key"));
        target.setIntent(rs.getString("intent"));
        // magnitude 允许为空：为空表示「按区域画像权重折算」，不能兜底成 1 否则区域适配会失效
        target.setMagnitude(rs.getBigDecimal("magnitude"));
        // weight 若为空则视为 1（参与优化），避免 NULL 传播进权重计算
        BigDecimal weight = rs.getBigDecimal("weight");
        target.setWeight(weight == null ? BigDecimal.ONE : weight);
        target.setNote(rs.getString("note"));
        target.setStatus(JdbcHelper.getInteger(rs, "status"));
        return target;
    }
}
