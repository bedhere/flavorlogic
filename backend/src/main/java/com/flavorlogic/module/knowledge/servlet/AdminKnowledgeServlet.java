package com.flavorlogic.module.knowledge.servlet;
import com.flavorlogic.common.servlet.BaseServlet;

import com.flavorlogic.module.knowledge.model.SourceDocument;
import com.flavorlogic.module.user.model.User;
import com.flavorlogic.common.service.Services;
import com.flavorlogic.common.util.BizException;
import com.flavorlogic.common.util.OperationLog;
import com.flavorlogic.common.util.PageResult;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 来源文档审核接口。
 *
 * <p>当前只支持管理员手动导入已清洗文本和审核，不访问外部网络；后续接入
 * 抓取器或 Yuxi 时，仍然复用这套审核状态和来源指纹。</p>
 */
@WebServlet("/api/admin/knowledge/documents/*")
public class AdminKnowledgeServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            Long id = pathId(request);
            if (id != null) {
                return Services.sourceDocument().detail(id);
            }
            String keyword = param(request, "keyword", null);
            String status = param(request, "reviewStatus", null);
            int page = intParam(request, "page", 1);
            int pageSize = intParam(request, "pageSize", 20);
            PageResult<SourceDocument> result = Services.sourceDocument().page(keyword, status, page, pageSize);
            return result;
        });
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            User operator = requireAdmin(request);
            SourceDocument document = readBody(request, SourceDocument.class);
            SourceDocument saved = Services.sourceDocument().importDraft(operator.getId(), document);
            OperationLog.record(request, "导入来源文档", "文档#" + saved.getId(), "进入审核队列");
            return saved;
        });
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            User operator = requireAdmin(request);
            Long id = pathId(request);
            if (id == null) {
                throw BizException.badRequest("缺少来源文档编号");
            }
            SourceDocument body = readBody(request, SourceDocument.class);
            SourceDocument saved = Services.sourceDocument().review(operator.getId(), id,
                    body.getReviewStatus(), body.getReviewNote());
            OperationLog.record(request, "审核来源文档", "文档#" + id,
                    "状态：" + saved.getReviewStatus());
            return saved;
        });
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }
}
