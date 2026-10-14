package com.flavorlogic.module.knowledge.servlet;
import com.flavorlogic.common.servlet.BaseServlet;

import com.flavorlogic.module.knowledge.model.EvidenceClaim;
import com.flavorlogic.module.user.model.User;
import com.flavorlogic.common.service.Services;
import com.flavorlogic.common.util.BizException;
import com.flavorlogic.common.util.OperationLog;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** 管理端证据声明录入与审核接口。 */
@WebServlet("/api/admin/knowledge/claims/*")
public class AdminEvidenceClaimServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            return Services.evidenceClaim().list(longParam(request, "documentId"),
                    longParam(request, "ingredientId"), param(request, "reviewStatus", null));
        });
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            EvidenceClaim saved = Services.evidenceClaim().create(readBody(request, EvidenceClaim.class));
            OperationLog.record(request, "录入证据声明", "声明#" + saved.getId(), "进入审核队列");
            return saved;
        });
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            User operator = requireAdmin(request);
            Long id = pathId(request);
            if (id == null) {
                throw BizException.badRequest("缺少证据声明编号");
            }
            EvidenceClaim body = readBody(request, EvidenceClaim.class);
            EvidenceClaim saved = Services.evidenceClaim().review(operator.getId(), id,
                    body.getReviewStatus(), body.getReviewNote());
            OperationLog.record(request, "审核证据声明", "声明#" + id, "状态：" + saved.getReviewStatus());
            return saved;
        });
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }
}
