package com.flavorlogic.dao;

import com.flavorlogic.model.AnalysisResult;

import java.sql.Connection;

/**
 * 风味分析结果数据访问接口（表 {@code analysis_result}）。
 */
public interface AnalysisResultDao {

    /**
     * 新增分析结果。
     *
     * @param conn   数据库连接
     * @param result 结果（JSON 字段为原文）
     * @return 新结果编号
     */
    long insert(Connection conn, AnalysisResult result);

    /**
     * 按任务编号查询结果。
     *
     * @param conn   数据库连接
     * @param taskId 任务编号
     * @return 结果或 null
     */
    AnalysisResult findByTaskId(Connection conn, Long taskId);
}
