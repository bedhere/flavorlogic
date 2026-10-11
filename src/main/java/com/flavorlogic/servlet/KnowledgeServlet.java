package com.flavorlogic.servlet;

import com.flavorlogic.model.EvidenceItem;
import com.flavorlogic.service.Services;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审核来源检索接口。当前实现使用 MySQL FULLTEXT，接口形状为后续 Yuxi 适配器保留。
 */
@WebServlet("/api/knowledge/search")
public class KnowledgeServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireUser(request);
            String query = param(request, "q", param(request, "keyword", null));
            int limit = intParam(request, "limit", 5);
            List<EvidenceItem> items = Services.sourceDocument().search(query, limit);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("provider", "MYSQL_FULLTEXT");
            result.put("query", query);
            result.put("items", items);
            return result;
        });
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }
}
