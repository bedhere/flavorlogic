package com.flavorlogic.servlet;

import com.flavorlogic.model.AnalysisTask;
import com.flavorlogic.service.Services;
import com.flavorlogic.util.PageResult;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端风味分析任务接口（需要管理员身份）。
 *
 * <p>对应接口路径 {@code GET /api/admin/tasks}：分页查询全平台分析任务，
 * 支持关键字、分析目标类型（goalType）与状态筛选；除分页数据外
 * 额外返回 {@code totalAll}（平台任务总数），供后台首页卡片展示。</p>
 */
@WebServlet("/api/admin/tasks")
public class AdminTaskServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /**
     * 分页查询分析任务并附带平台任务总数。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            String keyword = param(request, "keyword", null);
            String goalType = param(request, "goalType", null);
            String status = param(request, "status", null);
            int page = intParam(request, "page", 1);
            int pageSize = intParam(request, "pageSize", 10);
            PageResult<AnalysisTask> result =
                    Services.analysis().adminPage(keyword, goalType, status, page, pageSize);
            // LinkedHashMap 保证字段顺序稳定：先分页数据，再汇总数字
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("page", result);
            data.put("totalAll", Services.analysis().countAll());
            return data;
        });
    }

    /**
     * 管理端任务列表为只读统计接口，不开放写操作。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }

    /**
     * 管理端任务列表为只读统计接口，不开放写操作。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }

    /**
     * 管理端任务列表为只读统计接口，不开放写操作。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }
}
