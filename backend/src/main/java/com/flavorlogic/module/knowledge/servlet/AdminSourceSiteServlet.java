package com.flavorlogic.module.knowledge.servlet;
import com.flavorlogic.common.servlet.BaseServlet;

import com.flavorlogic.module.knowledge.model.SourceSite;
import com.flavorlogic.common.service.Services;
import com.flavorlogic.common.util.BizException;
import com.flavorlogic.common.util.OperationLog;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** 管理端来源站点白名单接口；登记站点不会自动触发网络请求。 */
@WebServlet("/api/admin/knowledge/sites/*")
public class AdminSourceSiteServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            Long id = pathId(request);
            if (id != null) {
                return Services.sourceSite().detail(id);
            }
            return Services.sourceSite().list("1".equals(param(request, "enabledOnly", "false")));
        });
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            SourceSite saved = Services.sourceSite().create(readBody(request, SourceSite.class));
            OperationLog.record(request, "登记来源站点", "站点#" + saved.getId(), "成功");
            return saved;
        });
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            Long id = pathId(request);
            if (id == null) {
                throw BizException.badRequest("缺少来源站点编号");
            }
            SourceSite saved = Services.sourceSite().update(id, readBody(request, SourceSite.class));
            OperationLog.record(request, "更新来源站点", "站点#" + id, "成功");
            return saved;
        });
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            Long id = pathId(request);
            if (id == null) {
                throw BizException.badRequest("缺少来源站点编号");
            }
            SourceSite current = Services.sourceSite().detail(id);
            current.setStatus(0);
            Services.sourceSite().update(id, current);
            OperationLog.record(request, "停用来源站点", "站点#" + id, "成功");
            return null;
        });
    }
}
