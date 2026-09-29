package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.AnalysisResultDao;
import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.model.AnalysisResult;
import com.flavorlogic.util.DateTimeUtil;
import com.flavorlogic.util.TextUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 风味分析结果数据访问实现（表 {@code analysis_result}）。
 *
 * <p>四个 JSON 列（baseline / target / delta / suggestion）直接以实体中的 JSON 原文
 * 作为字符串参数绑定；连接与事务由 Service 层管理，本类不关闭、不提交。</p>
 */
public class AnalysisResultDaoImpl implements AnalysisResultDao {

    /** 结果表全部列 */
    private static final String COLUMNS =
            "id, task_id, baseline_json, target_json, delta_json, suggestion_json, "
                    + "confidence, data_completeness, summary, explanation, engine_version, created_at";

    @Override
    public long insert(Connection conn, AnalysisResult result) {
        String sql = "INSERT INTO analysis_result (task_id, baseline_json, target_json, delta_json, "
                + "suggestion_json, confidence, data_completeness, summary, explanation, engine_version) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        // 非空列兜底：引擎版本与两项 DECIMAL 指标默认取模型层常量/零值，避免显式写入 NULL 报错
        String engineVersion = TextUtil.defaultIfBlank(result.getEngineVersion(), AnalysisResult.ENGINE_VERSION);
        BigDecimal confidence = result.getConfidence() == null ? BigDecimal.ZERO : result.getConfidence();
        BigDecimal completeness =
                result.getDataCompleteness() == null ? BigDecimal.ZERO : result.getDataCompleteness();
        return JdbcHelper.insertAndReturnKey(conn, sql,
                result.getTaskId(),
                result.getBaselineJson(),
                result.getTargetJson(),
                result.getDeltaJson(),
                result.getSuggestionJson(),
                confidence,
                completeness,
                result.getSummary(),
                result.getExplanation(),
                engineVersion);
    }

    @Override
    public AnalysisResult findByTaskId(Connection conn, Long taskId) {
        // task_id 上有唯一键 uk_analysis_result_task，最多命中一行
        String sql = "SELECT " + COLUMNS + " FROM analysis_result WHERE task_id = ?";
        return JdbcHelper.queryOne(conn, sql, AnalysisResultDaoImpl::mapRow, taskId);
    }

    /**
     * 映射结果行，含四个 JSON 原文。
     *
     * @param rs 结果集
     * @return 结果对象
     * @throws SQLException 读取失败
     */
    private static AnalysisResult mapRow(ResultSet rs) throws SQLException {
        AnalysisResult result = new AnalysisResult();
        result.setId(JdbcHelper.getLong(rs, "id"));
        result.setTaskId(JdbcHelper.getLong(rs, "task_id"));
        // confidence、data_completeness 为 DECIMAL 列，保持精度直接读取
        result.setConfidence(rs.getBigDecimal("confidence"));
        result.setDataCompleteness(rs.getBigDecimal("data_completeness"));
        result.setSummary(rs.getString("summary"));
        result.setExplanation(rs.getString("explanation"));
        result.setEngineVersion(rs.getString("engine_version"));
        result.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        result.setBaselineJson(rs.getString("baseline_json"));
        result.setTargetJson(rs.getString("target_json"));
        result.setDeltaJson(rs.getString("delta_json"));
        result.setSuggestionJson(rs.getString("suggestion_json"));
        return result;
    }
}
