package com.flavorlogic.common.service;
import com.flavorlogic.module.ingredient.model.Ingredient;

import com.flavorlogic.module.analysis.service.AnalysisService;
import com.flavorlogic.module.article.service.ArticleService;
import com.flavorlogic.module.knowledge.service.CrawlJobService;
import com.flavorlogic.module.dashboard.service.DashboardService;
import com.flavorlogic.module.knowledge.service.EvidenceClaimService;
import com.flavorlogic.module.recipe.service.GoalTemplateService;
import com.flavorlogic.module.knowledge.service.IngredientAttributeVersionService;
import com.flavorlogic.module.ingredient.service.IngredientService;
import com.flavorlogic.module.recipe.service.RecipeComposeService;
import com.flavorlogic.module.recipe.service.RecipeService;
import com.flavorlogic.module.region.service.RegionService;
import com.flavorlogic.module.knowledge.service.RuleEvidenceService;
import com.flavorlogic.module.analysis.service.RuleService;
import com.flavorlogic.module.knowledge.service.SourceDocumentService;
import com.flavorlogic.module.knowledge.service.SourceSiteService;
import com.flavorlogic.module.user.service.UserService;
import com.flavorlogic.module.analysis.service.impl.AnalysisServiceImpl;
import com.flavorlogic.module.article.service.impl.ArticleServiceImpl;
import com.flavorlogic.module.knowledge.service.impl.CrawlJobServiceImpl;
import com.flavorlogic.module.knowledge.service.impl.EvidenceClaimServiceImpl;
import com.flavorlogic.module.knowledge.service.impl.IngredientAttributeVersionServiceImpl;
import com.flavorlogic.module.knowledge.service.impl.RuleEvidenceServiceImpl;
import com.flavorlogic.module.dashboard.service.impl.DashboardServiceImpl;
import com.flavorlogic.module.recipe.service.impl.GoalTemplateServiceImpl;
import com.flavorlogic.module.ingredient.service.impl.IngredientServiceImpl;
import com.flavorlogic.module.region.service.impl.RegionServiceImpl;
import com.flavorlogic.module.analysis.service.impl.RuleServiceImpl;
import com.flavorlogic.module.recipe.service.impl.RecipeComposeServiceImpl;
import com.flavorlogic.module.recipe.service.impl.RecipeServiceImpl;
import com.flavorlogic.module.knowledge.service.impl.SourceDocumentServiceImpl;
import com.flavorlogic.module.knowledge.service.impl.SourceSiteServiceImpl;
import com.flavorlogic.module.user.service.impl.UserServiceImpl;

/**
 * Service 单例工厂：Servlet 层通过本类获取业务对象，避免在每次请求中重复创建。
 *
 * <p>Service 内部不持有可变状态（无状态设计），因此单例是线程安全的。</p>
 */
public final class Services {

    private static final UserService USER = new UserServiceImpl();
    private static final ArticleService ARTICLE = new ArticleServiceImpl();
    private static final IngredientService INGREDIENT = new IngredientServiceImpl();
    private static final RegionService REGION = new RegionServiceImpl();
    private static final RuleService RULE = new RuleServiceImpl();
    private static final RecipeService RECIPE = new RecipeServiceImpl();
    private static final AnalysisService ANALYSIS = new AnalysisServiceImpl();
    private static final DashboardService DASHBOARD = new DashboardServiceImpl();
    private static final GoalTemplateService GOAL_TEMPLATE = new GoalTemplateServiceImpl();
    private static final RecipeComposeService RECIPE_COMPOSE = new RecipeComposeServiceImpl();
    private static final SourceDocumentService SOURCE_DOCUMENT = new SourceDocumentServiceImpl();
    private static final SourceSiteService SOURCE_SITE = new SourceSiteServiceImpl();
    private static final CrawlJobService CRAWL_JOB = new CrawlJobServiceImpl();
    private static final EvidenceClaimService EVIDENCE_CLAIM = new EvidenceClaimServiceImpl();
    private static final IngredientAttributeVersionService ATTRIBUTE_VERSION = new IngredientAttributeVersionServiceImpl();
    private static final RuleEvidenceService RULE_EVIDENCE = new RuleEvidenceServiceImpl();

    private Services() {
    }

    /**
     * 用户与权限业务。
     *
     * @return UserService
     */
    public static UserService user() {
        return USER;
    }

    /**
     * 知识库业务。
     *
     * @return ArticleService
     */
    public static ArticleService article() {
        return ARTICLE;
    }

    /** Ingredient 业务。 */
    public static IngredientService ingredient() { return INGREDIENT; }
    /** Region 业务。 */
    public static RegionService region() { return REGION; }
    /** Rule 业务。 */
    public static RuleService rule() { return RULE; }

    /**
     * 配方业务。
     *
     * @return RecipeService
     */
    public static RecipeService recipe() {
        return RECIPE;
    }

    /**
     * 风味分析业务。
     *
     * @return AnalysisService
     */
    public static AnalysisService analysis() {
        return ANALYSIS;
    }

    /**
     * 工作台业务。
     *
     * @return DashboardService
     */
    public static DashboardService dashboard() {
        return DASHBOARD;
    }

    /**
     * 目标模板业务：把研发目标展开为目标向量（配方生成器的输入契约）。
     *
     * @return GoalTemplateService
     */
    public static GoalTemplateService goalTemplate() {
        return GOAL_TEMPLATE;
    }

    /**
     * 配方合成业务：把研发目标变成一份可执行的新配方（本系统的核心入口）。
     *
     * @return RecipeComposeService
     */
    public static RecipeComposeService recipeCompose() {
        return RECIPE_COMPOSE;
    }

    /**
     * 外部来源文档业务：导入审核队列、审核与本地全文检索。
     *
     * @return SourceDocumentService
     */
    public static SourceDocumentService sourceDocument() {
        return SOURCE_DOCUMENT;
    }

    /**
     * 外部来源站点白名单业务。
     *
     * @return SourceSiteService
     */
    public static SourceSiteService sourceSite() {
        return SOURCE_SITE;
    }

    /**
     * 受控资料采集任务业务。
     *
     * @return CrawlJobService
     */
    public static CrawlJobService crawlJob() {
        return CRAWL_JOB;
    }

    /**
     * 证据声明业务。
     *
     * @return EvidenceClaimService
     */
    public static EvidenceClaimService evidenceClaim() {
        return EVIDENCE_CLAIM;
    }

    /**
     * 食材属性版本业务。
     *
     * @return IngredientAttributeVersionService
     */
    public static IngredientAttributeVersionService attributeVersion() {
        return ATTRIBUTE_VERSION;
    }

    /**
     * 规则/知识卡证据关联业务。
     *
     * @return RuleEvidenceService
     */
    public static RuleEvidenceService ruleEvidence() {
        return RULE_EVIDENCE;
    }
}
