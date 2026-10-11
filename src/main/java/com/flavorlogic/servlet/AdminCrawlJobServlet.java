package com.flavorlogic.servlet;

import com.flavorlogic.model.CrawlJob;
import com.flavorlogic.model.User;
import com.flavorlogic.service.Services;
import com.flavorlogic.util.BizException;
import com.flavorlogic.util.OperationLog;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/** 管理端受控采集任务接口；执行动作一次只抓取一个白名单地址。 */
@WebServlet("/api/admin/knowledge/jobs/*")
public class AdminCrawlJobServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            Long id = pathId(request);
            if (id != null) {
                return Services.crawlJob().detail(id);
            }
            Long siteId = longParam(request, "siteId");
            return Services.crawlJob().list(siteId, param(request, "status", null));
        });
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            User operator = requireAdmin(request);
            Map<String, Object> body = readBody(request, Map.class);
            Long siteId = number(body.get("siteId"), "来源站点编号");
            String seedUrl = body.get("seedUrl") == null ? null : String.valueOf(body.get("seedUrl"));
            CrawlJob job = Services.crawlJob().create(operator.getId(), siteId, seedUrl);
            OperationLog.record(request, "创建采集任务", "任务#" + job.getId(), "进入 PENDING");
            return job;
        });
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            User operator = requireAdmin(request);
            if (!"run".equals(pathSegment(request, 1))) {
                throw BizException.badRequest("采集任务只支持 /{id}/run 执行");
            }
            Long id = pathId(request);
            CrawlJob job = Services.crawlJob().run(operator.getId(), id);
            OperationLog.record(request, "执行采集任务", "任务#" + id, "状态：" + job.getStatus());
            return job;
        });
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }

    private Long number(Object value, String field) {
        if (value == null) {
            throw BizException.badRequest(field + "不能为空");
        }
        if (value instanceof Number) {
            double numeric = ((Number) value).doubleValue();
            if (numeric != Math.rint(numeric)) {
                throw BizException.badRequest(field + "必须是整数编号");
            }
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw BizException.badRequest(field + "必须是数字编号");
        }
    }
}
