package com.flavorlogic.servlet;

import com.flavorlogic.model.User;
import com.flavorlogic.service.Services;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 研发工作台接口：{@code GET /api/dashboard}。
 *
 * <p>汇总配方数、分析数、最近配方与任务、最新文章；管理员额外返回平台统计与操作日志。</p>
 */
@WebServlet("/api/dashboard")
public class DashboardServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            User user = requireUser(request);
            return Services.dashboard().overview(user.getId(), user.isAdmin());
        });
    }
}
