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
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 登录过滤器：拦截需要登录的页面与接口。
 *
 * <p>页面请求未登录时跳转登录页并带上回跳地址；接口请求未登录时返回 401，
 * 由前端统一跳转登录页。</p>
 */
public class AuthFilter implements Filter {

    /** 游客可访问的页面 */
    private static final Set<String> PUBLIC_PAGES = new HashSet<>(Arrays.asList(
            "/", "/index.html", "/login.html", "/register.html",
            "/articles.html", "/article-detail.html", "/error.html",
            // 产品说明页：首页导航面板的落地页，必须在未登录时也能打开，
            // 否则新访客点开「它到底怎么算的」会直接被弹回登录页
            "/features.html"));

    /** 游客可访问的接口 */
    private static final Set<String> PUBLIC_APIS = new HashSet<>(Arrays.asList(
            "/api/auth/login", "/api/auth/register", "/api/auth/logout", "/api/auth/me",
            "/api/categories"));

    /** 游客可访问的静态资源前缀 */
    private static final String[] PUBLIC_PREFIXES = {
            "/css/", "/js/", "/assets/", "/lib/", "/favicon.ico", "/api/articles"};

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

        if (isPublic(path) || SessionUtil.isLoggedIn(request)) {
            chain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/")) {
            JsonUtil.writeError(response, HttpServletResponse.SC_UNAUTHORIZED, 401,
                    "登录状态已失效，请重新登录");
            return;
        }
        String redirect = URLEncoder.encode(path, "UTF-8");
        response.sendRedirect(request.getContextPath() + "/login.html?redirect=" + redirect);
    }

    @Override
    public void destroy() {
        // 无需释放资源
    }

    private boolean isPublic(String path) {
        if (PUBLIC_PAGES.contains(path) || PUBLIC_APIS.contains(path)) {
            return true;
        }
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = context != null && !context.isEmpty() && uri.startsWith(context)
                ? uri.substring(context.length()) : uri;
        return path.isEmpty() ? "/" : path;
    }
}
