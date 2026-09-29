package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.AnalysisFeedbackDao;
import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.model.AnalysisFeedback;
import com.flavorlogic.util.DateTimeUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 分析反馈数据访问实现（表 {@code analysis_feedback}）。
 *
 * <p>同一用户对同一任务只保留一条记录，靠唯一键 {@code uk_feedback_task_user (task_id, user_id)}
 * 配合 {@code ON DUPLICATE KEY UPDATE} 实现“存在即更新”。连接与事务由 Service 层管理。</p>
 */
public class AnalysisFeedbackDaoImpl implements AnalysisFeedbackDao {

    /** 反馈表全部列（comment 为关键字，用反引号包裹） */
    private static final String COLUMNS =
            "id, task_id, user_id, helpful, rating, `comment`, trial_result, created_at, updated_at";

    @Override
    public int upsert(Connection conn, AnalysisFeedback feedback) {
        // created_at / updated_at 交给数据库默认值与 ON UPDATE 维护
        String sql = "INSERT INTO analysis_feedback (task_id, user_id, helpful, rating, `comment`, trial_result) "
                + "VALUES (?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE helpful = VALUES(helpful), rating = VALUES(rating), "
                + "`comment` = VALUES(`comment`), trial_result = VALUES(trial_result)";
        return JdbcHelper.update(conn, sql,
                feedback.getTaskId(),
                feedback.getUserId(),
                feedback.getHelpful(),
                feedback.getRating(),
                feedback.getComment(),
                feedback.getTrialResult());
    }

    @Override
    public AnalysisFeedback findByTaskAndUser(Connection conn, Long taskId, Long userId) {
        String sql = "SELECT " + COLUMNS + " FROM analysis_feedback WHERE task_id = ? AND user_id = ?";
        return JdbcHelper.queryOne(conn, sql, AnalysisFeedbackDaoImpl::mapRow, taskId, userId);
    }

    @Override
    public List<AnalysisFeedback> listByTask(Connection conn, Long taskId) {
        String sql = "SELECT " + COLUMNS + " FROM analysis_feedback WHERE task_id = ?"
                + " ORDER BY created_at DESC, id DESC";
        return JdbcHelper.queryList(conn, sql, AnalysisFeedbackDaoImpl::mapRow, taskId);
    }

    @Override
    public long countByUser(Connection conn, Long userId) {
        String sql = "SELECT COUNT(*) FROM analysis_feedback WHERE user_id = ?";
        return JdbcHelper.queryLong(conn, sql, userId);
    }

    /**
     * 映射反馈行。
     *
     * @param rs 结果集
     * @return 反馈对象
     * @throws SQLException 读取失败
     */
    private static AnalysisFeedback mapRow(ResultSet rs) throws SQLException {
        AnalysisFeedback feedback = new AnalysisFeedback();
        feedback.setId(JdbcHelper.getLong(rs, "id"));
        feedback.setTaskId(JdbcHelper.getLong(rs, "task_id"));
        feedback.setUserId(JdbcHelper.getLong(rs, "user_id"));
        // helpful、rating 为可空 TINYINT 列
        feedback.setHelpful(JdbcHelper.getInteger(rs, "helpful"));
        feedback.setRating(JdbcHelper.getInteger(rs, "rating"));
        feedback.setComment(rs.getString("comment"));
        feedback.setTrialResult(rs.getString("trial_result"));
        feedback.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        feedback.setUpdatedAt(DateTimeUtil.format(rs.getTimestamp("updated_at")));
        return feedback;
    }
}
