package com.flavorlogic.service;

import com.flavorlogic.model.FlavorRule;
import com.flavorlogic.model.Ingredient;
import com.flavorlogic.model.RegionProfile;
import com.flavorlogic.util.PageResult;

import java.util.List;

/**
 * 基础数据（食材属性、区域画像、风味规则）业务接口。
 */
public interface MetaService {

    /**
     * 启用中的食材列表（分析页下拉与属性展示使用）。
     *
     * @return 食材列表
     */
    List<Ingredient> listEnabledIngredients();

    /**
     * 全部食材分类。
     *
     * @return 分类名称列表
     */
    List<String> ingredientCategories();

    /**
     * 管理端分页查询食材。
     *
     * @param keyword  名称关键字
     * @param category 分类筛选
     * @param status   状态筛选
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<Ingredient> adminPageIngredients(String keyword, String category, Integer status, int page, int pageSize);

    /**
     * 管理端新增食材。
     *
     * @param ingredient 食材
     * @return 新增后的食材
     */
    Ingredient adminCreateIngredient(Ingredient ingredient);

    /**
     * 管理端修改食材属性。
     *
     * @param ingredient 食材（需含编号）
     * @return 更新后的食材
     */
    Ingredient adminUpdateIngredient(Ingredient ingredient);

    /**
     * 管理端启用/停用食材。
     *
     * @param id     食材编号
     * @param status 1 启用，0 停用
     */
    void adminUpdateIngredientStatus(Long id, int status);

    /**
     * 食材总数。
     *
     * @return 食材总数
     */
    long countIngredients();

    /**
     * 启用中的食材数。
     *
     * @return 启用食材数
     */
    long countEnabledIngredients();

    /**
     * 启用中的区域画像。
     *
     * @return 区域列表
     */
    List<RegionProfile> listRegions();

    /**
     * 启用中的风味规则（演示“建议为什么产生”使用）。
     *
     * @return 规则列表
     */
    List<FlavorRule> listRules();

    /**
     * 管理端分页查询规则。
     *
     * @param keyword  关键字
     * @param goalType 目标类型筛选
     * @param status   状态筛选
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<FlavorRule> adminPageRules(String keyword, String goalType, Integer status, int page, int pageSize);

    /**
     * 管理端修改规则。
     *
     * @param rule 规则（需含编号）
     * @return 更新后的规则
     */
    FlavorRule adminUpdateRule(FlavorRule rule);

    /**
     * 管理端启用/停用规则。
     *
     * @param id     规则编号
     * @param status 1 启用，0 停用
     */
    void adminUpdateRuleStatus(Long id, int status);
}
