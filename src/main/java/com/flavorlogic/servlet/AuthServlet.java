package com.flavorlogic.servlet;

import com.flavorlogic.model.User;
import com.flavorlogic.service.Services;
import com.flavorlogic.util.ApiResponse;
import com.flavorlogic.util.BizException;
import com.flavorlogic.util.SessionUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口：{@code /api/auth/*}。
 *
 * <ul>
 *   <li>POST /api/auth/register —— 注册账号（不建立会话，需另行登录）</li>
 *   <li>POST /api/auth/login —— 登录并创建 Session</li>
 *   <li>POST /api/auth/logout —— 注销并销毁 Session</li>
 *   <li>GET  /api/auth/me —— 获取当前登录用户</li>
 *   <li>POST /api/auth/password —— 修改密码</li>
 * </ul>
 */
@WebServlet("/api/auth/*")
public class AuthServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /** 注册表单 */
    public static class RegisterForm {
        private String username;
        private String nickname;
        private String password;
        private String confirmPassword;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }

    /** 登录表单 */
    public static class LoginForm {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    /** 修改密码表单 */
    public static class PasswordForm {
        private String oldPassword;
        private String newPassword;
        private String confirmPassword;

        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        String action = pathSegment(request, 0);
        if ("me".equals(action)) {
            handle(request, response, () -> {
                User user = currentUser(request);
                if (user == null) {
                    throw BizException.unauthorized("尚未登录");
                }
                // 与登录/注册返回同一结构（含 isAdmin），便于前端统一判断
                return buildUserView(user);
            });
            return;
        }
        methodNotAllowed(response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        String action = pathSegment(request, 0);
        if (action == null) {
            methodNotAllowed(response);
            return;
        }
        switch (action) {
            case "register":
                handle(request, response, () -> {
                    RegisterForm form = readBody(request, RegisterForm.class);
                    User user = Services.user().register(form.getUsername(), form.getNickname(),
                            form.getPassword(), form.getConfirmPassword());
                    // 注册只创建账号，不建立会话：用户需要回到登录页手动登录
                    return ApiResponse.ok("注册成功，请使用新账号登录", buildUserView(user));
                });
                break;
            case "login":
                handle(request, response, () -> {
                    LoginForm form = readBody(request, LoginForm.class);
                    User user = Services.user().login(form.getUsername(), form.getPassword());
                    SessionUtil.login(request, user);
                    Services.user().recordLogin(user.getId());
                    return buildUserView(user);
                });
                break;
            case "logout":
                handle(request, response, () -> {
                    SessionUtil.logout(request);
                    return null;
                });
                break;
            case "password":
                handle(request, response, () -> {
                    Long userId = requireUserId(request);
                    PasswordForm form = readBody(request, PasswordForm.class);
                    Services.user().changePassword(userId, form.getOldPassword(),
                            form.getNewPassword(), form.getConfirmPassword());
                    return null;
                });
                break;
            default:
                methodNotAllowed(response);
        }
    }

    /**
     * 组装返回给前端的用户信息（不含密码哈希）。
     *
     * @param user 用户
     * @return 用户视图
     */
    private Map<String, Object> buildUserView(User user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put("username", user.getUsername());
        view.put("nickname", user.getNickname());
        view.put("role", user.getRole());
        view.put("isAdmin", user.isAdmin());
        return view;
    }
}
