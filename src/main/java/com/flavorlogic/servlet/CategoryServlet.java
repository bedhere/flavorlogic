package com.flavorlogic.servlet;

import com.flavorlogic.service.Services;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 文章分类接口（公开只读）。
 *
 * <p>对应接口路径 {@code GET /api/categories}：返回启用中的文章分类，
 * 供知识库列表页与后台文章表单的分类下拉使用。</p>
 */
@WebServlet("/api/categories")
public class CategoryServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /**
     * 返回启用中的文章分类列表。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> Services.article().categories());
    }

    /**
     * 分类由数据库脚本维护，接口不开放写操作。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }

    /**
     * 分类由数据库脚本维护，接口不开放写操作。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }

    /**
     * 分类由数据库脚本维护，接口不开放写操作。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }
}
