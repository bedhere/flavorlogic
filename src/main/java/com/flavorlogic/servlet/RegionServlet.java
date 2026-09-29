package com.flavorlogic.servlet;

import com.flavorlogic.service.Services;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 区域风味画像接口（公开只读）。
 *
 * <p>对应接口路径 {@code GET /api/regions}：返回启用中的区域画像，
 * 供分析页选择目标区域、以及页面展示「该区域偏好风味」使用。</p>
 */
@WebServlet("/api/regions")
public class RegionServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /**
     * 返回启用中的区域画像列表。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> Services.meta().listRegions());
    }

    /**
     * 区域画像由数据库脚本维护，接口不开放写操作。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }

    /**
     * 区域画像由数据库脚本维护，接口不开放写操作。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }

    /**
     * 区域画像由数据库脚本维护，接口不开放写操作。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }
}
