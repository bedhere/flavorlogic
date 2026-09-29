package com.flavorlogic.servlet;

import com.flavorlogic.model.KnowledgeArticle;
import com.flavorlogic.service.Services;
import com.flavorlogic.util.PageResult;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户端研发知识库接口。
 *
 * <p>对应接口路径：
 * <ul>
 *   <li>{@code GET /api/articles}：分页查询已发布文章，支持分类与关键字筛选；</li>
 *   <li>{@code GET /api/articles/{id}}：文章详情，同时返回同分类的相关文章。</li>
 * </ul>
 * 本类只做「读参数 → 调 Service → 返回 JSON」，业务校验与 SQL 全部在 Service/DAO 层。</p>
 */
@WebServlet("/api/articles/*")
public class ArticleServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /** 详情页相关文章展示条数 */
    private static final int RELATED_LIMIT = 4;

    /**
     * 列表与详情查询。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            Long id = pathId(request);
            if (id == null) {
                // 无路径编号 → 列表：仅返回已发布文章（分页由 Service 统一收敛范围）
                Long categoryId = longParam(request, "categoryId");
                String keyword = param(request, "keyword", null);
                int page = intParam(request, "page", 1);
                int pageSize = intParam(request, "pageSize", 10);
                PageResult<KnowledgeArticle> result =
                        Services.article().pageForUser(categoryId, keyword, page, pageSize);
                return result;
            }
            // 有路径编号 → 详情：正文已由 Service 做安全过滤并累加浏览次数
            KnowledgeArticle article = Services.article().detailForUser(id);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("article", article);
            // 相关文章：同分类、排除当前文章，取 4 条
            data.put("related", Services.article().related(article.getCategoryId(), article.getId(), RELATED_LIMIT));
            return data;
        });
    }

    /**
     * 用户端知识库不开放写操作。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }

    /**
     * 用户端知识库不开放写操作。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }

    /**
     * 用户端知识库不开放写操作。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }
}
