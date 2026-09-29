package com.flavorlogic.service;

import com.flavorlogic.util.BizException;
import com.flavorlogic.util.DBUtil;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Service 基类：统一连接获取、事务提交与回滚、资源释放。
 *
 * <p>只读操作走 {@link #read(SqlFunction)}；写入操作走 {@link #write(SqlFunction)} 或
 * {@link #writeVoid(SqlConsumer)}，后者在一组 DAO 调用全部成功后才提交，
 * 任一步抛出异常都会整体回滚，保证“配方 + 明细”“任务 + 结果”这类组合写入的一致性。</p>
 */
public abstract class BaseService {

    /**
     * 只读事务函数。
     *
     * @param <T> 返回类型
     */
    @FunctionalInterface
    public interface SqlFunction<T> {
        /**
         * 在给定连接上执行。
         *
         * @param conn 数据库连接
         * @return 执行结果
         */
        T apply(Connection conn);
    }

    /**
     * 写入事务函数。
     */
    @FunctionalInterface
    public interface SqlConsumer {
        /**
         * 在给定连接上执行。
         *
         * @param conn 数据库连接
         */
        void accept(Connection conn);
    }

    /**
     * 执行只读操作。
     *
     * @param action 只读逻辑
     * @param <T>    返回类型
     * @return 执行结果
     */
    protected <T> T read(SqlFunction<T> action) {
        try (Connection conn = DBUtil.getConnection()) {
            return action.apply(conn);
        } catch (SQLException e) {
            throw new BizException(500, "数据库连接失败：" + e.getMessage());
        }
    }

    /**
     * 执行写入操作（自动提交或回滚）。
     *
     * @param action 写入逻辑
     * @param <T>    返回类型
     * @return 执行结果
     */
    protected <T> T write(SqlFunction<T> action) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);
            T result = action.apply(conn);
            conn.commit();
            return result;
        } catch (SQLException e) {
            DBUtil.rollbackQuietly(conn);
            throw new BizException(500, "数据库操作失败：" + e.getMessage());
        } catch (RuntimeException e) {
            DBUtil.rollbackQuietly(conn);
            throw e;
        } finally {
            DBUtil.close(conn);
        }
    }

    /**
     * 执行写入操作（无返回值）。
     *
     * @param action 写入逻辑
     */
    protected void writeVoid(SqlConsumer action) {
        write(conn -> {
            action.accept(conn);
            return null;
        });
    }
}
