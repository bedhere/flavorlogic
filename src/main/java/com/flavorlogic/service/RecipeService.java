package com.flavorlogic.service;

import com.flavorlogic.model.Recipe;
import com.flavorlogic.model.RecipeProfile;
import com.flavorlogic.model.RecipeSnapshot;
import com.flavorlogic.util.PageResult;

import java.util.List;

/**
 * 配方管理业务接口。
 */
public interface RecipeService {

    /**
     * 新建配方（含全部配料明细）。
     *
     * @param userId 所属用户
     * @param recipe 配方与明细
     * @return 新建后的配方
     */
    Recipe create(Long userId, Recipe recipe);

    /**
     * 修改配方（明细整体替换，版本号 +1）。
     *
     * @param userId 当前用户
     * @param recipe 配方与明细（需含编号）
     * @return 更新后的配方
     */
    Recipe update(Long userId, Recipe recipe);

    /**
     * 分页查询当前用户的配方。
     *
     * @param userId   用户编号
     * @param keyword  名称关键字
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<Recipe> page(Long userId, String keyword, int page, int pageSize);

    /**
     * 配方详情（含归属校验）。
     *
     * @param userId   当前用户
     * @param recipeId 配方编号
     * @return 配方详情
     */
    Recipe detail(Long userId, Long recipeId);

    /**
     * 软删除配方。
     *
     * @param userId   当前用户
     * @param recipeId 配方编号
     */
    void delete(Long userId, Long recipeId);

    /**
     * 最近修改的配方。
     *
     * @param userId 用户编号
     * @param limit  条数
     * @return 配方列表
     */
    List<Recipe> recent(Long userId, int limit);

    /**
     * 用户有效配方数。
     *
     * @param userId 用户编号
     * @return 配方数
     */
    long countByUser(Long userId);

    /**
     * 由配方生成分析用快照（固化食材八维属性，保证历史结果可复现）。
     *
     * @param recipe 配方（含明细）
     * @return 配方快照
     */
    RecipeSnapshot buildSnapshot(Recipe recipe);

    /**
     * 计算当前用户全部有效配方的风味画像。
     *
     * <p><b>只算不存</b>：不创建分析任务、不写分析历史。用于配方库列表的风味标签
     * 与「查看画像」弹窗。口径与正式分析一致（同一个 {@code FlavorEngine}），
     * 因此列表里的画像与结果页顶部的「味觉打分」必然相同。</p>
     *
     * @param userId 用户编号
     * @return 画像列表，按配方编号升序；单次最多返回 200 条
     */
    List<RecipeProfile> profiles(Long userId);
}
