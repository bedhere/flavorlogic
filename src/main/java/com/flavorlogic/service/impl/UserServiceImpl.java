package com.flavorlogic.service.impl;

import com.flavorlogic.dao.UserDao;
import com.flavorlogic.dao.impl.UserDaoImpl;
import com.flavorlogic.model.User;
import com.flavorlogic.service.BaseService;
import com.flavorlogic.service.UserService;
import com.flavorlogic.util.BizException;
import com.flavorlogic.util.PageResult;
import com.flavorlogic.util.PasswordUtil;
import com.flavorlogic.util.TextUtil;
import com.flavorlogic.util.ValidationUtil;

import java.util.List;

/**
 * 用户与权限业务实现。
 */
public class UserServiceImpl extends BaseService implements UserService {

    private final UserDao userDao = new UserDaoImpl();

    @Override
    public User register(String username, String nickname, String password, String confirmPassword) {
        String name = ValidationUtil.requireUsername(username);
        String nick = ValidationUtil.requireText(nickname, "昵称", 50);
        ValidationUtil.requirePassword(password);
        if (!password.equals(confirmPassword)) {
            throw BizException.badRequest("两次输入的密码不一致");
        }
        return write(conn -> {
            if (userDao.findByUsername(conn, name) != null) {
                throw BizException.conflict("用户名「" + name + "」已被注册");
            }
            User user = new User();
            user.setUsername(name);
            user.setNickname(nick);
            user.setRole(User.ROLE_USER);
            user.setStatus(1);
            user.setPasswordHash(PasswordUtil.hash(password));
            user.setId(userDao.insert(conn, user));
            return user;
        });
    }

    @Override
    public User login(String username, String password) {
        String name = ValidationUtil.requireText(username, "用户名", 50);
        if (TextUtil.isBlank(password)) {
            throw BizException.badRequest("密码不能为空");
        }
        User user = read(conn -> userDao.findByUsername(conn, name));
        if (user == null || !PasswordUtil.verify(password, user.getPasswordHash())) {
            throw BizException.badRequest("用户名或密码错误");
        }
        if (!user.isEnabled()) {
            throw BizException.forbidden("账号已被禁用，请联系管理员");
        }
        return user;
    }

    @Override
    public User findById(Long id) {
        if (id == null) {
            return null;
        }
        return read(conn -> userDao.findById(conn, id));
    }

    @Override
    public User updateProfile(Long userId, String nickname) {
        String nick = ValidationUtil.requireText(nickname, "昵称", 50);
        return write(conn -> {
            User user = userDao.findById(conn, userId);
            if (user == null) {
                throw BizException.notFound("用户不存在");
            }
            userDao.updateProfile(conn, userId, nick);
            user.setNickname(nick);
            return user;
        });
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword, String confirmPassword) {
        if (TextUtil.isBlank(oldPassword)) {
            throw BizException.badRequest("原密码不能为空");
        }
        ValidationUtil.requirePassword(newPassword);
        if (!newPassword.equals(confirmPassword)) {
            throw BizException.badRequest("两次输入的新密码不一致");
        }
        if (oldPassword.equals(newPassword)) {
            throw BizException.badRequest("新密码不能与原密码相同");
        }
        writeVoid(conn -> {
            User user = userDao.findById(conn, userId);
            if (user == null) {
                throw BizException.notFound("用户不存在");
            }
            if (!PasswordUtil.verify(oldPassword, user.getPasswordHash())) {
                throw BizException.badRequest("原密码不正确");
            }
            userDao.updatePassword(conn, userId, PasswordUtil.hash(newPassword));
        });
    }

    @Override
    public void recordLogin(Long userId) {
        writeVoid(conn -> userDao.updateLastLogin(conn, userId));
    }

    @Override
    public PageResult<User> adminPage(String keyword, String role, Integer status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String trimmedKeyword = TextUtil.isBlank(keyword) ? null : keyword.trim();
        String trimmedRole = TextUtil.isBlank(role) ? null : role.trim();
        return read(conn -> {
            long total = userDao.count(conn, trimmedKeyword, trimmedRole, status);
            List<User> list = total == 0
                    ? java.util.Collections.emptyList()
                    : userDao.page(conn, trimmedKeyword, trimmedRole, status, offset, safeSize);
            return new PageResult<>(list, total, safePage, safeSize);
        });
    }

    @Override
    public void adminUpdateStatus(Long operatorId, Long userId, int status) {
        if (status != 0 && status != 1) {
            throw BizException.badRequest("状态取值不合法");
        }
        if (userId == null) {
            throw BizException.badRequest("缺少用户编号");
        }
        if (operatorId != null && operatorId.equals(userId) && status == 0) {
            throw BizException.badRequest("不能禁用当前登录的管理员账号");
        }
        writeVoid(conn -> {
            User target = userDao.findById(conn, userId);
            if (target == null) {
                throw BizException.notFound("用户不存在");
            }
            userDao.updateStatus(conn, userId, status);
        });
    }

    @Override
    public void adminUpdateRole(Long operatorId, Long userId, String role) {
        String target = ValidationUtil.requireOneOf(role, "角色", User.ROLE_USER, User.ROLE_ADMIN);
        if (userId == null) {
            throw BizException.badRequest("缺少用户编号");
        }
        if (operatorId != null && operatorId.equals(userId) && User.ROLE_USER.equals(target)) {
            throw BizException.badRequest("不能取消当前登录管理员的管理权限");
        }
        writeVoid(conn -> {
            User user = userDao.findById(conn, userId);
            if (user == null) {
                throw BizException.notFound("用户不存在");
            }
            userDao.updateRole(conn, userId, target);
        });
    }

    @Override
    public long countAll() {
        return read(userDao::countAll);
    }
}
