package com.flavorlogic.servlet;

import com.flavorlogic.model.Ingredient;
import com.flavorlogic.service.Services;
import com.flavorlogic.util.BizException;
import com.flavorlogic.util.OperationLog;
import com.flavorlogic.util.PageResult;
import com.flavorlogic.util.TextUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 管理端食材属性接口（全部需要管理员身份）。
 *
 * <p>对应接口路径：
 * <ul>
 *   <li>{@code GET /api/admin/ingredients}：食材分页查询（含停用）；</li>
 *   <li>{@code GET /api/admin/ingredients/categories}：食材分类下拉数据；</li>
 *   <li>{@code POST /api/admin/ingredients}：新增食材；</li>
 *   <li>{@code PUT /api/admin/ingredients/{id}}：修改食材；</li>
 *   <li>{@code DELETE /api/admin/ingredients/{id}}：停用食材（status=0，不做物理删除）。</li>
 * </ul>
 * 八维属性取值范围与名称唯一性由 Service 校验，已产生的分析结果不受停用影响。</p>
 */
@WebServlet("/api/admin/ingredients/*")
public class AdminIngredientServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /** 停用状态值 */
    private static final int STATUS_DISABLED = 0;

    /**
     * 食材分页或分类下拉。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            // 后台分类下拉数据：/api/admin/ingredients/categories
            if ("categories".equals(pathSegment(request, 0))) {
                return Services.meta().ingredientCategories();
            }
            String keyword = param(request, "keyword", null);
            String category = param(request, "category", null);
            // status 缺省为 null，表示不按状态筛选
            Integer status = integerParam(request, "status");
            int page = intParam(request, "page", 1);
            int pageSize = intParam(request, "pageSize", 10);
            PageResult<Ingredient> result =
                    Services.meta().adminPageIngredients(keyword, category, status, page, pageSize);
            return result;
        });
    }

    /**
     * 新增食材。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            Ingredient ingredient = readBody(request, Ingredient.class);
            Ingredient saved = Services.meta().adminCreateIngredient(ingredient);
            OperationLog.record(request, "新增食材", "食材#" + saved.getId()
                    + TextUtil.defaultIfBlank(saved.getName(), ""), "成功");
            return saved;
        });
    }

    /**
     * 修改食材属性：以路径编号为准。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            requireAdmin(request);
            Long id = pathId(request);
            if (id == null) {
                throw BizException.badRequest("缺少食材编号");
            }
            Ingredient ingredient = readBody(request, Ingredient.class);
            // 路径编号优先，避免请求体编号与路径不一致导致改错数据
            ingredient.setId(id);
            Ingredient saved = Services.meta().adminUpdateIngredient(ingredient);
            OperationLog.record(request, "修改食材", "食材#" + id
                    + TextUtil.defaultIfBlank(saved.getName(), ""), "成功");
            return saved;
        });
    }

    /**
     * 停用食材：置 status=0，历史分析结果继续引用该食材的属性快照。
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
                throw BizException.badRequest("缺少食材编号");
            }
            Services.meta().adminUpdateIngredientStatus(id, STATUS_DISABLED);
            OperationLog.record(request, "停用食材", "食材#" + id, "成功");
            return null;
        });
    }
}
