package com.flavorlogic.service.impl;

import com.flavorlogic.dao.GoalTargetDao;
import com.flavorlogic.dao.impl.GoalTargetDaoImpl;
import com.flavorlogic.model.AnalysisTask;
import com.flavorlogic.model.FlavorDimensions;
import com.flavorlogic.model.FlavorVector;
import com.flavorlogic.model.GoalTarget;
import com.flavorlogic.model.GoalVector;
import com.flavorlogic.model.RegionProfile;
import com.flavorlogic.service.BaseService;
import com.flavorlogic.service.GoalTemplateService;
import com.flavorlogic.util.BizException;
import com.flavorlogic.util.TextUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 目标模板业务实现。
 *
 * <p>展开动作只有三步：查模板 → 折算目标值 → 截断到合法区间。
 * **所有目标语义都来自 {@code goal_target} 表**，本类不含任何针对具体研发目标的分支判断——
 * 因此新增研发目标、调整某个目标的维度意图，都只需改数据。</p>
 */
public class GoalTemplateServiceImpl extends BaseService implements GoalTemplateService {

    private static final Logger LOG = Logger.getLogger(GoalTemplateServiceImpl.class.getName());

    /** 风味得分的取值上限，目标值超出时截断 */
    private static final BigDecimal MAX_SCORE = new BigDecimal("100");

    /**
     * 合法研发目标集合。
     *
     * <p>这份枚举属于代码而非数据：判断“调用方传的值是否合法”是程序语言的职责，
     * 而“每个目标对应什么意图”才是数据的职责。</p>
     */
    private static final Set<String> SUPPORTED_GOALS = new LinkedHashSet<>(Arrays.asList(
            AnalysisTask.GOAL_REPLACE,
            AnalysisTask.GOAL_REDUCE_SUGAR,
            AnalysisTask.GOAL_REDUCE_FAT,
            AnalysisTask.GOAL_ADJUST_STIMULATION,
            AnalysisTask.GOAL_REGION_ADAPT,
            AnalysisTask.GOAL_GENERAL));

    private final GoalTargetDao goalTargetDao = new GoalTargetDaoImpl();

    @Override
    public GoalVector expand(String goalType, FlavorVector baseline, RegionProfile region) {
        if (baseline == null) {
            throw BizException.badRequest("缺少基准风味向量，无法展开目标");
        }
        String goal = TextUtil.defaultIfBlank(goalType, AnalysisTask.GOAL_GENERAL).trim();
        if (!SUPPORTED_GOALS.contains(goal)) {
            throw BizException.badRequest("不支持的研发目标类型：" + goal);
        }

        List<GoalTarget> rows = read(conn -> goalTargetDao.listByGoalType(conn, goal));
        if (rows.isEmpty()) {
            // 模板缺失不阻断流程：回落到「全部保持」，与知识卡缺数据时的处理保持一致。
            // 记录 WARNING 便于发现漏执行的数据脚本。
            LOG.log(Level.WARNING, "研发目标 {0} 的目标模板为空，本次按“全部保持”展开", goal);
        }
        Map<String, GoalTarget> flavorRows = new LinkedHashMap<>();
        List<GoalTarget> componentRows = new ArrayList<>();
        for (GoalTarget row : rows) {
            if (GoalTarget.KIND_FLAVOR.equals(row.getTargetKind())
                    && FlavorDimensions.isDimension(row.getTargetKey())) {
                flavorRows.put(row.getTargetKey(), row);
            } else if (GoalTarget.KIND_COMPONENT.equals(row.getTargetKind())) {
                componentRows.add(row);
            }
        }

        GoalVector vector = new GoalVector();
        vector.setGoalType(goal);
        if (region != null) {
            vector.setRegionCode(region.getRegionCode());
            vector.setRegionName(region.getRegionName());
        }
        for (String dimension : FlavorDimensions.ALL) {
            vector.getDimensions().add(buildDimension(dimension, flavorRows.get(dimension), baseline, region));
        }
        for (GoalTarget row : componentRows) {
            vector.getComponents().add(buildComponent(row));
        }
        return vector;
    }

    /**
     * 展开单个风味维度。
     *
     * @param dimension 维度键
     * @param row       该维度的模板行，可为 null（模板缺失）
     * @param baseline  基准风味向量
     * @param region    区域画像，可为 null
     * @return 该维度的目标
     */
    private GoalVector.DimensionGoal buildDimension(String dimension, GoalTarget row,
                                                    FlavorVector baseline, RegionProfile region) {
        GoalVector.DimensionGoal goal = new GoalVector.DimensionGoal();
        goal.setKey(dimension);
        goal.setLabel(FlavorDimensions.label(dimension));
        String intent = row == null
                ? GoalTarget.INTENT_KEEP
                : TextUtil.defaultIfBlank(row.getIntent(), GoalTarget.INTENT_KEEP);
        goal.setIntent(intent);
        goal.setBaseline(baseline.get(dimension));
        goal.setWeight(row == null || row.getWeight() == null ? BigDecimal.ONE : row.getWeight());
        goal.setNote(row == null ? "未定义模板，按“保持基准”处理" : row.getNote());
        if (GoalTarget.INTENT_CHANGE.equals(intent)) {
            goal.setTarget(resolveTarget(dimension, row == null ? null : row.getMagnitude(),
                    baseline.get(dimension), region));
        }
        return goal;
    }

    /**
     * 把成分层动作行转成目标项。
     *
     * @param row 模板行
     * @return 成分层目标
     */
    private GoalVector.ComponentGoal buildComponent(GoalTarget row) {
        GoalVector.ComponentGoal goal = new GoalVector.ComponentGoal();
        goal.setCategory(row.getTargetKey());
        goal.setIntent(TextUtil.defaultIfBlank(row.getIntent(), GoalTarget.INTENT_KEEP));
        // magnitude 为 null 时**不兜底**：合成器会跳过该动作并给出提示，
        // 而不是擅自猜一个减量幅度 —— 凭空定值会让生成的配方失去依据
        goal.setMagnitude(row.getMagnitude());
        goal.setWeight(row.getWeight() == null ? BigDecimal.ONE : row.getWeight());
        goal.setNote(row.getNote());
        return goal;
    }

    /**
     * 计算 CHANGE 维度的目标值。
     *
     * <p>倍数优先取模板的 {@code magnitude}；为空时按区域画像权重折算（区域适配场景）；
     * 两者都没有则等于基准值。</p>
     *
     * <p><b>结果必须截断到 0–100</b>：区域权重是倍数，会把本来就高的维度推过上限——
     * 例如基准辣度 90 × 西南权重 1.2 ＝ 108，不截断就会得到非法分数。</p>
     *
     * @param dimension     维度键
     * @param magnitude     模板给定的倍数，可为 null
     * @param baselineValue 基准值
     * @param region        区域画像，可为 null
     * @return 目标值（两位小数）
     */
    private BigDecimal resolveTarget(String dimension, BigDecimal magnitude,
                                     BigDecimal baselineValue, RegionProfile region) {
        BigDecimal factor = magnitude;
        if (factor == null && region != null) {
            factor = region.weightOf(dimension);
        }
        if (factor == null) {
            factor = BigDecimal.ONE;
        }
        BigDecimal value = baselineValue.multiply(factor);
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            value = BigDecimal.ZERO;
        } else if (value.compareTo(MAX_SCORE) > 0) {
            value = MAX_SCORE;
        }
        return FlavorVector.normalize(value);
    }
}
