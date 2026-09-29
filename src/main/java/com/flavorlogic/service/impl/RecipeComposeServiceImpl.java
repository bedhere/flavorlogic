package com.flavorlogic.service.impl;

import com.flavorlogic.dao.FlavorKnowledgeDao;
import com.flavorlogic.dao.IngredientDao;
import com.flavorlogic.dao.RegionProfileDao;
import com.flavorlogic.dao.impl.FlavorKnowledgeDaoImpl;
import com.flavorlogic.dao.impl.IngredientDaoImpl;
import com.flavorlogic.dao.impl.RegionProfileDaoImpl;
import com.flavorlogic.engine.FlavorEngine;
import com.flavorlogic.engine.KnowledgeIndex;
import com.flavorlogic.engine.RecipeComposer;
import com.flavorlogic.model.AnalysisConstraints;
import com.flavorlogic.model.ComposedRecipe;
import com.flavorlogic.model.FlavorKnowledge;
import com.flavorlogic.model.FlavorVector;
import com.flavorlogic.model.GoalVector;
import com.flavorlogic.model.Ingredient;
import com.flavorlogic.model.Recipe;
import com.flavorlogic.model.RecipeSnapshot;
import com.flavorlogic.model.RegionProfile;
import com.flavorlogic.service.BaseService;
import com.flavorlogic.service.GoalTemplateService;
import com.flavorlogic.service.RecipeComposeService;
import com.flavorlogic.service.RecipeService;
import com.flavorlogic.util.BizException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 配方合成业务实现：负责把各块数据取齐，交给 {@code RecipeComposer} 完成算法。
 *
 * <p>本类只做编排与数据加载，不含任何合成规则——规则分别落在
 * {@code goal_target}（目标语义）、{@code flavor_knowledge}（候选与用量区间）
 * 与 {@code RecipeComposer}（合成步骤）里。</p>
 */
public class RecipeComposeServiceImpl extends BaseService implements RecipeComposeService {

    private static final Logger LOG = Logger.getLogger(RecipeComposeServiceImpl.class.getName());

    private final RecipeService recipeService = new RecipeServiceImpl();
    private final GoalTemplateService goalTemplateService = new GoalTemplateServiceImpl();
    private final RegionProfileDao regionDao = new RegionProfileDaoImpl();
    private final FlavorKnowledgeDao knowledgeDao = new FlavorKnowledgeDaoImpl();
    private final IngredientDao ingredientDao = new IngredientDaoImpl();

    private final FlavorEngine flavorEngine = new FlavorEngine();
    private final RecipeComposer composer = new RecipeComposer();

    @Override
    public ComposedRecipe compose(Long userId, Long recipeId, String goalType,
                                  Long regionProfileId, List<String> allergens) {
        if (recipeId == null) {
            throw BizException.badRequest("缺少配方编号");
        }
        // 基准快照与风味向量：合成器需要它作为起点与偏差判断的参照
        Recipe recipe = recipeService.detail(userId, recipeId);
        RecipeSnapshot baseline = recipeService.buildSnapshot(recipe);
        RegionProfile region = loadRegion(regionProfileId);

        FlavorVector baselineVector = flavorEngine.compute(baseline);
        GoalVector goal = goalTemplateService.expand(goalType, baselineVector, region);

        AnalysisConstraints constraints = new AnalysisConstraints();
        constraints.setAllergens(allergens == null ? new ArrayList<>() : new ArrayList<>(allergens));

        Map<String, List<FlavorKnowledge>> cardIndex = KnowledgeIndex.build(
                loadKnowledge(), goal.getGoalType(), constraints);
        Map<String, Ingredient> ingredientByName = loadIngredientIndex();

        ComposedRecipe result = composer.compose(baseline, goal, cardIndex, ingredientByName);
        result.setGoalType(goal.getGoalType());
        return result;
    }

    /**
     * 加载目标区域画像。
     *
     * @param regionProfileId 区域编号，可为 null
     * @return 区域画像；未指定时为 null
     */
    private RegionProfile loadRegion(Long regionProfileId) {
        if (regionProfileId == null) {
            return null;
        }
        RegionProfile region = read(conn -> regionDao.findById(conn, regionProfileId));
        if (region == null) {
            throw BizException.badRequest("目标区域不存在或已停用");
        }
        return region;
    }

    /**
     * 加载启用中的知识卡。与风析流程保持一致：知识库是<b>增强项而非必需项</b>，
     * 表不存在时降级为空集合，合成照常进行（只是没有可用的候选与区间）。
     *
     * @return 知识卡列表；加载失败时返回空列表
     */
    private List<FlavorKnowledge> loadKnowledge() {
        try {
            return read(knowledgeDao::listEnabled);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "知识卡加载失败（可能未执行知识库建表脚本），本次合成没有可用的候选与用量区间", e);
            return new ArrayList<>();
        }
    }

    /**
     * 加载启用中的食材并按名称索引。合成器需要它把知识卡命中的候选加入配方——
     * 候选通常不在基准配方里，因此拿不到属性，必须回食材库取。
     *
     * @return 食材名称 → 食材
     */
    private Map<String, Ingredient> loadIngredientIndex() {
        Map<String, Ingredient> index = new LinkedHashMap<>();
        for (Ingredient ingredient : read(ingredientDao::listEnabled)) {
            index.put(ingredient.getName(), ingredient);
        }
        return index;
    }
}
