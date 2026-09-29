package com.flavorlogic.util;

import com.flavorlogic.model.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Session 工具：统一登录态的写入、读取与销毁。
 *
 * <p>安全约定：登录成功后销毁旧 Session 并重新生成会话标识（防会话固定攻击）；
 * Session 中只保存编号、登录名、昵称与角色，不保存密码或密码哈希。</p>
 */
public final class SessionUtil {

    /** Session 属性：用户编号 */
    public static final String USER_ID = "flUserId";
    /** Session 属性：登录名 */
    public static final String USERNAME = "flUsername";
    /** Session 属性：昵称 */
    public static final String NICKNAME = "flNickname";
    /** Session 属性：角色 */
    public static final String ROLE = "flRole";

    private SessionUtil() {
    }

    /**
     * 写入登录态（会重建 Session）。
     *
     * @param request HTTP 请求
     * @param user    登录成功的用户
     */
    public static void login(HttpServletRequest request, User user) {
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(USER_ID, user.getId());
        session.setAttribute(USERNAME, user.getUsername());
        session.setAttribute(NICKNAME, TextUtil.defaultIfBlank(user.getNickname(), user.getUsername()));
        session.setAttribute(ROLE, TextUtil.defaultIfBlank(user.getRole(), User.ROLE_USER));
    }

    /**
     * 销毁登录态。
     *
     * @param request HTTP 请求
     */
    public static void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    /**
     * 从 Session 还原当前用户（不含密码哈希）。
     *
     * @param request HTTP 请求
     * @return 当前用户，未登录返回 null
     */
    public static User currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object userId = session.getAttribute(USER_ID);
        if (userId == null) {
            return null;
        }
        User user = new User();
        user.setId(userId instanceof Long ? (Long) userId : Long.valueOf(String.valueOf(userId)));
        user.setUsername((String) session.getAttribute(USERNAME));
        user.setNickname((String) session.getAttribute(NICKNAME));
        user.setRole((String) session.getAttribute(ROLE));
        user.setStatus(1);
        return user;
    }

    /**
     * 当前用户编号。
     *
     * @param request HTTP 请求
     * @return 用户编号，未登录返回 null
     */
    public static Long currentUserId(HttpServletRequest request) {
        User user = currentUser(request);
        return user == null ? null : user.getId();
    }

    /**
     * 是否已登录。
     *
     * @param request HTTP 请求
     * @return 是否已登录
     */
    public static boolean isLoggedIn(HttpServletRequest request) {
        return currentUser(request) != null;
    }

    /**
     * 当前用户是否为管理员。
     *
     * @param request HTTP 请求
     * @return 是否管理员
     */
    public static boolean isAdmin(HttpServletRequest request) {
        User user = currentUser(request);
        return user != null && user.isAdmin();
    }

    /**
     * 当前登录名（用于操作日志）。
     *
     * @param request HTTP 请求
     * @return 登录名，未登录返回 null
     */
    public static String currentUsername(HttpServletRequest request) {
        User user = currentUser(request);
        return user == null ? null : user.getUsername();
    }
}
