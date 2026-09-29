package com.flavorlogic.service;

import com.flavorlogic.model.User;
import com.flavorlogic.util.PageResult;

/**
 * 用户与权限业务接口。
 */
public interface UserService {

    /**
     * 注册普通用户。
     *
     * @param username        登录名
     * @param nickname        昵称
     * @param password        密码
     * @param confirmPassword 确认密码
     * @return 注册后的用户
     */
    User register(String username, String nickname, String password, String confirmPassword);

    /**
     * 登录校验。
     *
     * @param username 登录名
     * @param password 密码
     * @return 登录成功的用户
     */
    User login(String username, String password);

    /**
     * 按编号查询用户。
     *
     * @param id 用户编号
     * @return 用户，不存在返回 null
     */
    User findById(Long id);

    /**
     * 更新昵称。
     *
     * @param userId   用户编号
     * @param nickname 新昵称
     * @return 更新后的用户
     */
    User updateProfile(Long userId, String nickname);

    /**
     * 修改密码。
     *
     * @param userId          用户编号
     * @param oldPassword     原密码
     * @param newPassword     新密码
     * @param confirmPassword 确认新密码
     */
    void changePassword(Long userId, String oldPassword, String newPassword, String confirmPassword);

    /**
     * 记录登录时间。
     *
     * @param userId 用户编号
     */
    void recordLogin(Long userId);

    /**
     * 管理端分页查询用户。
     *
     * @param keyword  用户名或昵称关键字
     * @param role     角色筛选
     * @param status   状态筛选
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<User> adminPage(String keyword, String role, Integer status, int page, int pageSize);

    /**
     * 管理端启用/禁用账号。
     *
     * @param operatorId 操作者编号
     * @param userId     目标用户编号
     * @param status     1 启用，0 禁用
     */
    void adminUpdateStatus(Long operatorId, Long userId, int status);

    /**
     * 管理端调整角色。
     *
     * @param operatorId 操作者编号
     * @param userId     目标用户编号
     * @param role       USER / ADMIN
     */
    void adminUpdateRole(Long operatorId, Long userId, String role);

    /**
     * 用户总数。
     *
     * @return 用户总数
     */
    long countAll();
}
