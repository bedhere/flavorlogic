package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.dao.UserDao;
import com.flavorlogic.model.User;
import com.flavorlogic.util.DateTimeUtil;
import com.flavorlogic.util.TextUtil;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户数据访问实现（表 {@code sys_user}）。
 *
 * <p>本类只负责持久化：不判断用户名是否重复、不校验密码、不做权限判断，
 * 这些规则属于 Service 层。</p>
 *
 * <p>所有 SQL 均通过 {@link JdbcHelper} 以 {@code ?} 参数化执行，用户输入
 * 永不拼接进 SQL 文本；传入的 {@link Connection} 由 Service 层负责关闭与事务，
 * 本类不调用 close / commit / rollback / setAutoCommit。</p>
 */
public class UserDaoImpl implements UserDao {

    /** 列表与详情共用的列清单 */
    private static final String COLUMNS =
            "u.id, u.username, u.password_hash, u.nickname, u.role, u.status, "
                    + "u.last_login_at, u.created_at, u.updated_at";

    /** 基础查询语句 */
    private static final String SELECT_BASE = "SELECT " + COLUMNS + " FROM sys_user u";

    /** 行映射：sys_user 一行 → User */
    private static final JdbcHelper.RowMapper<User> ROW_MAPPER = rs -> {
        User user = new User();
        user.setId(JdbcHelper.getLong(rs, "id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setNickname(rs.getString("nickname"));
        user.setRole(rs.getString("role"));
        user.setStatus(JdbcHelper.getInteger(rs, "status"));
        user.setLastLoginAt(DateTimeUtil.format(rs.getTimestamp("last_login_at")));
        user.setCreatedAt(DateTimeUtil.format(rs.getTimestamp("created_at")));
        user.setUpdatedAt(DateTimeUtil.format(rs.getTimestamp("updated_at")));
        return user;
    };

    /**
     * 按登录名查询用户。
     *
     * @param conn     数据库连接
     * @param username 登录名
     * @return 用户或 null
     */
    @Override
    public User findByUsername(Connection conn, String username) {
        String sql = SELECT_BASE + " WHERE u.username = ? LIMIT 1";
        return JdbcHelper.queryOne(conn, sql, ROW_MAPPER, username);
    }

    /**
     * 按主键查询用户。
     *
     * @param conn 数据库连接
     * @param id   用户编号
     * @return 用户或 null
     */
    @Override
    public User findById(Connection conn, Long id) {
        String sql = SELECT_BASE + " WHERE u.id = ?";
        return JdbcHelper.queryOne(conn, sql, ROW_MAPPER, id);
    }

    /**
     * 新增用户，其余列（last_login_at 留空、created_at / updated_at）交给数据库默认值。
     *
     * @param conn 数据库连接
     * @param user 用户（密码需为哈希值）
     * @return 新用户编号
     */
    @Override
    public long insert(Connection conn, User user) {
        String sql = "INSERT INTO sys_user (username, password_hash, nickname, role, status) "
                + "VALUES (?, ?, ?, ?, ?)";
        // role 与 status 列 NOT NULL，未赋值时按建表默认值（USER / 1）写入
        String role = TextUtil.defaultIfBlank(user.getRole(), User.ROLE_USER);
        Integer status = user.getStatus() == null ? Integer.valueOf(1) : user.getStatus();
        return JdbcHelper.insertAndReturnKey(conn, sql,
                user.getUsername(), user.getPasswordHash(), user.getNickname(), role, status);
    }

    /**
     * 更新最近登录时间。
     *
     * @param conn 数据库连接
     * @param id   用户编号
     * @return 影响行数
     */
    @Override
    public int updateLastLogin(Connection conn, Long id) {
        String sql = "UPDATE sys_user SET last_login_at = NOW() WHERE id = ?";
        return JdbcHelper.update(conn, sql, id);
    }

    /**
     * 更新昵称。
     *
     * @param conn     数据库连接
     * @param id       用户编号
     * @param nickname 昵称
     * @return 影响行数
     */
    @Override
    public int updateProfile(Connection conn, Long id, String nickname) {
        String sql = "UPDATE sys_user SET nickname = ? WHERE id = ?";
        return JdbcHelper.update(conn, sql, nickname, id);
    }

    /**
     * 更新密码哈希。
     *
     * @param conn         数据库连接
     * @param id           用户编号
     * @param passwordHash 新密码哈希
     * @return 影响行数
     */
    @Override
    public int updatePassword(Connection conn, Long id, String passwordHash) {
        String sql = "UPDATE sys_user SET password_hash = ? WHERE id = ?";
        return JdbcHelper.update(conn, sql, passwordHash, id);
    }

    /**
     * 更新账号状态。
     *
     * @param conn   数据库连接
     * @param id     用户编号
     * @param status 1 正常，0 禁用
     * @return 影响行数
     */
    @Override
    public int updateStatus(Connection conn, Long id, int status) {
        String sql = "UPDATE sys_user SET status = ? WHERE id = ?";
        return JdbcHelper.update(conn, sql, status, id);
    }

    /**
     * 更新角色。
     *
     * @param conn 数据库连接
     * @param id   用户编号
     * @param role USER / ADMIN
     * @return 影响行数
     */
    @Override
    public int updateRole(Connection conn, Long id, String role) {
        String sql = "UPDATE sys_user SET role = ? WHERE id = ?";
        return JdbcHelper.update(conn, sql, role, id);
    }

    /**
     * 分页查询用户，按编号倒序（新用户在前）。
     *
     * @param conn    数据库连接
     * @param keyword 用户名或昵称关键字，可为空
     * @param role    角色筛选，可为空
     * @param status  状态筛选，可为空
     * @param offset  偏移量
     * @param limit   每页条数
     * @return 用户列表
     */
    @Override
    public List<User> page(Connection conn, String keyword, String role, Integer status, int offset, int limit) {
        List<Object> params = new ArrayList<>();
        String sql = SELECT_BASE + buildWhere(keyword, role, status, params)
                + " ORDER BY u.id DESC LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        return JdbcHelper.queryList(conn, sql, ROW_MAPPER, params.toArray());
    }

    /**
     * 统计满足条件的用户数，筛选条件与 {@link #page} 完全一致。
     *
     * @param conn    数据库连接
     * @param keyword 关键字，可为空
     * @param role    角色筛选，可为空
     * @param status  状态筛选，可为空
     * @return 记录数
     */
    @Override
    public long count(Connection conn, String keyword, String role, Integer status) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM sys_user u" + buildWhere(keyword, role, status, params);
        return JdbcHelper.queryLong(conn, sql, params.toArray());
    }

    /**
     * 统计用户总数。
     *
     * @param conn 数据库连接
     * @return 用户总数
     */
    @Override
    public long countAll(Connection conn) {
        String sql = "SELECT COUNT(*) FROM sys_user";
        return JdbcHelper.queryLong(conn, sql);
    }

    /**
     * 拼接可选筛选条件（条件值为 {@code ?} 参数绑定，关键字统一走 {@link TextUtil#likePattern}）。
     *
     * @param keyword 用户名或昵称关键字，为空时不追加条件
     * @param role    角色，为空时不追加条件
     * @param status  状态，为 null 时不追加条件
     * @param params  参数收集器，按拼接顺序写入绑定值
     * @return WHERE 子句（始终以 {@code WHERE 1 = 1} 开头，便于追加 AND）
     */
    private static String buildWhere(String keyword, String role, Integer status, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (!TextUtil.isBlank(keyword)) {
            where.append(" AND (u.username LIKE ? OR u.nickname LIKE ?)");
            String pattern = TextUtil.likePattern(keyword);
            params.add(pattern);
            params.add(pattern);
        }
        if (!TextUtil.isBlank(role)) {
            where.append(" AND u.role = ?");
            params.add(role);
        }
        if (status != null) {
            where.append(" AND u.status = ?");
            params.add(status);
        }
        return where.toString();
    }
}
