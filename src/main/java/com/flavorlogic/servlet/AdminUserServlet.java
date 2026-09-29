package com.flavorlogic.servlet;

import com.flavorlogic.model.User;
import com.flavorlogic.service.Services;
import com.flavorlogic.util.BizException;
import com.flavorlogic.util.JsonUtil;
import com.flavorlogic.util.OperationLog;
import com.flavorlogic.util.PageResult;
import com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 管理端用户管理接口（全部需要管理员身份）。
 *
 * <p>对应接口路径：
 * <ul>
 *   <li>{@code GET /api/admin/users}：用户分页查询，支持关键字、角色、状态筛选；</li>
 *   <li>{@code PUT /api/admin/users/{id}/role}：调整用户角色（请求体 {@code {"role":"ADMIN"}}）；</li>
 *   <li>{@code PUT /api/admin/users/{id}/status}：启用/禁用账号（请求体 {@code {"status":0}}）。</li>
 * </ul>
 * 角色与状态的合法取值、越权保护（如不允许禁用自己）由 Service 校验。</p>
 */
@WebServlet("/api/admin/users/*")
public class AdminUserServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /**
     * 用户分页查询。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            String keyword = param(request, "keyword", null);
            String role = param(request, "role", null);
            // status 缺省为 null，表示不按状态筛选
            Integer status = integerParam(request, "status");
            int page = intParam(request, "page", 1);
            int pageSize = intParam(request, "pageSize", 10);
            PageResult<User> result = Services.user().adminPage(keyword, role, status, page, pageSize);
            return result;
        });
    }

    /**
     * 调整角色或启用/禁用账号：路径形如 {@code /{id}/role} 与 {@code /{id}/status}。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            User operator = requireAdmin(request);
            // 第 0 段是目标用户编号，第 1 段是动作
            Long userId = longValue(pathSegment(request, 0));
            String action = pathSegment(request, 1);
            if (userId == null || action == null) {
                throw BizException.badRequest("请求路径应为 /api/admin/users/{id}/{role|status}");
            }
            JsonObject body = JsonUtil.readBodyAsObject(request);
            if ("role".equals(action)) {
                String role = readRole(body);
                Services.user().adminUpdateRole(operator.getId(), userId, role);
                OperationLog.record(request, "调整用户角色", "用户#" + userId, "成功：" + role);
            } else if ("status".equals(action)) {
                int status = readStatus(body);
                Services.user().adminUpdateStatus(operator.getId(), userId, status);
                OperationLog.record(request, "启用/禁用用户", "用户#" + userId, "成功：status=" + status);
            } else {
                throw BizException.badRequest("不支持的操作");
            }
            // 返回 null → 统一输出 {code:0}
            return null;
        });
    }

    /**
     * 管理端不开放用户新增。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }

    /**
     * 管理端不开放用户删除（账号只做禁用）。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        methodNotAllowed(response);
    }

    /**
     * 解析路径段中的编号。
     *
     * @param segment 路径段
     * @return 编号，缺失或非数字返回 null
     */
    private Long longValue(String segment) {
        if (segment == null) {
            return null;
        }
        try {
            return Long.parseLong(segment.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 读取请求体中的 role 字段。
     *
     * @param body 请求体 JSON，可为 null
     * @return 角色编码
     */
    private String readRole(JsonObject body) {
        if (body == null || !body.has("role") || body.get("role").isJsonNull()) {
            throw BizException.badRequest("缺少参数 role");
        }
        return body.get("role").getAsString();
    }

    /**
     * 读取请求体中的 status 字段。
     *
     * @param body 请求体 JSON，可为 null
     * @return 状态值（1 启用，0 禁用）
     */
    private int readStatus(JsonObject body) {
        if (body == null || !body.has("status") || body.get("status").isJsonNull()) {
            throw BizException.badRequest("缺少参数 status");
        }
        try {
            return body.get("status").getAsInt();
        } catch (RuntimeException e) {
            throw BizException.badRequest("参数 status 必须是整数");
        }
    }
}
