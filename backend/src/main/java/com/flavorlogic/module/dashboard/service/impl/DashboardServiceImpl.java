package com.flavorlogic.module.dashboard.service.impl;
import com.flavorlogic.module.user.service.impl.UserServiceImpl;
import com.flavorlogic.module.recipe.service.impl.RecipeServiceImpl;
import com.flavorlogic.module.article.service.impl.ArticleServiceImpl;
import com.flavorlogic.module.analysis.service.impl.AnalysisServiceImpl;

import com.flavorlogic.module.analysis.model.AnalysisTask;
import com.flavorlogic.module.analysis.service.AnalysisService;
import com.flavorlogic.module.article.service.ArticleService;
import com.flavorlogic.module.dashboard.service.DashboardService;
import com.flavorlogic.module.ingredient.service.IngredientService;
import com.flavorlogic.module.ingredient.service.impl.IngredientServiceImpl;
import com.flavorlogic.module.region.service.RegionService;
import com.flavorlogic.module.region.service.impl.RegionServiceImpl;
import com.flavorlogic.module.analysis.service.RuleService;
import com.flavorlogic.module.analysis.service.impl.RuleServiceImpl;
import com.flavorlogic.module.recipe.service.RecipeService;
import com.flavorlogic.module.user.service.UserService;
import com.flavorlogic.common.util.OperationLog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 研发工作台业务实现：把配方、分析、知识库与管理统计汇总为一个响应。
 *
 * <p>工作台只做汇总与跳转，不承担复杂编辑，因此这里只做只读查询。</p>
 */
public class DashboardServiceImpl implements DashboardService {

    private final RecipeService recipeService = new RecipeServiceImpl();
    private final AnalysisService analysisService = new AnalysisServiceImpl();
    private final ArticleService articleService = new ArticleServiceImpl();
    private final IngredientService ingredientService = new IngredientServiceImpl();
    private final RegionService regionService = new RegionServiceImpl();
    private final RuleService ruleService = new RuleServiceImpl();
    private final UserService userService = new UserServiceImpl();

    @Override
    public Map<String, Object> overview(Long userId, boolean admin) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recipeCount", recipeService.countByUser(userId));
        data.put("taskCount", analysisService.countByUser(userId));
        data.put("completedCount", analysisService.countCompletedByUser(userId));
        data.put("lastAnalysisAt", analysisService.lastAnalysisAt(userId));
        data.put("recentRecipes", recipeService.recent(userId, 5));

        List<AnalysisTask> recentTasks = analysisService.history(userId, null, null, null, 1, 5).getList();
        data.put("recentTasks", recentTasks);
        data.put("latestArticles", articleService.recent(5));
        data.put("ingredientCount", ingredientService.countEnabledIngredients());
        data.put("regionCount", regionService.listRegions().size());
        data.put("ruleCount", ruleService.listRules().size());
        data.put("articleCount", articleService.countPublished());
        data.put("isAdmin", admin);

        if (admin) {
            Map<String, Object> platform = new LinkedHashMap<>();
            platform.put("userCount", userService.countAll());
            platform.put("taskCountAll", analysisService.countAll());
            platform.put("ingredientCountAll", ingredientService.countIngredients());
            platform.put("articleCountAll", articleService.countAll());
            data.put("platform", platform);
            data.put("operationLogs", OperationLog.tail(15));
        }
        return data;
    }
}
