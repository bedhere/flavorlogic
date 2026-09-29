package com.flavorlogic.service;

import com.flavorlogic.model.ComposedRecipe;

import java.util.List;

/**
 * 配方合成业务接口：把「研发目标」变成一份<b>可执行的新配方</b>。
 *
 * <p>这是本系统的核心入口（见《项目介绍》4.1 第（5）项）。它编排四件事：
 * 取基准配方快照 → 展开目标向量 → 建立知识卡索引 → 交给 {@code RecipeComposer} 合成。</p>
 */
public interface RecipeComposeService {

    /**
     * 依据研发目标合成一份新配方。
     *
     * <p><b>只算不存</b>：不创建分析任务、不写分析历史。用户确认后再由「采用为新配方」
     * 走 {@code RecipeService.create()} 落库——生成过程本身不产生记录。</p>
     *
     * @param userId          当前用户
     * @param recipeId        基准配方编号
     * @param goalType        研发目标类型（{@code AnalysisTask.GOAL_*}）
     * @param regionProfileId 目标区域编号，可为 null
     * @param allergens       过敏原/禁用清单，可为 null
     * @return 合成结果：新配料表、改动清单、逐维达标情况与提示
     */
    ComposedRecipe compose(Long userId, Long recipeId, String goalType,
                           Long regionProfileId, List<String> allergens);
}
