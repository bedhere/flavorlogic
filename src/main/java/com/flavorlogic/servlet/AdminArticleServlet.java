package com.flavorlogic.servlet;

import com.flavorlogic.model.KnowledgeArticle;
import com.flavorlogic.model.User;
import com.flavorlogic.service.Services;
import com.flavorlogic.util.BizException;
import com.flavorlogic.util.JsonUtil;
import com.flavorlogic.util.OperationLog;
import com.flavorlogic.util.PageResult;
import com.flavorlogic.util.TextUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端研发知识库接口（全部需要管理员身份）。
 *
 * <p>对应接口路径：
 * <ul>
 *   <li>{@code GET /api/admin/articles}：分页查询文章（含草稿与下架）；</li>
 *   <li>{@code GET /api/admin/articles/categories}：文章分类下拉数据；</li>
 *   <li>{@code POST /api/admin/articles}：新增文章；</li>
 *   <li>{@code POST /api/admin/articles/sync}：外部内容同步（简化版，不联网）；</li>
 *   <li>{@code PUT /api/admin/articles/{id}}：编辑文章；</li>
 *   <li>{@code DELETE /api/admin/articles/{id}}：软删除文章（status=3）。</li>
 * </ul>
 * 所有写操作成功后写入文件操作日志，便于课设演示「操作留痕」。</p>
 */
@WebServlet("/api/admin/articles/*")
public class AdminArticleServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /** 模拟同步构造的演示条目数 */
    private static final int SIMULATED_ITEM_COUNT = 2;

    /** 模拟同步时用于演示「来源地址重复拦截」的地址 */
    private static final String SIMULATED_DUPLICATE_URL = "https://example.com/demo-duplicate";

    /**
     * 管理端列表：{@code categories} 子路径返回分类下拉数据，其余走文章分页。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            // 后台分类下拉数据：/api/admin/articles/categories
            if ("categories".equals(pathSegment(request, 0))) {
                return Services.article().categories();
            }
            Long categoryId = longParam(request, "categoryId");
            String keyword = param(request, "keyword", null);
            // status 为空表示不筛选；非法值由 integerParam 抛参数异常
            Integer status = integerParam(request, "status");
            int page = intParam(request, "page", 1);
            int pageSize = intParam(request, "pageSize", 10);
            PageResult<KnowledgeArticle> result =
                    Services.article().adminPage(categoryId, keyword, status, page, pageSize);
            return result;
        });
    }

    /**
     * 新增文章；子路径为 {@code sync} 时执行同步逻辑。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            User operator = requireAdmin(request);
            if ("sync".equals(pathSegment(request, 0))) {
                return sync(request);
            }
            KnowledgeArticle article = readBody(request, KnowledgeArticle.class);
            KnowledgeArticle saved = Services.article().adminCreate(operator, article);
            OperationLog.record(request, "新增文章", "文章#" + saved.getId()
                    + TextUtil.defaultIfBlank(saved.getTitle(), ""), "成功");
            return saved;
        });
    }

    /**
     * 编辑文章：以路径编号为准，避免请求体编号与路径不一致。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            User operator = requireAdmin(request);
            Long id = pathId(request);
            if (id == null) {
                throw BizException.badRequest("缺少文章编号");
            }
            KnowledgeArticle article = readBody(request, KnowledgeArticle.class);
            // 路径编号优先，防止前端传错或篡改请求体中的 id
            article.setId(id);
            KnowledgeArticle saved = Services.article().adminUpdate(operator, article);
            OperationLog.record(request, "编辑文章", "文章#" + id
                    + TextUtil.defaultIfBlank(saved.getTitle(), ""), "成功");
            return saved;
        });
    }

    /**
     * 软删除文章：仅把状态改为 status=3，数据保留以便追溯。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            Long id = pathId(request);
            if (id == null) {
                throw BizException.badRequest("缺少文章编号");
            }
            Services.article().adminUpdateStatus(id, KnowledgeArticle.STATUS_DELETED);
            OperationLog.record(request, "删除文章", "文章#" + id, "成功");
            return null;
        });
    }

    /**
     * 外部内容同步（简化版，不联网抓取）。
     *
     * <p>请求体形如
     * {@code {"items":[{"title":"","summary":"","content":"","sourceName":"","sourceUrl":"","categoryId":123}]}}：</p>
     * <ul>
     *   <li>请求体为空或 items 为空 → 模拟模式：构造演示条目，只做重复检测不写库；</li>
     *   <li>items 非空 → 逐条以草稿状态入库，被拦截的条目收集到 duplicated 列表。</li>
     * </ul>
     *
     * @param request HTTP 请求
     * @return 同步结果 Map
     */
    private Map<String, Object> sync(HttpServletRequest request) {
        JsonObject body = JsonUtil.readBodyAsObject(request);
        JsonArray items = body == null ? null : body.getAsJsonArray("items");
        if (items == null || items.size() == 0) {
            return simulatedSync(request);
        }
        List<Map<String, Object>> duplicated = new ArrayList<>();
        int imported = 0;
        // 逐条处理：单条被拦截不影响整批，重复/非法条目收集后统一返回
        for (JsonElement element : items) {
            String title = null;
            try {
                KnowledgeArticle article = toArticle(element);
                title = article.getTitle();
                Services.article().adminCreate(requireAdmin(request), article);
                imported++;
            } catch (BizException e) {
                // 重复或校验不通过：记录原因后继续处理后续条目
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", title);
                item.put("reason", e.getMessage());
                duplicated.add(item);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("simulated", false);
        result.put("total", items.size());
        result.put("imported", imported);
        result.put("duplicated", duplicated);
        OperationLog.record(request, "同步文章", "外部内容同步（草稿导入）",
                "成功：导入 " + imported + " 条，拦截 " + duplicated.size() + " 条");
        return result;
    }

    /**
     * 模拟同步：不访问网络、不写库，仅演示同步流程与重复检测结果。
     *
     * @param request HTTP 请求
     * @return 模拟同步结果 Map
     */
    private Map<String, Object> simulatedSync(HttpServletRequest request) {
        List<Map<String, Object>> duplicated = new ArrayList<>();
        for (int i = 1; i <= SIMULATED_ITEM_COUNT; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", "演示同步条目 " + i);
            item.put("sourceUrl", i == 1 ? "https://example.com/demo-" + i : SIMULATED_DUPLICATE_URL);
            item.put("reason", i == 1 ? "模拟条目：未写库，仅用于展示同步流程"
                    : "来源地址已存在（" + SIMULATED_DUPLICATE_URL + "），已拦截");
            duplicated.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("simulated", true);
        result.put("total", SIMULATED_ITEM_COUNT);
        result.put("imported", 0);
        result.put("duplicated", duplicated);
        result.put("message", "当前为模拟同步：第一阶段仅支持管理员手动维护文章，未抓取外部网络内容");
        OperationLog.record(request, "同步文章", "外部内容同步（模拟模式）", "成功：模拟检测 " + SIMULATED_ITEM_COUNT + " 条");
        return result;
    }

    /**
     * 把同步条目 JSON 转换为文章实体（状态固定为草稿，等待人工审核发布）。
     *
     * @param element 条目 JSON
     * @return 文章实体
     */
    private KnowledgeArticle toArticle(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw BizException.badRequest("同步条目必须是 JSON 对象");
        }
        JsonObject json = element.getAsJsonObject();
        KnowledgeArticle article = new KnowledgeArticle();
        article.setTitle(text(json, "title"));
        article.setSummary(text(json, "summary"));
        article.setContent(text(json, "content"));
        article.setSourceName(text(json, "sourceName"));
        article.setSourceUrl(text(json, "sourceUrl"));
        article.setCategoryId(longValue(json, "categoryId"));
        // 同步内容一律以草稿落库，是否发布由管理员在后台确认
        article.setStatus(KnowledgeArticle.STATUS_DRAFT);
        return article;
    }

    /**
     * 读取字符串字段。
     *
     * @param json  JSON 对象
     * @param name  字段名
     * @return 字段值，缺失或 null 返回 null
     */
    private String text(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        String value = element.getAsString();
        return TextUtil.isBlank(value) ? null : value.trim();
    }

    /**
     * 读取长整数字段。
     *
     * @param json JSON 对象
     * @param name 字段名
     * @return 字段值，缺失、null 或非数字返回 null
     */
    private Long longValue(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
