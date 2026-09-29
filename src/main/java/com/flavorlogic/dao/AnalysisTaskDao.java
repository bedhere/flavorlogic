package com.flavorlogic.dao;

import com.flavorlogic.model.AnalysisTask;

import java.sql.Connection;
import java.util.List;

/**
 * 风味分析任务数据访问接口（表 {@code analysis_task}）。
 */
public interface AnalysisTaskDao {

    /**
     * 新增分析任务。
     *
     * @param conn 数据库连接
     * @param task 任务（含 JSON 快照原文）
     * @return 新任务编号
     */
    long insert(Connection conn, AnalysisTask task);

    /**
     * 按主键查询任务（不含结果）。
     *
     * @param conn 数据库连接
     * @param id   任务编号
     * @return 任务或 null
     */
    AnalysisTask findById(Connection conn, Long id);

    /**
     * 按主键查询任务详情：联表带出用户昵称、配方名称与结果摘要。
     *
     * @param conn 数据库连接
     * @param id   任务编号
     * @return 任务或 null
     */
    AnalysisTask findDetailById(Connection conn, Long id);

    /**
     * 标记任务开始运行。
     *
     * @param conn 数据库连接
     * @param id   任务编号
     * @return 影响行数
     */
    int markRunning(Connection conn, Long id);

    /**
     * 标记任务完成。
     *
     * @param conn 数据库连接
     * @param id   任务编号
     * @return 影响行数
     */
    int markCompleted(Connection conn, Long id);

    /**
     * 标记任务失败。
     *
     * @param conn         数据库连接
     * @param id           任务编号
     * @param errorMessage 失败原因
     * @return 影响行数
     */
    int markFailed(Connection conn, Long id, String errorMessage);

    /**
     * 删除任务（结果与反馈由外键级联删除）。
     *
     * @param conn 数据库连接
     * @param id   任务编号
     * @return 影响行数
     */
    int delete(Connection conn, Long id);

    /**
     * 分页查询某用户的分析任务。
     *
     * @param conn     数据库连接
     * @param userId   用户编号
     * @param keyword  任务名称或配方名称关键字，可为空
     * @param goalType 目标类型筛选，可为空
     * @param status   状态筛选，可为空
     * @param offset   偏移量
     * @param limit    每页条数
     * @return 任务列表（带出结果摘要与置信度）
     */
    List<AnalysisTask> pageByUser(Connection conn, Long userId, String keyword, String goalType, String status,
                                  int offset, int limit);

    /**
     * 统计某用户满足条件的任务数。
     *
     * @param conn     数据库连接
     * @param userId   用户编号
     * @param keyword  关键字，可为空
     * @param goalType 目标类型筛选，可为空
     * @param status   状态筛选，可为空
     * @return 记录数
     */
    long countByUser(Connection conn, Long userId, String keyword, String goalType, String status);

    /**
     * 查询用户最近的分析任务。
     *
     * @param conn   数据库连接
     * @param userId 用户编号
     * @param limit  条数
     * @return 任务列表
     */
    List<AnalysisTask> listRecentByUser(Connection conn, Long userId, int limit);

    /**
     * 统计用户任务总数。
     *
     * @param conn   数据库连接
     * @param userId 用户编号
     * @return 任务总数
     */
    long countByUser(Connection conn, Long userId);

    /**
     * 统计用户已完成任务数。
     *
     * @param conn   数据库连接
     * @param userId 用户编号
     * @return 已完成任务数
     */
    long countCompletedByUser(Connection conn, Long userId);

    /**
     * 用户最近一次分析完成时间。
     *
     * @param conn   数据库连接
     * @param userId 用户编号
     * @return 时间字符串，无记录返回 null
     */
    String findLastFinishedAt(Connection conn, Long userId);

    /**
     * 管理端分页查询全部任务。
     *
     * @param conn     数据库连接
     * @param keyword  关键字，可为空
     * @param goalType 目标类型筛选，可为空
     * @param status   状态筛选，可为空
     * @param offset   偏移量
     * @param limit    每页条数
     * @return 任务列表
     */
    List<AnalysisTask> pageAll(Connection conn, String keyword, String goalType, String status, int offset, int limit);

    /**
     * 管理端统计任务数。
     *
     * @param conn     数据库连接
     * @param keyword  关键字，可为空
     * @param goalType 目标类型筛选，可为空
     * @param status   状态筛选，可为空
     * @return 记录数
     */
    long countAll(Connection conn, String keyword, String goalType, String status);

    /**
     * 统计平台任务总数。
     *
     * @param conn 数据库连接
     * @return 任务总数
     */
    long countAll(Connection conn);
}
