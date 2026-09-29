package com.flavorlogic.servlet;

import com.flavorlogic.model.Recipe;
import com.flavorlogic.service.Services;
import com.flavorlogic.util.BizException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * 配方接口：{@code /api/recipes/*}。
 *
 * <ul>
 *   <li>GET    /api/recipes —— 当前用户配方分页（keyword、page、pageSize）</li>
 *   <li>GET    /api/recipes/profiles —— 当前用户全部配方的风味画像（<b>只算不存</b>，
 *       不创建分析任务、不写分析历史；供配方库列表标签与「查看画像」弹窗使用）</li>
 *   <li>GET    /api/recipes/{id}/compose?goalType=&amp;regionProfileId=&amp;allergens=
 *       —— <b>依据研发目标合成一份可执行的新配方</b>（本系统的核心接口，<b>只算不存</b>）</li>
 *   <li>GET    /api/recipes/{id} —— 配方详情（含配料明细，校验归属）</li>
 *   <li>POST   /api/recipes —— 新建配方（含配料明细）</li>
 *   <li>PUT    /api/recipes/{id} —— 修改配方（明细整体替换，版本号 +1）</li>
 *   <li>DELETE /api/recipes/{id} —— 软删除配方</li>
 * </ul>
 */
@WebServlet("/api/recipes/*")
public class RecipeServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        // 必须先判断字面量路径再取编号：pathId() 只识别数字段，
        // /api/recipes/profiles 会被它判成「没有编号」而落进列表分支。
        if ("profiles".equals(pathSegment(request, 0))) {
            handle(request, response, () -> Services.recipe().profiles(requireUserId(request)));
            return;
        }
        Long id = pathId(request);
        if (id == null) {
            handle(request, response, () -> Services.recipe().page(
                    requireUserId(request),
                    param(request, "keyword", null),
                    intParam(request, "page", 1),
                    intParam(request, "pageSize", 10)));
            return;
        }
        // /api/recipes/{id}/compose —— 配方合成：给出目标，拿到可执行的新配方。
        // 用 GET 而不是 POST：它不创建任何记录（幂等），参数少且便于在浏览器里直接验证。
        if ("compose".equals(pathSegment(request, 1))) {
            handle(request, response, () -> Services.recipeCompose().compose(
                    requireUserId(request),
                    id,
                    param(request, "goalType", null),
                    parseLong(param(request, "regionProfileId", null)),
                    splitList(param(request, "allergens", null))));
            return;
        }
        handle(request, response, () -> Services.recipe().detail(requireUserId(request), id));
    }

    /**
     * 解析可空的数字参数，非法值统一按“未提供”处理。
     *
     * @param text 原文本
     * @return 数值；为空或非法时返回 null
     */
    private Long parseLong(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 拆分逗号分隔的清单，同时兼容中文逗号、顿号、分号与空格。
     *
     * @param text 原文本
     * @return 去空后的清单
     */
    private List<String> splitList(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }
        for (String part : text.split("[,，、;；\\s]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            Long userId = requireUserId(request);
            Recipe recipe = readBody(request, Recipe.class);
            if (recipe.getId() != null) {
                throw BizException.badRequest("新建配方不需要传编号");
            }
            return Services.recipe().create(userId, recipe);
        });
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            Long userId = requireUserId(request);
            Long id = pathId(request);
            if (id == null) {
                throw BizException.badRequest("缺少配方编号");
            }
            Recipe recipe = readBody(request, Recipe.class);
            recipe.setId(id);
            return Services.recipe().update(userId, recipe);
        });
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            Long userId = requireUserId(request);
            Long id = pathId(request);
            if (id == null) {
                throw BizException.badRequest("缺少配方编号");
            }
            Services.recipe().delete(userId, id);
            return null;
        });
    }
}
