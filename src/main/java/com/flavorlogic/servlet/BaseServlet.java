package com.flavorlogic.servlet;

import com.flavorlogic.model.User;
import com.flavorlogic.util.BizException;
import com.flavorlogic.util.JsonUtil;
import com.flavorlogic.util.SessionUtil;
import com.flavorlogic.util.TextUtil;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet 基类：统一参数读取、登录校验、异常转换与 JSON 输出。
 *
 * <p>HTTP 状态码约定：401（未登录）、403（无权限）、404（资源不存在）会直接体现在状态码上，
 * 便于前端做统一跳转；参数错误（400）、数据冲突（409）与服务器错误（500）返回 200 +
 * 业务码非 0，由前端就地提示，避免浏览器弹出错误页。</p>
 */
public abstract class BaseServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    protected static final Logger LOG = Logger.getLogger(BaseServlet.class.getName());

    /**
     * 业务处理函数：返回对象将被包装为 {@code {code:0, data:...}}。
     */
    @FunctionalInterface
    public interface Handler {
        /**
         * 执行业务逻辑。
         *
         * @return 响应数据，可为 null
         */
        Object execute();
    }

    /**
     * 当前登录用户。
     *
     * @param request HTTP 请求
     * @return 用户，未登录返回 null
     */
    protected User currentUser(HttpServletRequest request) {
        return SessionUtil.currentUser(request);
    }

    /**
     * 获取当前用户编号，未登录直接抛 401。
     *
     * @param request HTTP 请求
     * @return 用户编号
     */
    protected Long requireUserId(HttpServletRequest request) {
        Long userId = SessionUtil.currentUserId(request);
        if (userId == null) {
            throw BizException.unauthorized("登录状态已失效，请重新登录");
        }
        return userId;
    }

    /**
     * 获取当前用户，未登录直接抛 401。
     *
     * @param request HTTP 请求
     * @return 用户
     */
    protected User requireUser(HttpServletRequest request) {
        User user = currentUser(request);
        if (user == null) {
            throw BizException.unauthorized("登录状态已失效，请重新登录");
        }
        return user;
    }

    /**
     * 校验管理员身份。
     *
     * @param request HTTP 请求
     * @return 管理员用户
     */
    protected User requireAdmin(HttpServletRequest request) {
        User user = requireUser(request);
        if (!user.isAdmin()) {
            throw BizException.forbidden("该操作需要管理员权限");
        }
        return user;
    }

    /**
     * 读取路径参数：{@code /api/recipes/12} → 12。
     *
     * @param request HTTP 请求
     * @return 编号，无路径参数或非数字返回 null
     */
    protected Long pathId(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (TextUtil.isBlank(pathInfo) || "/".equals(pathInfo)) {
            return null;
        }
        String[] segments = pathInfo.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            if (TextUtil.isBlank(segments[i])) {
                continue;
            }
            try {
                return Long.parseLong(segments[i]);
            } catch (NumberFormatException e) {
                // 该段不是编号（例如 /api/analysis/12/feedback 的 feedback），继续向前找
                continue;
            }
        }
        return null;
    }

    /**
     * 读取第 index 段路径参数（从 0 开始，已忽略首个空串）。
     *
     * @param request HTTP 请求
     * @param index   段序号
     * @return 路径段，不存在返回 null
     */
    protected String pathSegment(HttpServletRequest request, int index) {
        String pathInfo = request.getPathInfo();
        if (TextUtil.isBlank(pathInfo) || "/".equals(pathInfo)) {
            return null;
        }
        String[] segments = pathInfo.substring(1).split("/");
        return index < segments.length && !TextUtil.isBlank(segments[index]) ? segments[index] : null;
    }

    /**
     * 读取字符串参数。
     *
     * @param request      HTTP 请求
     * @param name         参数名
     * @param defaultValue 默认值
     * @return 参数值
     */
    protected String param(HttpServletRequest request, String name, String defaultValue) {
        String value = request.getParameter(name);
        return TextUtil.isBlank(value) ? defaultValue : value.trim();
    }

    /**
     * 读取整数参数。
     *
     * @param request      HTTP 请求
     * @param name         参数名
     * @param defaultValue 默认值
     * @return 参数值
     */
    protected int intParam(HttpServletRequest request, String name, int defaultValue) {
        return TextUtil.parseInt(request.getParameter(name), defaultValue);
    }

    /**
     * 读取可空长整数参数。
     *
     * @param request HTTP 请求
     * @param name    参数名
     * @return 参数值，缺省或非法返回 null
     */
    protected Long longParam(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (TextUtil.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw BizException.badRequest("参数 " + name + " 必须是数字编号");
        }
    }

    /**
     * 读取可空整数参数。
     *
     * @param request HTTP 请求
     * @param name    参数名
     * @return 参数值，缺省或非法返回 null
     */
    protected Integer integerParam(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (TextUtil.isBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw BizException.badRequest("参数 " + name + " 必须是整数");
        }
    }

    /**
     * 统一执行业务并输出结果。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  业务逻辑
     */
    protected void handle(HttpServletRequest request, HttpServletResponse response, Handler handler) {
        try {
            Object data = handler.execute();
            if (data instanceof com.flavorlogic.util.ApiResponse) {
                // 允许业务自行指定提示信息（如「注册成功，请登录」）
                JsonUtil.write(response, HttpServletResponse.SC_OK, (com.flavorlogic.util.ApiResponse<?>) data);
            } else if (data == null) {
                JsonUtil.writeOk(response);
            } else {
                JsonUtil.writeOk(response, data);
            }
        } catch (BizException e) {
            JsonUtil.writeError(response, httpStatusOf(e.getCode()), e.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "接口处理失败：" + request.getMethod() + " " + request.getRequestURI(), e);
            JsonUtil.writeError(response, HttpServletResponse.SC_OK, 500, "服务器内部错误，请稍后重试");
        }
    }

    /**
     * 方法不支持时的统一响应。
     *
     * @param response HTTP 响应
     */
    protected void methodNotAllowed(HttpServletResponse response) {
        JsonUtil.writeError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, 405, "该接口不支持此请求方法");
    }

    /**
     * 读取请求体并绑定为对象，请求体为空时抛参数异常。
     *
     * @param request HTTP 请求
     * @param type    目标类型
     * @param <T>     目标类型
     * @return 绑定结果
     */
    protected <T> T readBody(HttpServletRequest request, Class<T> type) {
        T body = JsonUtil.readBody(request, type);
        if (body == null) {
            throw BizException.badRequest("请求体不能为空");
        }
        return body;
    }

    private int httpStatusOf(int businessCode) {
        switch (businessCode) {
            case BizException.CODE_UNAUTHORIZED:
                return HttpServletResponse.SC_UNAUTHORIZED;
            case BizException.CODE_FORBIDDEN:
                return HttpServletResponse.SC_FORBIDDEN;
            case BizException.CODE_NOT_FOUND:
                return HttpServletResponse.SC_NOT_FOUND;
            default:
                return HttpServletResponse.SC_OK;
        }
    }

    /**
     * 输出 JSON（供个别直接写响应的场景使用）。
     *
     * @param response HTTP 响应
     * @param status   HTTP 状态码
     * @param code     业务码
     * @param message  提示信息
     */
    protected void writeError(HttpServletResponse response, int status, int code, String message) {
        JsonUtil.writeError(response, status, code, message);
    }

    /**
     * 判断请求是否期望 JSON 响应（页面跳转场景使用）。
     *
     * @param request HTTP 请求
     * @return 是否 AJAX 请求
     */
    protected boolean isAjax(HttpServletRequest request) {
        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        return "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (accept != null && accept.contains("application/json"))
                || (uri != null && uri.startsWith(request.getContextPath() + "/api/"));
    }

    /**
     * 页面跳转。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param path     站内路径（以 / 开头）
     */
    protected void redirect(HttpServletRequest request, HttpServletResponse response, String path) {
        try {
            response.sendRedirect(request.getContextPath() + path);
        } catch (IOException e) {
            throw new IllegalStateException("页面跳转失败", e);
        }
    }
}
