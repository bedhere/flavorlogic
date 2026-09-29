package com.flavorlogic.service.impl;

import com.flavorlogic.dao.AnalysisFeedbackDao;
import com.flavorlogic.dao.AnalysisResultDao;
import com.flavorlogic.dao.AnalysisTaskDao;
import com.flavorlogic.dao.FlavorKnowledgeDao;
import com.flavorlogic.dao.FlavorRuleDao;
import com.flavorlogic.dao.IngredientDao;
import com.flavorlogic.dao.RegionProfileDao;
import com.flavorlogic.dao.impl.AnalysisFeedbackDaoImpl;
import com.flavorlogic.dao.impl.AnalysisResultDaoImpl;
import com.flavorlogic.dao.impl.AnalysisTaskDaoImpl;
import com.flavorlogic.dao.impl.FlavorKnowledgeDaoImpl;
import com.flavorlogic.dao.impl.FlavorRuleDaoImpl;
import com.flavorlogic.dao.impl.IngredientDaoImpl;
import com.flavorlogic.dao.impl.RegionProfileDaoImpl;
import com.flavorlogic.engine.AnalysisEngine;
import com.flavorlogic.model.AnalysisConstraints;
import com.flavorlogic.model.AnalysisFeedback;
import com.flavorlogic.model.AnalysisOutcome;
import com.flavorlogic.model.AnalysisRequest;
import com.flavorlogic.model.AnalysisResult;
import com.flavorlogic.model.AnalysisTask;
import com.flavorlogic.model.FlavorDelta;
import com.flavorlogic.model.FlavorKnowledge;
import com.flavorlogic.model.FlavorRule;
import com.flavorlogic.model.Ingredient;
import com.flavorlogic.model.Recipe;
import com.flavorlogic.model.RecipeItem;
import com.flavorlogic.model.RecipeSnapshot;
import com.flavorlogic.model.RegionProfile;
import com.flavorlogic.model.Suggestion;
import com.flavorlogic.service.AnalysisService;
import com.flavorlogic.service.BaseService;
import com.flavorlogic.service.RecipeService;
import com.flavorlogic.util.BizException;
import com.flavorlogic.util.JsonUtil;
import com.flavorlogic.util.PageResult;
import com.flavorlogic.util.TextUtil;
import com.flavorlogic.util.ValidationUtil;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 风味分析业务实现：项目主功能。
 *
 * <p>执行流程（见项目介绍 15.1）：</p>
 * <ol>
 *   <li>校验参数并载入基准配方（含归属校验）；</li>
 *   <li>生成基准快照与目标快照，快照中固化食材八维属性；</li>
 *   <li>任务以 PENDING 落库并提交，保证失败任务也能在历史中看到；</li>
 *   <li>流转 RUNNING，调用规则引擎计算，写入结果并流转 COMPLETED；</li>
 *   <li>任一步骤失败则流转 FAILED 并记录失败原因。</li>
 * </ol>
 */
public class AnalysisServiceImpl extends BaseService implements AnalysisService {

    private static final Logger LOG = Logger.getLogger(AnalysisServiceImpl.class.getName());

    private static final Type DELTA_LIST_TYPE = new TypeToken<List<FlavorDelta>>() { }.getType();
    private static final Type SUGGESTION_LIST_TYPE = new TypeToken<List<Suggestion>>() { }.getType();
    private static final int MAX_TARGET_ITEMS = 50;

    private static final Set<String> GOAL_TYPES = new LinkedHashSet<>(java.util.Arrays.asList(
            AnalysisTask.GOAL_REPLACE, AnalysisTask.GOAL_REDUCE_SUGAR, AnalysisTask.GOAL_REDUCE_FAT,
            AnalysisTask.GOAL_ADJUST_STIMULATION, AnalysisTask.GOAL_REGION_ADAPT, AnalysisTask.GOAL_GENERAL));

    private final AnalysisTaskDao taskDao = new AnalysisTaskDaoImpl();
    private final AnalysisResultDao resultDao = new AnalysisResultDaoImpl();
    private final AnalysisFeedbackDao feedbackDao = new AnalysisFeedbackDaoImpl();
    private final IngredientDao ingredientDao = new IngredientDaoImpl();
    private final RegionProfileDao regionDao = new RegionProfileDaoImpl();
    private final FlavorRuleDao ruleDao = new FlavorRuleDaoImpl();
    private final FlavorKnowledgeDao knowledgeDao = new FlavorKnowledgeDaoImpl();
    private final RecipeService recipeService = new RecipeServiceImpl();
    private final AnalysisEngine engine = new AnalysisEngine();

    @Override
    public AnalysisTask analyze(Long userId, AnalysisRequest request) {
        if (userId == null) {
            throw BizException.unauthorized("请先登录后再发起分析");
        }
        if (request == null || request.getRecipeId() == null) {
            throw BizException.badRequest("请选择需要分析的基准配方");
        }
        String goalType = ValidationUtil.requireOneOf(
                TextUtil.defaultIfBlank(request.getGoalType(), AnalysisTask.GOAL_GENERAL),
                "分析类型", GOAL_TYPES.toArray(new String[0]));

        Recipe recipe = recipeService.detail(userId, request.getRecipeId());
        RecipeSnapshot baseline = recipeService.buildSnapshot(recipe);
        RecipeSnapshot target = buildTargetSnapshot(recipe, request.getTargetItems());
        // 目标与基准完全相同 → 八维偏移必然全为 0、也不会命中任何规则，
        // 结果页只会展示一排「稳定」。这种记录没有任何信息量，却会挤占分析历史，
        // 因此在这里直接拒绝，并明确告诉用户该改什么。
        //
        // 注意：第二阶段引入结构化工艺参数后，此校验必须一并比较工艺参数 ——
        // 配料不变但火候改变的对比是有意义的分析，不能被拦下。
        if (sameRecipeItems(baseline, target)) {
            throw BizException.badRequest("目标方案与基准配方完全相同，本次分析不会产生任何差异。"
                    + "请至少调整一项配料（改用量、增减配料或替换食材）后再提交；"
                    + "如果你只是想了解这份配方自身是什么味道，可以到「配方库」点击它的风味画像。");
        }
        RegionProfile region = loadRegion(request.getRegionProfileId());

        AnalysisConstraints constraints = request.getConstraints() == null
                ? new AnalysisConstraints() : request.getConstraints();
        if (constraints.getCostLimit() != null && constraints.getCostLimit().signum() < 0) {
            throw BizException.badRequest("成本上限不能为负数");
        }
        if (TextUtil.isBlank(constraints.getHealthGoal())) {
            constraints.setHealthGoal("NONE");
        }

        AnalysisTask task = new AnalysisTask();
        task.setUserId(userId);
        task.setRecipeId(recipe.getId());
        task.setRegionProfileId(region == null ? null : region.getId());
        task.setGoalType(goalType);
        task.setTargetRegion(region == null ? null : region.getRegionName());
        task.setTaskName(TextUtil.truncate(TextUtil.defaultIfBlank(request.getTaskName(),
                recipe.getName() + " · " + task.goalText()), 100));
        task.setConstraintJson(JsonUtil.toJson(constraints));
        task.setBaselineSnapshotJson(JsonUtil.toJson(baseline));
        task.setTargetSnapshotJson(JsonUtil.toJson(target));
        task.setConstraints(constraints);
        task.setBaselineSnapshot(baseline);
        task.setTargetSnapshot(target);

        Long taskId = write(conn -> taskDao.insert(conn, task));

        try {
            writeVoid(conn -> taskDao.markRunning(conn, taskId));
            List<FlavorRule> rules = read(conn -> ruleDao.listEnabledByGoal(conn, goalType));
            // 知识卡一次全量加载后在引擎内按「维度 + 目标类型」建索引：
            // 课设阶段仅数十条，比按维度逐条查库更省连接开销
            List<FlavorKnowledge> knowledge = loadKnowledge();
            AnalysisOutcome outcome = engine.analyze(task, baseline, target, region, rules, knowledge);
            AnalysisResult result = toEntity(taskId, outcome);
            writeVoid(conn -> {
                resultDao.insert(conn, result);
                taskDao.markCompleted(conn, taskId);
            });
        } catch (RuntimeException e) {
            String message = e instanceof BizException ? e.getMessage() : "分析过程发生异常，请稍后重试";
            try {
                writeVoid(conn -> taskDao.markFailed(conn, taskId, TextUtil.truncate(message, 480)));
            } catch (RuntimeException logError) {
                LOG.log(Level.WARNING, "标记任务失败状态时出错：taskId=" + taskId, logError);
            }
            if (e instanceof BizException) {
                throw e;
            }
            LOG.log(Level.SEVERE, "分析引擎执行失败：taskId=" + taskId, e);
            throw new BizException(500, message);
        }
        return detail(userId, taskId);
    }

    @Override
    public PageResult<AnalysisTask> history(Long userId, String keyword, String goalType, String status,
                                            int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        String trimmedKeyword = TextUtil.isBlank(keyword) ? null : keyword.trim();
        String trimmedGoal = TextUtil.isBlank(goalType) ? null : goalType.trim();
        String trimmedStatus = TextUtil.isBlank(status) ? null : status.trim();
        return read(conn -> {
            long total = taskDao.countByUser(conn, userId, trimmedKeyword, trimmedGoal, trimmedStatus);
            List<AnalysisTask> list = total == 0
                    ? Collections.emptyList()
                    : taskDao.pageByUser(conn, userId, trimmedKeyword, trimmedGoal, trimmedStatus,
                            (safePage - 1) * safeSize, safeSize);
            return new PageResult<>(list, total, safePage, safeSize);
        });
    }

    @Override
    public AnalysisTask detail(Long userId, Long taskId) {
        if (taskId == null) {
            throw BizException.badRequest("缺少分析任务编号");
        }
        return read(conn -> {
            AnalysisTask task = taskDao.findDetailById(conn, taskId);
            if (task == null) {
                throw BizException.notFound("分析任务不存在");
            }
            if (userId != null && !userId.equals(task.getUserId())) {
                throw BizException.forbidden("无权查看他人的分析记录");
            }
            task.setConstraints(parse(request -> JsonUtil.gson().fromJson(request, AnalysisConstraints.class),
                    task.getConstraintJson()));
            task.setBaselineSnapshot(parse(request -> JsonUtil.gson().fromJson(request, RecipeSnapshot.class),
                    task.getBaselineSnapshotJson()));
            task.setTargetSnapshot(parse(request -> JsonUtil.gson().fromJson(request, RecipeSnapshot.class),
                    task.getTargetSnapshotJson()));

            AnalysisResult result = resultDao.findByTaskId(conn, taskId);
            if (result != null) {
                result.setBaseline(parseBaseline(result.getBaselineJson()));
                result.setTarget(parseBaseline(result.getTargetJson()));
                result.setDeltas(parseList(result.getDeltaJson(), DELTA_LIST_TYPE));
                result.setSuggestions(parseList(result.getSuggestionJson(), SUGGESTION_LIST_TYPE));
                task.setResult(result);
            }
            if (userId != null) {
                task.setFeedback(feedbackDao.findByTaskAndUser(conn, taskId, userId));
            }
            return task;
        });
    }

    @Override
    public void delete(Long userId, Long taskId) {
        if (taskId == null) {
            throw BizException.badRequest("缺少分析任务编号");
        }
        writeVoid(conn -> {
            AnalysisTask task = taskDao.findById(conn, taskId);
            if (task == null) {
                throw BizException.notFound("分析任务不存在");
            }
            if (!task.getUserId().equals(userId)) {
                throw BizException.forbidden("无权删除他人的分析记录");
            }
            taskDao.delete(conn, taskId);
        });
    }

    @Override
    public AnalysisFeedback saveFeedback(Long userId, Long taskId, AnalysisFeedback feedback) {
        if (feedback == null) {
            throw BizException.badRequest("缺少反馈内容");
        }
        if (feedback.getHelpful() != null && feedback.getHelpful() != 0 && feedback.getHelpful() != 1) {
            throw BizException.badRequest("“是否有帮助”取值不合法");
        }
        if (feedback.getRating() != null && (feedback.getRating() < 1 || feedback.getRating() > 5)) {
            throw BizException.badRequest("评分必须在 1-5 之间");
        }
        if (!TextUtil.isBlank(feedback.getTrialResult())) {
            ValidationUtil.requireOneOf(feedback.getTrialResult(), "试产结果",
                    AnalysisFeedback.TRIAL_NOT_TRIED, AnalysisFeedback.TRIAL_PASSED,
                    AnalysisFeedback.TRIAL_PARTIAL, AnalysisFeedback.TRIAL_FAILED);
        }
        feedback.setComment(ValidationUtil.optionalText(feedback.getComment(), 1000, "文字反馈"));
        return write(conn -> {
            AnalysisTask task = taskDao.findById(conn, taskId);
            if (task == null) {
                throw BizException.notFound("分析任务不存在");
            }
            if (!task.getUserId().equals(userId)) {
                throw BizException.forbidden("只能对自己的分析记录提交反馈");
            }
            feedback.setTaskId(taskId);
            feedback.setUserId(userId);
            feedbackDao.upsert(conn, feedback);
            return feedbackDao.findByTaskAndUser(conn, taskId, userId);
        });
    }

    @Override
    public PageResult<AnalysisTask> adminPage(String keyword, String goalType, String status,
                                              int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        String trimmedKeyword = TextUtil.isBlank(keyword) ? null : keyword.trim();
        String trimmedGoal = TextUtil.isBlank(goalType) ? null : goalType.trim();
        String trimmedStatus = TextUtil.isBlank(status) ? null : status.trim();
        return read(conn -> {
            long total = taskDao.countAll(conn, trimmedKeyword, trimmedGoal, trimmedStatus);
            List<AnalysisTask> list = total == 0
                    ? Collections.emptyList()
                    : taskDao.pageAll(conn, trimmedKeyword, trimmedGoal, trimmedStatus,
                            (safePage - 1) * safeSize, safeSize);
            return new PageResult<>(list, total, safePage, safeSize);
        });
    }

    @Override
    public long countByUser(Long userId) {
        return read(conn -> taskDao.countByUser(conn, userId));
    }

    @Override
    public long countCompletedByUser(Long userId) {
        return read(conn -> taskDao.countCompletedByUser(conn, userId));
    }

    @Override
    public String lastAnalysisAt(Long userId) {
        return read(conn -> taskDao.findLastFinishedAt(conn, userId));
    }

    @Override
    public long countAll() {
        return read(taskDao::countAll);
    }

    /**
     * 加载启用中的配料调配知识卡。
     *
     * <p>知识库是分析流程的<b>增强项而非必需项</b>：若数据库尚未执行知识库建表脚本
     * （{@code flavor_knowledge} 表不存在），这里降级为「无知识卡」，
     * 建议自动回落到规则自带的候选与参考幅度，分析照常完成——
     * 不会因为知识库缺数据而让主功能报错。异常会记录日志，便于发现漏执行的脚本。</p>
     *
     * @return 知识卡列表；加载失败时返回空列表
     */
    private List<FlavorKnowledge> loadKnowledge() {
        try {
            return read(knowledgeDao::listEnabled);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "知识卡加载失败（可能未执行知识库建表脚本），本次分析回落到规则自带候选", e);
            return new ArrayList<>();
        }
    }

    /**
     * 判断目标方案与基准方案的配料是否完全一致。
     *
     * <p>比较「食材名 + 用量 + 单位」三项，忽略明细的排列顺序；
     * 用量用 {@link BigDecimal#stripTrailingZeros()} 归一后再比，避免 100 与 100.000 被判为不同。</p>
     *
     * @param baseline 基准快照
     * @param target   目标快照
     * @return 完全一致返回 true
     */
    private boolean sameRecipeItems(RecipeSnapshot baseline, RecipeSnapshot target) {
        if (baseline == null || target == null) {
            return false;
        }
        List<RecipeSnapshot.SnapshotItem> left = baseline.getItems();
        List<RecipeSnapshot.SnapshotItem> right = target.getItems();
        if (left == null || right == null || left.isEmpty() || left.size() != right.size()) {
            return false;
        }
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (RecipeSnapshot.SnapshotItem item : left) {
            counter.merge(itemKey(item), 1, Integer::sum);
        }
        for (RecipeSnapshot.SnapshotItem item : right) {
            String key = itemKey(item);
            Integer remain = counter.get(key);
            if (remain == null || remain <= 0) {
                return false;
            }
            counter.put(key, remain - 1);
        }
        return true;
    }

    /**
     * 生成用于比对配料的键：食材名 + 用量 + 单位。
     *
     * @param item 快照配料
     * @return 比对键
     */
    private String itemKey(RecipeSnapshot.SnapshotItem item) {
        String name = item.getIngredientName() == null ? "" : item.getIngredientName().trim();
        String amount = item.getAmount() == null
                ? "" : item.getAmount().stripTrailingZeros().toPlainString();
        String unit = item.getUnit() == null ? "" : item.getUnit().trim();
        return name + "|" + amount + "|" + unit;
    }

    /**
     * 构建目标方案快照：补齐食材编号，固化属性。
     *
     * @param recipe      基准配方
     * @param targetItems 前端提交的目标配料
     * @return 目标快照
     */
    private RecipeSnapshot buildTargetSnapshot(Recipe recipe, List<RecipeItem> targetItems) {
        if (targetItems == null || targetItems.isEmpty()) {
            throw BizException.badRequest("请至少保留一项目标配料");
        }
        if (targetItems.size() > MAX_TARGET_ITEMS) {
            throw BizException.badRequest("目标方案最多支持 " + MAX_TARGET_ITEMS + " 项配料");
        }
        Map<String, Long> idByName = new LinkedHashMap<>();
        for (Ingredient ingredient : read(ingredientDao::listEnabled)) {
            idByName.put(ingredient.getName(), ingredient.getId());
        }

        List<RecipeItem> normalized = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (RecipeItem item : targetItems) {
            String name = ValidationUtil.requireText(item.getIngredientName(), "目标方案食材名称", 100);
            if (!names.add(name)) {
                throw BizException.badRequest("目标方案中存在重复食材「" + name + "」，请合并后再提交");
            }
            RecipeItem copy = new RecipeItem();
            copy.setIngredientName(name);
            copy.setIngredientId(item.getIngredientId() != null ? item.getIngredientId() : idByName.get(name));
            copy.setAmount(ValidationUtil.requireDecimal(
                    item.getAmount() == null ? null : item.getAmount().toPlainString(),
                    "「" + name + "」的用量", new BigDecimal("0.001"), new BigDecimal("999999.999")));
            copy.setUnit(TextUtil.defaultIfBlank(
                    ValidationUtil.optionalText(item.getUnit(), 20, "用量单位"), "g"));
            normalized.add(copy);
        }

        Recipe targetRecipe = new Recipe();
        targetRecipe.setId(recipe.getId());
        targetRecipe.setName(recipe.getName());
        targetRecipe.setProductType(recipe.getProductType());
        targetRecipe.setItems(normalized);
        RecipeSnapshot snapshot = recipeService.buildSnapshot(targetRecipe);
        snapshot.setRecipeId(recipe.getId());
        snapshot.setRecipeName(recipe.getName());
        return snapshot;
    }

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
     * 引擎输出转数据库实体。
     *
     * @param taskId  任务编号
     * @param outcome 引擎输出
     * @return 结果实体（JSON 列已序列化）
     */
    private AnalysisResult toEntity(Long taskId, AnalysisOutcome outcome) {
        AnalysisResult result = new AnalysisResult();
        result.setTaskId(taskId);
        result.setBaselineJson(JsonUtil.toJson(outcome.getBaseline()));
        result.setTargetJson(JsonUtil.toJson(outcome.getTarget()));
        result.setDeltaJson(JsonUtil.toJson(outcome.getDeltas()));
        result.setSuggestionJson(JsonUtil.toJson(outcome.getSuggestions()));
        result.setConfidence(outcome.getConfidence());
        result.setDataCompleteness(outcome.getDataCompleteness());
        result.setSummary(outcome.getSummary());
        result.setExplanation(outcome.getExplanation());
        result.setEngineVersion(outcome.getEngineVersion());
        result.setBaseline(outcome.getBaseline());
        result.setTarget(outcome.getTarget());
        result.setDeltas(outcome.getDeltas());
        result.setSuggestions(outcome.getSuggestions());
        return result;
    }

    private com.flavorlogic.model.FlavorVector parseBaseline(String json) {
        if (TextUtil.isBlank(json)) {
            return new com.flavorlogic.model.FlavorVector();
        }
        try {
            com.flavorlogic.model.FlavorVector vector =
                    JsonUtil.gson().fromJson(json, com.flavorlogic.model.FlavorVector.class);
            return vector == null ? new com.flavorlogic.model.FlavorVector() : vector;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "解析风味向量失败", e);
            return new com.flavorlogic.model.FlavorVector();
        }
    }

    private <T> List<T> parseList(String json, Type type) {
        if (TextUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            List<T> list = JsonUtil.gson().fromJson(json, type);
            return list == null ? new ArrayList<>() : list;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "解析结果 JSON 失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 通用的 JSON 解析包装，解析失败时返回 null 并记录日志。
     *
     * @param parser 解析函数
     * @param json   JSON 文本
     * @param <T>    目标类型
     * @return 解析结果或 null
     */
    private <T> T parse(java.util.function.Function<String, T> parser, String json) {
        if (TextUtil.isBlank(json)) {
            return null;
        }
        try {
            return parser.apply(json);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "解析 JSON 字段失败", e);
            return null;
        }
    }
}
