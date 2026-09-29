package com.flavorlogic.dao;

import com.flavorlogic.model.User;

import java.sql.Connection;
import java.util.List;

/**
 * 用户数据访问接口（表 {@code sys_user}）。
 */
public interface UserDao {

    /**
     * 按登录名查询用户。
     *
     * @param conn     数据库连接
     * @param username 登录名
     * @return 用户或 null
     */
    User findByUsername(Connection conn, String username);

    /**
     * 按主键查询用户。
     *
     * @param conn 数据库连接
     * @param id   用户编号
     * @return 用户或 null
     */
    User findById(Connection conn, Long id);

    /**
     * 新增用户。
     *
     * @param conn 数据库连接
     * @param user 用户（密码需为哈希值）
     * @return 新用户编号
     */
    long insert(Connection conn, User user);

    /**
     * 更新最近登录时间。
     *
     * @param conn 数据库连接
     * @param id   用户编号
     * @return 影响行数
     */
    int updateLastLogin(Connection conn, Long id);

    /**
     * 更新昵称。
     *
     * @param conn     数据库连接
     * @param id       用户编号
     * @param nickname 昵称
     * @return 影响行数
     */
    int updateProfile(Connection conn, Long id, String nickname);

    /**
     * 更新密码哈希。
     *
     * @param conn         数据库连接
     * @param id           用户编号
     * @param passwordHash 新密码哈希
     * @return 影响行数
     */
    int updatePassword(Connection conn, Long id, String passwordHash);

    /**
     * 更新账号状态。
     *
     * @param conn   数据库连接
     * @param id     用户编号
     * @param status 1 正常，0 禁用
     * @return 影响行数
     */
    int updateStatus(Connection conn, Long id, int status);

    /**
     * 更新角色。
     *
     * @param conn 数据库连接
     * @param id   用户编号
     * @param role USER / ADMIN
     * @return 影响行数
     */
    int updateRole(Connection conn, Long id, String role);

    /**
     * 分页查询用户。
     *
     * @param conn    数据库连接
     * @param keyword 用户名或昵称关键字，可为空
     * @param role    角色筛选，可为空
     * @param status  状态筛选，可为空
     * @param offset  偏移量
     * @param limit   每页条数
     * @return 用户列表
     */
    List<User> page(Connection conn, String keyword, String role, Integer status, int offset, int limit);

    /**
     * 统计满足条件的用户数。
     *
     * @param conn    数据库连接
     * @param keyword 关键字，可为空
     * @param role    角色筛选，可为空
     * @param status  状态筛选，可为空
     * @return 记录数
     */
    long count(Connection conn, String keyword, String role, Integer status);

    /**
     * 统计用户总数。
     *
     * @param conn 数据库连接
     * @return 用户总数
     */
    long countAll(Connection conn);
}
