package com.flavorlogic.servlet;

import com.flavorlogic.service.Services;
import com.flavorlogic.util.PageResult;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 食材属性接口（公开只读）。
 *
 * <p>对应接口路径 {@code GET /api/ingredients}：返回启用中的食材八维风味属性，
 * 同时附带食材分类列表，供分析页与知识库页面的下拉筛选直接使用。</p>
 */
@WebServlet("/api/ingredients")
public class IngredientServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /**
     * 返回启用中的食材列表与分类列表。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            // 使用 LinkedHashMap 保证返回字段顺序稳定，便于前端与接口文档对照
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("list", Services.meta().listEnabledIngredients());
            data.put("categories", Services.meta().ingredientCategories());
            return data;
        });
    }

    /**
     * 基础数据不开放写操作（写操作在 {@code /api/admin/ingredients}）。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }

    /**
     * 基础数据不开放写操作。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }

    /**
     * 基础数据不开放写操作。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }
}
