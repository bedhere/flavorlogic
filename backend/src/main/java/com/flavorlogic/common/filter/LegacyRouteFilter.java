package com.flavorlogic.common.filter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** 旧地址兼容：仅站内固定映射，保留查询参数；没有复制旧页面或旧业务代码。 */
public class LegacyRouteFilter implements Filter {
    private static final Map<String, String> ROUTES = new HashMap<>();
    static {
        ROUTES.put("/admin/articles.html", "/admin/article/articles.html");
        ROUTES.put("/admin/evidence.html", "/admin/knowledge/evidence.html");
        ROUTES.put("/admin/ingredients.html", "/admin/ingredient/ingredients.html");
        ROUTES.put("/admin/knowledge.html", "/admin/knowledge/knowledge.html");
        ROUTES.put("/admin/tasks.html", "/admin/analysis/tasks.html");
        ROUTES.put("/admin/users.html", "/admin/user/users.html");
        ROUTES.put("/analysis-history.html", "/modules/analysis/analysis-history.html");
        ROUTES.put("/analysis-result.html", "/modules/analysis/analysis-result.html");
        ROUTES.put("/analysis-run.html", "/modules/analysis/analysis-run.html");
        ROUTES.put("/article-detail.html", "/modules/article/article-detail.html");
        ROUTES.put("/articles.html", "/modules/article/articles.html");
        ROUTES.put("/assets/back-cookie.png", "/shared/assets/back-cookie.png");
        ROUTES.put("/assets/cookie-texture.jpg", "/shared/assets/cookie-texture.jpg");
        ROUTES.put("/assets/cookie.glb", "/shared/assets/cookie.glb");
        ROUTES.put("/assets/front-cookie.png", "/shared/assets/front-cookie.png");
        ROUTES.put("/assets/hero-chef.jpg", "/shared/assets/hero-chef.jpg");
        ROUTES.put("/assets/hero-ingredients.jpg", "/shared/assets/hero-ingredients.jpg");
        ROUTES.put("/assets/hero-kitchen.jpg", "/shared/assets/hero-kitchen.jpg");
        ROUTES.put("/assets/knowledge-ingredients.jpg", "/shared/assets/knowledge-ingredients.jpg");
        ROUTES.put("/assets/knowledge-lab.jpg", "/shared/assets/knowledge-lab.jpg");
        ROUTES.put("/assets/wildcrumb-landscape.webp", "/shared/assets/wildcrumb-landscape.webp");
        ROUTES.put("/css/app.css", "/shared/styles/app.css");
        ROUTES.put("/css/landing.css", "/shared/styles/landing.css");
        ROUTES.put("/css/pages.css", "/shared/styles/pages.css");
        ROUTES.put("/dashboard.html", "/modules/dashboard/dashboard.html");
        ROUTES.put("/error.html", "/modules/public/error.html");
        ROUTES.put("/features.html", "/modules/public/features.html");
        ROUTES.put("/js/components/fl-chart.js", "/shared/components/fl-chart.js");
        ROUTES.put("/js/components/fl-ingredient-picker.js", "/shared/components/fl-ingredient-picker.js");
        ROUTES.put("/js/components/landing-motion.js", "/shared/components/landing-motion.js");
        ROUTES.put("/js/core/api.js", "/shared/core/api.js");
        ROUTES.put("/js/core/common.js", "/shared/core/common.js");
        ROUTES.put("/js/pages/admin/articles.js", "/admin/article/articles.js");
        ROUTES.put("/js/pages/admin/evidence.js", "/admin/knowledge/evidence.js");
        ROUTES.put("/js/pages/admin/ingredients.js", "/admin/ingredient/ingredients.js");
        ROUTES.put("/js/pages/admin/knowledge.js", "/admin/knowledge/knowledge.js");
        ROUTES.put("/js/pages/admin/tasks.js", "/admin/analysis/tasks.js");
        ROUTES.put("/js/pages/admin/users.js", "/admin/user/users.js");
        ROUTES.put("/js/pages/analysis-history.js", "/modules/analysis/analysis-history.js");
        ROUTES.put("/js/pages/analysis-result.js", "/modules/analysis/analysis-result.js");
        ROUTES.put("/js/pages/analysis-run.js", "/modules/analysis/analysis-run.js");
        ROUTES.put("/js/pages/article-detail.js", "/modules/article/article-detail.js");
        ROUTES.put("/js/pages/articles.js", "/modules/article/articles.js");
        ROUTES.put("/js/pages/dashboard.js", "/modules/dashboard/dashboard.js");
        ROUTES.put("/js/pages/error.js", "/modules/public/error.js");
        ROUTES.put("/js/pages/features.js", "/modules/public/features.js");
        ROUTES.put("/js/pages/index.js", "/index.js");
        ROUTES.put("/js/pages/knowledge-search.js", "/modules/knowledge/knowledge-search.js");
        ROUTES.put("/js/pages/login.js", "/modules/auth/login.js");
        ROUTES.put("/js/pages/profile.js", "/modules/profile/profile.js");
        ROUTES.put("/js/pages/recipe-edit.js", "/modules/recipe/recipe-edit.js");
        ROUTES.put("/js/pages/recipe-generate.js", "/modules/recipe/recipe-generate.js");
        ROUTES.put("/js/pages/recipes.js", "/modules/recipe/recipes.js");
        ROUTES.put("/js/pages/register.js", "/modules/auth/register.js");
        ROUTES.put("/knowledge-search.html", "/modules/knowledge/knowledge-search.html");
        ROUTES.put("/lib/GLTFLoader.js", "/shared/vendor/GLTFLoader.js");
        ROUTES.put("/lib/bootstrap.bundle.min.js", "/shared/vendor/bootstrap.bundle.min.js");
        ROUTES.put("/lib/bootstrap.min.css", "/shared/vendor/bootstrap.min.css");
        ROUTES.put("/lib/chart.umd.js", "/shared/vendor/chart.umd.js");
        ROUTES.put("/lib/jquery.min.js", "/shared/vendor/jquery.min.js");
        ROUTES.put("/lib/three.min.js", "/shared/vendor/three.min.js");
        ROUTES.put("/login.html", "/modules/auth/login.html");
        ROUTES.put("/profile.html", "/modules/profile/profile.html");
        ROUTES.put("/recipe-edit.html", "/modules/recipe/recipe-edit.html");
        ROUTES.put("/recipe-generate.html", "/modules/recipe/recipe-generate.html");
        ROUTES.put("/recipes.html", "/modules/recipe/recipes.html");
        ROUTES.put("/register.html", "/modules/auth/register.html");
    }

    @Override
    public void doFilter(ServletRequest input, ServletResponse output, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) input;
        HttpServletResponse response = (HttpServletResponse) output;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String target = ROUTES.get(path);
        // 数据库中历史 coverUrl 仍可能使用 assets/ 前缀。
        if (target == null && path.startsWith("/assets/")) {
            target = "/shared/assets/" + path.substring("/assets/".length());
        }
        if (target != null) {
            String query = request.getQueryString();
            response.sendRedirect(request.getContextPath() + target + (query == null ? "" : "?" + query));
            return;
        }
        chain.doFilter(input, output);
    }
}
