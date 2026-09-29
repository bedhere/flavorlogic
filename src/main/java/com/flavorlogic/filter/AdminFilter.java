package com.flavorlogic.filter;

import com.flavorlogic.util.JsonUtil;
import com.flavorlogic.util.SessionUtil;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 管理员权限过滤器：拦截 {@code /admin/*} 页面与 {@code /api/admin/*} 接口。
 *
 * <p>该过滤器在 {@link AuthFilter} 之后执行，因此到达这里时“未登录”已被处理，
 * 此处只需判断角色；普通用户访问后台页面会被引导到 403 提示页。</p>
 */
public class AdminFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
        // 无需初始化参数
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String path = pathOf(request);

        if (SessionUtil.isAdmin(request)) {
            chain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/")) {
            JsonUtil.writeError(response, HttpServletResponse.SC_FORBIDDEN, 403,
                    "该操作需要管理员权限");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/error.html?code=403");
    }

    @Override
    public void destroy() {
        // 无需释放资源
    }

    private String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = context != null && !context.isEmpty() && uri.startsWith(context)
                ? uri.substring(context.length()) : uri;
        return path.isEmpty() ? "/" : path;
    }
}
