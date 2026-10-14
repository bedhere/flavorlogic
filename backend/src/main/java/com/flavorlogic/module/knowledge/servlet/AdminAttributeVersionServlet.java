package com.flavorlogic.module.knowledge.servlet;
import com.flavorlogic.common.servlet.BaseServlet;

import com.flavorlogic.module.knowledge.model.IngredientAttributeVersion;
import com.flavorlogic.module.user.model.User;
import com.flavorlogic.common.service.Services;
import com.flavorlogic.common.util.BizException;
import com.flavorlogic.common.util.OperationLog;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** 管理端食材属性版本录入、审核和显式应用接口。 */
@WebServlet("/api/admin/knowledge/attributes/*")
public class AdminAttributeVersionServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            return Services.attributeVersion().list(longParam(request, "ingredientId"),
                    param(request, "dimension", null), param(request, "reviewStatus", null));
        });
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            IngredientAttributeVersion saved = Services.attributeVersion()
                    .create(readBody(request, IngredientAttributeVersion.class));
            OperationLog.record(request, "录入食材属性版本", "属性版本#" + saved.getId(), "进入审核队列");
            return saved;
        });
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            User operator = requireAdmin(request);
            Long id = pathId(request);
            if (id == null) {
                throw BizException.badRequest("缺少属性版本编号");
            }
            if ("apply".equals(pathSegment(request, 1))) {
                IngredientAttributeVersion applied = Services.attributeVersion().apply(operator.getId(), id);
                OperationLog.record(request, "应用食材属性版本", "属性版本#" + id, "已写入食材主表");
                return applied;
            }
            IngredientAttributeVersion body = readBody(request, IngredientAttributeVersion.class);
            IngredientAttributeVersion saved = Services.attributeVersion().review(operator.getId(), id,
                    body.getReviewStatus(), body.getReviewNote());
            OperationLog.record(request, "审核食材属性版本", "属性版本#" + id,
                    "状态：" + saved.getReviewStatus());
            return saved;
        });
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }
}
