package com.flavorlogic.servlet;

import com.flavorlogic.service.Services;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 风味补偿规则接口：{@code GET /api/rules}。
 *
 * <p>返回启用中的规则列表，供结果页与后台说明「这条建议为什么产生」。
 * 第一阶段规则由初始化数据提供，后台只读展示，不开放修改。</p>
 */
@WebServlet("/api/rules")
public class RuleServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireUser(request);
            return Services.meta().listRules();
        });
    }
}
