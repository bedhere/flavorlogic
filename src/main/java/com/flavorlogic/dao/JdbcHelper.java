package com.flavorlogic.dao;

import com.flavorlogic.util.BizException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC 通用访问助手：统一参数绑定、结果映射与资源释放。
 *
 * <p>DAO 层只通过本类执行参数化 SQL（{@link PreparedStatement}），
 * 从机制上避免 SQL 注入。连接由 Service 层负责创建、提交与关闭，
 * 因此这里的方法都不关闭传入的 {@link Connection}。</p>
 */
public final class JdbcHelper {

    /**
     * 行映射接口。
     *
     * @param <T> 映射结果类型
     */
    @FunctionalInterface
    public interface RowMapper<T> {
        /**
         * 将当前行映射为对象。
         *
         * @param rs 结果集，游标已定位到当前行
         * @return 映射结果
         * @throws SQLException 读取失败
         */
        T map(ResultSet rs) throws SQLException;
    }

    private JdbcHelper() {
    }

    /**
     * 查询列表。
     *
     * @param conn   数据库连接（由调用方管理）
     * @param sql    SQL 语句
     * @param mapper 行映射器
     * @param params 参数
     * @param <T>    结果类型
     * @return 结果列表
     */
    public static <T> List<T> queryList(Connection conn, String sql, RowMapper<T> mapper, Object... params) {
        List<T> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapper.map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new BizException(500, "数据库查询失败：" + e.getMessage());
        }
    }

    /**
     * 查询单条记录。
     *
     * @param conn   数据库连接
     * @param sql    SQL 语句
     * @param mapper 行映射器
     * @param params 参数
     * @param <T>    结果类型
     * @return 单条结果，无数据返回 null
     */
    public static <T> T queryOne(Connection conn, String sql, RowMapper<T> mapper, Object... params) {
        List<T> list = queryList(conn, sql, mapper, params);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 查询单个长整数（COUNT、MAX 等聚合）。
     *
     * @param conn   数据库连接
     * @param sql    SQL 语句
     * @param params 参数
     * @return 聚合结果，无数据返回 0
     */
    public static long queryLong(Connection conn, String sql, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
            }
        } catch (SQLException e) {
            throw new BizException(500, "数据库统计失败：" + e.getMessage());
        }
    }

    /**
     * 查询单行多列，返回第一行。
     *
     * @param conn   数据库连接
     * @param sql    SQL 语句
     * @param mapper 行映射器
     * @param params 参数
     * @param <T>    结果类型
     * @return 结果或 null
     */
    public static <T> T querySingle(Connection conn, String sql, RowMapper<T> mapper, Object... params) {
        return queryOne(conn, sql, mapper, params);
    }

    /**
     * 执行增删改。
     *
     * @param conn   数据库连接
     * @param sql    SQL 语句
     * @param params 参数
     * @return 影响行数
     */
    public static int update(Connection conn, String sql, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new BizException(500, "数据库写入失败：" + e.getMessage());
        }
    }

    /**
     * 执行插入并返回自增主键。
     *
     * @param conn   数据库连接
     * @param sql    SQL 语句
     * @param params 参数
     * @return 自增主键
     */
    public static long insertAndReturnKey(Connection conn, String sql, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new BizException(500, "数据库写入失败：未影响任何记录");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new BizException(500, "数据库写入失败：未返回主键");
        } catch (SQLException e) {
            throw new BizException(500, "数据库写入失败：" + e.getMessage());
        }
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            int index = i + 1;
            if (value == null) {
                ps.setObject(index, null);
            } else if (value instanceof BigDecimal) {
                ps.setBigDecimal(index, (BigDecimal) value);
            } else if (value instanceof Integer) {
                ps.setInt(index, (Integer) value);
            } else if (value instanceof Long) {
                ps.setLong(index, (Long) value);
            } else if (value instanceof Boolean) {
                ps.setBoolean(index, (Boolean) value);
            } else if (value instanceof Timestamp) {
                ps.setTimestamp(index, (Timestamp) value);
            } else {
                ps.setString(index, String.valueOf(value));
            }
        }
    }

    /**
     * 读取可空的 Long 列。
     *
     * @param rs     结果集
     * @param column 列名
     * @return 值或 null
     * @throws SQLException 读取失败
     */
    public static Long getLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * 读取可空的 Integer 列。
     *
     * @param rs     结果集
     * @param column 列名
     * @return 值或 null
     * @throws SQLException 读取失败
     */
    public static Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * 读取可空的 BigDecimal 列。
     *
     * @param rs     结果集
     * @param column 列名
     * @return 值或 null
     * @throws SQLException 读取失败
     */
    public static BigDecimal getBigDecimal(ResultSet rs, String column) throws SQLException {
        return rs.getBigDecimal(column);
    }

    /**
     * 读取可空字符串列。
     *
     * @param rs     结果集
     * @param column 列名
     * @return 值或 null
     * @throws SQLException 读取失败
     */
    public static String getString(ResultSet rs, String column) throws SQLException {
        return rs.getString(column);
    }
}
