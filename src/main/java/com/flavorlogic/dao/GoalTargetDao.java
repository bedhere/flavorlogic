package com.flavorlogic.dao;

import com.flavorlogic.model.GoalTarget;

import java.sql.Connection;
import java.util.List;

/**
 * 目标项定义数据访问接口（表 {@code goal_target}）。
 */
public interface GoalTargetDao {

    /**
     * 查询某个研发目标下全部启用中的目标项。
     *
     * <p>一次取回该目标的全部规则（8 条风味层 ＋ 可选若干条成分层），
     * 由 Service 在内存中展开，避免逐维度发 SQL。</p>
     *
     * @param conn     数据库连接
     * @param goalType 研发目标类型
     * @return 目标项列表，按目标种类与编号排序；无匹配时返回空列表
     */
    List<GoalTarget> listByGoalType(Connection conn, String goalType);
}
