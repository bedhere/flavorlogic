package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.AnalysisTaskDao;
import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.model.AnalysisTask;
import com.flavorlogic.util.DateTimeUtil;
import com.flavorlogic.util.TextUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 风味分析任务数据访问实现（表 {@code analysis_task}）。
 *
 * <p>本类只负责参数化 SQL 与结果集映射：连接、事务、归属校验与状态流转合法性
 * 均由 Service 层负责，因此这里不关闭、不提交、不回滚传入的 {@link Connection}。</p>
 *
 * <p>三个 JSON 快照列（constraint / baseline / target）直接以实体中的 JSON 原文
 * 作为字符串参数绑定，由 MySQL 负责校验并存为 JSON。</p>
 */
public class AnalysisTaskDaoImpl implements AnalysisTaskDao {

    /** 单表查询列：含三个 JSON 快照原文（详情场景使用） */
    private static final String FULL_COLUMNS =
            "id, user_id, recipe_id, region_profile_id, task_name, goal_type, target_region, "
                    + "constraint_json, baseline_snapshot_json, target_snapshot_json, "
                    + "status, error_message, created_at, started_at, finished_at";

    /** 详情查询列：任务全字段 + 用户昵称 + 配方名称 + 结果摘要/置信度/是否存在结果 */
    private static final String DETAIL_COLUMNS =
            "t.id, t.user_id, t.recipe_id, t.region_profile_id, t.task_name, t.goal_type, t.target_region, "
                    + "t.constraint_json, t.baseline_snapshot_json, t.target_snapshot_json, "
                    + "t.status, t.error_message, t.created_at, t.started_at, t.finished_at, "
                    + "u.nickname AS username, r.name AS recipe_name, "
                    + "ar.summary AS summary, ar.confidence AS confidence, "
                    + "CASE WHEN ar.id IS NULL THEN 0 ELSE 1 END AS has_result";

    /** 列表查询列：刻意不选三个大 JSON 列，减少网络传输量 */
    private static final String LIST_COLUMNS =
            "t.id, t.user_id, t.recipe_id, t.region_profile_id, t.task_name, t.goal_type, t.target_region, "
                    + "t.status, t.error_message, t.created_at, t.started_at, t.finished_at, "
                    + "r.name AS recipe_name, ar.summary AS summary, ar.confidence AS confidence, "
                    + "CASE WHEN ar.id IS NULL THEN 0 ELSE 1 END AS has_result";

    /** 列表查询的联表片段：列表需要配方名称与结果摘要，故固定联这两张表 */
    private static final String LIST_FROM =
            " FROM analysis_task t"
                    + " LEFT JOIN recipe r ON t.recipe_id = r.id"
                    + " LEFT JOIN analysis_result ar ON ar.task_id = t.id";

    /** 统计查询的联表片段：无关键字时无须联表配方 */
    private static final String COUNT_FROM = " FROM analysis_task t";

    /** 统计查询的联表片段：按关键字匹配配方名称时联表配方 */
    private static final String COUNT_FROM_WITH_RECIPE =
            " FROM analysis_task t LEFT JOIN recipe r ON t.recipe_id = r.id";

    /** 列表排序：最近创建优先，同一秒创建用主键兜底保证稳定 */
    private static final String LIST_ORDER = " ORDER BY t.created_at DESC, t.id DESC";

    @Override
    public long insert(Connection conn, AnalysisTask task) {
        String sql = "INSERT INTO analysis_task (user_id, recipe_id, region_profile_id, task_name, goal_type, "
                + "target_region, constraint_json, baseline_snapshot_json, target_snapshot_json, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        // 状态由调用方决定，未指定时兜底为待处理
        String status = TextUtil.isBlank(task.getStatus()) ? AnalysisTask.STATUS_PENDING : task.getStatus();
        return JdbcHelper.insertAndReturnKey(conn, sql,
                task.getUserId(),
                task.getRecipeId(),
                task.getRegionProfileId(),
                task.getTaskName(),
                task.getGoalType(),
                task.getTargetRegion(),
                task.getConstraintJson(),
                task.getBaselineSnapshotJson(),
                task.getTargetSnapshotJson(),
                status);
    }

    @Override
    public AnalysisTask findById(Connection conn, Long id) {
        String sql = "SELECT " + FULL_COLUMNS + " FROM analysis_task WHERE id = ?";
        return JdbcHelper.queryOne(conn, sql, AnalysisTaskDaoImpl::mapFullRow, id);
    }

    @Override
    public AnalysisTask findDetailById(Connection conn, Long id) {
        String sql = "SELECT " + DETAIL_COLUMNS
                + " FROM analysis_task t"
                + " LEFT JOIN sys_user u ON t.user_id = u.id"
                + " LEFT JOIN recipe r ON t.recipe_id = r.id"
                + " LEFT JOIN analysis_result ar ON ar.task_id = t.id"
                + " WHERE t.id = ?";
        return JdbcHelper.queryOne(conn, sql, AnalysisTaskDaoImpl::mapDetailRow, id);
    }

    @Override
    public int markRunning(Connection conn, Long id) {
        String sql = "UPDATE analysis_task SET status = ?, started_at = NOW(), error_message = NULL WHERE id = ?";
        return JdbcHelper.update(conn, sql, AnalysisTask.STATUS_RUNNING, id);
    }

    @Override
    public int markCompleted(Connection conn, Long id) {
        String sql = "UPDATE analysis_task SET status = ?, finished_at = NOW(), error_message = NULL WHERE id = ?";
        return JdbcHelper.update(conn, sql, AnalysisTask.STATUS_COMPLETED, id);
    }

    @Override
    public int markFailed(Connection conn, Long id, String errorMessage) {
        String sql = "UPDATE analysis_task SET status = ?, finished_at = NOW(), error_message = ? WHERE id = ?";
        return JdbcHelper.update(conn, sql, AnalysisTask.STATUS_FAILED, errorMessage, id);
    }

    @Override
    public int delete(Connection conn, Long id) {
        String sql = "DELETE FROM analysis_task WHERE id = ?";
        return JdbcHelper.update(conn, sql, id);
    }

    @Override
    public List<AnalysisTask> pageByUser(Connection conn, Long userId, String keyword, String goalType, String status,
                                         int offset, int limit) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(userId, keyword, goalType, status, params);
        String sql = "SELECT " + LIST_COLUMNS + LIST_FROM + where + LIST_ORDER + " LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        return JdbcHelper.queryList(conn, sql, AnalysisTaskDaoImpl::mapListRow, params.toArray());
    }

    @Override
    public long countByUser(Connection conn, Long userId, String keyword, String goalType, String status) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(userId, keyword, goalType, status, params);
        String sql = "SELECT COUNT(*)" + countFrom(keyword) + where;
        return JdbcHelper.queryLong(conn, sql, params.toArray());
    }

    @Override
    public List<AnalysisTask> listRecentByUser(Connection conn, Long userId, int limit) {
        String sql = "SELECT " + LIST_COLUMNS + LIST_FROM + " WHERE t.user_id = ?" + LIST_ORDER + " LIMIT ?";
        return JdbcHelper.queryList(conn, sql, AnalysisTaskDaoImpl::mapListRow, userId, limit);
    }

    @Override
    public long countByUser(Connection conn, Long userId) {
        String sql = "SELECT COUNT(*) FROM analysis_task WHERE user_id = ?";
        return JdbcHelper.queryLong(conn, sql, userId);
    }

    @Override
    public long countCompletedByUser(Connection conn, Long userId) {
        String sql = "SELECT COUNT(*) FROM analysis_task WHERE user_id = ? AND status = ?";
        return JdbcHelper.queryLong(conn, sql, userId, AnalysisTask.STATUS_COMPLETED);
    }

    @Override
    public String findLastFinishedAt(Connection conn, Long userId) {
        String sql = "SELECT MAX(finished_at) FROM analysis_task WHERE user_id = ?";
        return JdbcHelper.queryOne(conn, sql, rs -> DateTimeUtil.format(rs.getTimestamp(1)), userId);
    }

    @Override
    public List<AnalysisTask> pageAll(Connection conn, String keyword, String goalType, String status,
                                      int offset, int limit) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(null, keyword, goalType, status, params);
        String sql = "SELECT " + LIST_COLUMNS + LIST_FROM + where + LIST_ORDER + " LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        return JdbcHelper.queryList(conn, sql, AnalysisTaskDaoImpl::mapListRow, params.toArray());
    }

    @Override
    public long countAll(Connection conn, String keyword, String goalType, String status) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(null, keyword, goalType, status, params);
        String sql = "SELECT COUNT(*)" + countFrom(keyword) + where;
        return JdbcHelper.queryLong(conn, sql, params.toArray());
    }

    @Override
    public long countAll(Connection conn) {
        String sql = "SELECT COUNT(*) FROM analysis_task";
        return JdbcHelper.queryLong(conn, sql);
    }

    /**
     * 生成可选筛选条件对应的 WHERE 子句，并按占位符顺序收集参数。
     *
     * @param userId   用户编号，为 null 表示不限制用户（管理端）
     * @param keyword  任务名称或配方名称关键字，可为空
     * @param goalType 目标类型，可为空
     * @param status   状态，可为空
     * @param params   参数收集容器
     * @return WHERE 子句（无条件时为空串）
     */
    private static String buildWhere(Long userId, String keyword, String goalType, String status,
                                     List<Object> params) {
        List<String> conditions = new ArrayList<>();
        if (userId != null) {
            conditions.add("t.user_id = ?");
            params.add(userId);
        }
        if (!TextUtil.isBlank(keyword)) {
            // MySQL 5.7 的 LIKE 默认转义符即反斜杠，无需 ESCAPE 子句
            String pattern = TextUtil.likePattern(keyword);
            conditions.add("(t.task_name LIKE ? OR r.name LIKE ?)");
            params.add(pattern);
            params.add(pattern);
        }
        if (!TextUtil.isBlank(goalType)) {
            conditions.add("t.goal_type = ?");
            params.add(goalType.trim());
        }
        if (!TextUtil.isBlank(status)) {
            conditions.add("t.status = ?");
            params.add(status.trim());
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    /**
     * 选择统计查询的联表片段：关键字命中配方名称时才需要联表 {@code recipe}。
     *
     * @param keyword 关键字
     * @return FROM 片段
     */
    private static String countFrom(String keyword) {
        return TextUtil.isBlank(keyword) ? COUNT_FROM : COUNT_FROM_WITH_RECIPE;
    }

    /**
     * 映射单表全字段行（含 JSON 原文）。
     *
     * @param rs 结果集
     * @return 任务对象
     * @throws SQLException 读取失败
     */
    private static AnalysisTask mapFullRow(ResultSet rs) throws SQLException {
        AnalysisTask task = new AnalysisTask();
        fillBase(rs, task);
        task.setConstraintJson(rs.getString("constraint_json"));
        task.setBaselineSnapshotJson(rs.getString("baseline_snapshot_json"));
        task.setTargetSnapshotJson(rs.getString("target_snapshot_json"));
        return task;
    }

    /**
     * 映射详情行（全字段 + 冗余展示字段）。
     *
     * @param rs 结果集
     * @return 任务对象
     * @throws SQLException 读取失败
     */
    private static AnalysisTask mapDetailRow(ResultSet rs) throws SQLException {
        AnalysisTask task = mapFullRow(rs);
        task.setUsername(rs.getString("username"));
        fillListColumns(rs, task);
        return task;
    }

    /**
     * 映射列表行（不含 JSON 原文）。
     *
     * @param rs 结果集
     * @return 任务对象
     * @throws SQLException 读取失败
     */
    private static AnalysisTask mapListRow(ResultSet rs) throws SQLException {
        AnalysisTask task = new AnalysisTask();
        fillBase(rs, task);
        fillListColumns(rs, task);
        return task;
    }

    /**
     * 映射任务表本身的公共字段。
     *
     * @param rs   结果集
     * @param task 待填充对象
     * @throws SQLException 读取失败
     */
    private static void fillBase(ResultSet rs, AnalysisTask task) throws SQLException {
        task.setId(JdbcHelper.getLong(rs, "id"));
        task.setUserId(JdbcHelper.getLong(rs, "user_id"));
        task.setRecipeId(JdbcHelper.getLong(rs, "recipe_id"));
        task.setRegionProfileId(JdbcHelper.getLong(rs, "region_profile_id"));
        task.setTaskName(rs.getString("task_name"));
        task.setGoalType(rs.getString("goal_type"));
        task.setTargetRegion(rs.getString("target_region"));
        task.setStatus(rs.getString("status"));
        task.setErrorMessage(rs.getString("error_message"));
        task.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        task.setStartedAt(DateTimeUtil.format(rs.getTimestamp("started_at")));
        task.setFinishedAt(DateTimeUtil.format(rs.getTimestamp("finished_at")));
    }

    /**
     * 映射联表带出的冗余展示字段（配方名称、结果摘要、置信度、是否存在结果）。
     *
     * @param rs   结果集
     * @param task 待填充对象
     * @throws SQLException 读取失败
     */
    private static void fillListColumns(ResultSet rs, AnalysisTask task) throws SQLException {
        task.setRecipeName(rs.getString("recipe_name"));
        task.setSummary(rs.getString("summary"));
        // confidence 为 DECIMAL 列，保持精度直接读取
        task.setConfidence(rs.getBigDecimal("confidence"));
        Integer hasResult = JdbcHelper.getInteger(rs, "has_result");
        task.setHasResult(hasResult != null && hasResult == 1);
    }
}
