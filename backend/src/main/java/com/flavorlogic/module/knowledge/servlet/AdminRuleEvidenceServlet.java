package com.flavorlogic.module.knowledge.servlet;
import com.flavorlogic.common.servlet.BaseServlet;

import com.flavorlogic.module.knowledge.model.RuleEvidence;
import com.flavorlogic.common.service.Services;
import com.flavorlogic.common.util.BizException;
import com.flavorlogic.common.util.OperationLog;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** 管理端规则/知识卡证据关联接口。 */
@WebServlet("/api/admin/knowledge/evidence-links/*")
public class AdminRuleEvidenceServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            return Services.ruleEvidence().list(longParam(request, "ruleId"),
                    longParam(request, "knowledgeId"), longParam(request, "claimId"));
        });
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            RuleEvidence saved = Services.ruleEvidence().link(readBody(request, RuleEvidence.class));
            OperationLog.record(request, "关联规则证据", "关联#" + saved.getId(), "成功");
            return saved;
        });
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            Long id = pathId(request);
            if (id == null) {
                throw BizException.badRequest("缺少关联编号");
            }
            Services.ruleEvidence().unlink(id);
            OperationLog.record(request, "解除规则证据", "关联#" + id, "成功");
            return null;
        });
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }
}
