package com.flavorlogic.dao;

import com.flavorlogic.model.AnalysisFeedback;

import java.sql.Connection;
import java.util.List;

/**
 * 分析反馈数据访问接口（表 {@code analysis_feedback}）。
 */
public interface AnalysisFeedbackDao {

    /**
     * 保存反馈：同一用户对同一任务只保留一条，存在则更新。
     *
     * @param conn     数据库连接
     * @param feedback 反馈
     * @return 影响行数
     */
    int upsert(Connection conn, AnalysisFeedback feedback);

    /**
     * 查询某用户对某任务的反馈。
     *
     * @param conn   数据库连接
     * @param taskId 任务编号
     * @param userId 用户编号
     * @return 反馈或 null
     */
    AnalysisFeedback findByTaskAndUser(Connection conn, Long taskId, Long userId);

    /**
     * 查询任务下的全部反馈（管理端）。
     *
     * @param conn   数据库连接
     * @param taskId 任务编号
     * @return 反馈列表
     */
    List<AnalysisFeedback> listByTask(Connection conn, Long taskId);

    /**
     * 统计用户提交的反馈数。
     *
     * @param conn   数据库连接
     * @param userId 用户编号
     * @return 反馈数
     */
    long countByUser(Connection conn, Long userId);
}
