package com.flavorlogic.service;

import com.flavorlogic.service.impl.AnalysisServiceImpl;
import com.flavorlogic.service.impl.ArticleServiceImpl;
import com.flavorlogic.service.impl.CrawlJobServiceImpl;
import com.flavorlogic.service.impl.EvidenceClaimServiceImpl;
import com.flavorlogic.service.impl.IngredientAttributeVersionServiceImpl;
import com.flavorlogic.service.impl.RuleEvidenceServiceImpl;
import com.flavorlogic.service.impl.DashboardServiceImpl;
import com.flavorlogic.service.impl.GoalTemplateServiceImpl;
import com.flavorlogic.service.impl.MetaServiceImpl;
import com.flavorlogic.service.impl.RecipeComposeServiceImpl;
import com.flavorlogic.service.impl.RecipeServiceImpl;
import com.flavorlogic.service.impl.SourceDocumentServiceImpl;
import com.flavorlogic.service.impl.SourceSiteServiceImpl;
import com.flavorlogic.service.impl.UserServiceImpl;

/**
 * Service 单例工厂：Servlet 层通过本类获取业务对象，避免在每次请求中重复创建。
 *
 * <p>Service 内部不持有可变状态（无状态设计），因此单例是线程安全的。</p>
 */
public final class Services {

    private static final UserService USER = new UserServiceImpl();
    private static final ArticleService ARTICLE = new ArticleServiceImpl();
    private static final MetaService META = new MetaServiceImpl();
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

    /**
     * 基础数据业务。
     *
     * @return MetaService
     */
    public static MetaService meta() {
        return META;
    }

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
