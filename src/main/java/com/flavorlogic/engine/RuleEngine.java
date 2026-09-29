package com.flavorlogic.engine;

import com.flavorlogic.model.AnalysisConstraints;
import com.flavorlogic.model.FlavorDelta;
import com.flavorlogic.model.FlavorDimensions;
import com.flavorlogic.model.FlavorKnowledge;
import com.flavorlogic.model.FlavorRule;
import com.flavorlogic.model.Suggestion;
import com.flavorlogic.util.JsonUtil;
import com.flavorlogic.util.TextUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 规则引擎：根据风味偏移匹配 {@code flavor_rule} 中的规则，生成可解释的补偿建议。
 *
 * <p><b>触发与内容的分工</b>：{@code flavor_rule} 负责「什么时候该给建议」（触发条件），
 * {@code flavor_knowledge} 负责「具体补什么、补多少、为什么、有什么风险」（知识内容）。
 * 规则命中后优先到知识卡取候选配料与用量区间，一条建议对应一张知识卡，
 * 因此建议可以带上起效原理、风险提示与可追溯的依据来源。</p>
 *
 * <p>知识卡<b>不是必需的</b>：若某维度没有启用中的知识卡，自动回落到
 * {@code flavor_rule.action_json} 里的候选与幅度，保证规则不因知识库缺数据而失效。</p>
 *
 * <p>条件 JSON 结构：{@code {"dimension":"umami","operator":"LTE","changePercent":-10}}，
 * 支持运算符 {@code LTE}（小于等于）、{@code GTE}（大于等于）、{@code ABS_GTE}（绝对值大于等于）。</p>
 *
 * <p>动作 JSON 结构示例：
 * {@code {"actionType":"ADD","candidates":["酵母抽提物"],"referencePercentMin":0.05,"referencePercentMax":0.30}}。</p>
 */
public class RuleEngine {

    private static final Logger LOG = Logger.getLogger(RuleEngine.class.getName());

    /** 单次分析最多返回的建议条数，避免结果页堆叠 */
    private static final int MAX_SUGGESTIONS = 8;

    /** 单条规则最多展开的知识卡条数，避免同一维度刷出过多建议 */
    private static final int MAX_CARDS_PER_RULE = 3;

    /**
     * 依据启用规则评估偏移明细并生成建议。
     *
     * @param rules       适用于本次目标的规则（已按优先级排序）
     * @param deltas      偏移明细
     * @param constraints 分析约束（用于过滤过敏原与禁用原料）
     * @param knowledge   启用中的知识卡；为 null 或为空时全部回落到规则自带候选
     * @param goalType    本次分析的研发目标类型，用于挑选匹配的知识卡
     * @return 建议列表，按偏移幅度从大到小排序
     */
    public List<Suggestion> evaluate(List<FlavorRule> rules, List<FlavorDelta> deltas,
                                    AnalysisConstraints constraints,
                                    List<FlavorKnowledge> knowledge, String goalType) {
        List<Suggestion> suggestions = new ArrayList<>();
        if (rules == null || rules.isEmpty() || deltas == null || deltas.isEmpty()) {
            return suggestions;
        }
        Map<String, FlavorDelta> deltaByDimension = new LinkedHashMap<>();
        for (FlavorDelta delta : deltas) {
            deltaByDimension.put(delta.getDimension(), delta);
        }
        // 知识卡按「维度 + 目标类型」建索引：目标精确匹配的卡在前，GENERAL 卡作为补充
        Map<String, List<FlavorKnowledge>> cardIndex = KnowledgeIndex.build(knowledge, goalType, constraints);

        for (FlavorRule rule : rules) {
            try {
                JsonObject condition = JsonUtil.gson().fromJson(rule.getConditionJson(), JsonObject.class);
                if (condition == null) {
                    LOG.log(Level.WARNING, "规则 {0} 的条件 JSON 为空，已跳过", rule.getRuleCode());
                    continue;
                }
                String dimension = optString(condition, "dimension");
                String operator = optString(condition, "operator");
                BigDecimal threshold = optDecimal(condition, "changePercent");
                if (!FlavorDimensions.isDimension(dimension) || operator == null || threshold == null) {
                    LOG.log(Level.WARNING, "规则 {0} 的条件 JSON 结构不完整，已跳过", rule.getRuleCode());
                    continue;
                }
                FlavorDelta delta = deltaByDimension.get(dimension);
                if (delta == null || !matches(delta.getChangePercent(), operator, threshold)) {
                    continue;
                }
                JsonObject action = JsonUtil.gson().fromJson(rule.getActionJson(), JsonObject.class);
                if (action == null) {
                    LOG.log(Level.WARNING, "规则 {0} 的动作 JSON 为空，已跳过", rule.getRuleCode());
                    continue;
                }
                List<Suggestion> built = buildByKnowledge(rule, delta, action, cardIndex, constraints);
                if (built.isEmpty()) {
                    // 该维度没有启用中的知识卡：回落到规则自带的候选与参考幅度
                    built.add(build(rule, delta, action, constraints));
                }
                suggestions.addAll(built);
                delta.setNote("命中规则「" + rule.getRuleName() + "」：" + rule.getExplanation());
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "规则 " + rule.getRuleCode() + " 解析失败，已跳过", e);
            }
        }

        suggestions.sort((a, b) -> {
            BigDecimal left = parseChange(a.getChangeText());
            BigDecimal right = parseChange(b.getChangeText());
            return right.abs().compareTo(left.abs());
        });
        return suggestions.size() > MAX_SUGGESTIONS ? suggestions.subList(0, MAX_SUGGESTIONS) : suggestions;
    }

    /**
     * 用知识卡构建建议：一条建议对应一张知识卡。
     *
     * @param rule        命中的规则
     * @param delta       该维度的偏移
     * @param action      规则动作 JSON（提供动作类型与风险开关）
     * @param cardIndex   知识卡索引
     * @param constraints 分析约束
     * @return 建议列表；无可用知识卡时返回空列表（由调用方回落到规则自带候选）
     */
    private List<Suggestion> buildByKnowledge(FlavorRule rule, FlavorDelta delta, JsonObject action,
                                              Map<String, List<FlavorKnowledge>> cardIndex,
                                              AnalysisConstraints constraints) {
        List<Suggestion> result = new ArrayList<>();
        List<FlavorKnowledge> cards = cardIndex.get(delta.getDimension());
        if (cards == null || cards.isEmpty()) {
            return result;
        }
        List<String> blocked = new ArrayList<>();
        for (FlavorKnowledge card : cards) {
            if (result.size() >= MAX_CARDS_PER_RULE) {
                break;
            }
            if (card.hitsAllergen(constraints == null ? null : constraints.getAllergens())) {
                blocked.add(card.getCandidateIngredient());
                continue;
            }
            result.add(buildFromCard(rule, delta, action, card));
        }
        if (result.isEmpty() && !blocked.isEmpty()) {
            // 命中的候选全部被过敏原/禁用清单挡下：如实告知，而不是静默不输出
            Suggestion suggestion = new Suggestion();
            suggestion.setRuleCode(rule.getRuleCode());
            suggestion.setRuleName(rule.getRuleName());
            suggestion.setDimension(delta.getDimension());
            suggestion.setDimensionLabel(delta.getDimensionLabel());
            suggestion.setActionType(Suggestion.ACTION_NOTICE);
            suggestion.setActionText(actionText(Suggestion.ACTION_NOTICE));
            suggestion.setTarget(delta.getDimensionLabel() + "相关原料");
            suggestion.setDirection("暂无可推荐方向");
            suggestion.setReferenceRange("无固定幅度，按打样结果微调");
            suggestion.setReason(TextUtil.defaultIfBlank(rule.getExplanation(),
                    "该建议由规则「" + rule.getRuleName() + "」根据风味偏移自动生成。"));
            suggestion.setRisk("知识库中该方向的候选「" + String.join("、", blocked)
                    + "」均命中过敏原或禁用清单，已全部剔除；请调整约束条件或补充替代原料。");
            suggestion.setChangeText(formatPercent(delta.getChangePercent()));
            result.add(suggestion);
        }
        return result;
    }

    /**
     * 把一张知识卡转换成一条建议。
     *
     * @param rule   命中的规则
     * @param delta  该维度的偏移
     * @param action 规则动作 JSON
     * @param card   知识卡
     * @return 建议
     */
    private Suggestion buildFromCard(FlavorRule rule, FlavorDelta delta, JsonObject action, FlavorKnowledge card) {
        Suggestion suggestion = new Suggestion();
        suggestion.setRuleCode(rule.getRuleCode());
        suggestion.setRuleName(rule.getRuleName());
        suggestion.setKnowledgeCode(card.getKnowledgeCode());
        suggestion.setKnowledgeTitle(card.getTitle());
        suggestion.setEvidenceSource(card.getEvidenceSource());
        suggestion.setDimension(delta.getDimension());
        suggestion.setDimensionLabel(delta.getDimensionLabel());

        // 知识卡的方向比规则更精确（同一规则下可能同时存在「补什么」与「减什么」两类卡）
        String actionType = TextUtil.defaultIfBlank(card.getDirection(),
                TextUtil.defaultIfBlank(optString(action, "actionType"), Suggestion.ACTION_NOTICE));
        suggestion.setActionType(actionType);
        suggestion.setActionText(actionText(actionType));

        List<String> candidates = new ArrayList<>();
        candidates.add(card.getCandidateIngredient());
        suggestion.setCandidates(candidates);
        suggestion.setTarget(card.getCandidateIngredient());

        suggestion.setDirection(suggestion.getActionText() + "：" + delta.getDimensionLabel()
                + "（当前" + delta.getDirection() + " " + formatPercent(delta.getChangePercent()) + "）");
        suggestion.setReferenceRange(TextUtil.defaultIfBlank(card.referenceRangeText(),
                "无固定幅度，按打样结果微调"));
        // 生成原因优先用知识卡的起效原理，比规则的一句话说明更具体
        suggestion.setReason(TextUtil.defaultIfBlank(card.getMechanism(),
                TextUtil.defaultIfBlank(rule.getExplanation(),
                        "该建议由规则「" + rule.getRuleName() + "」根据风味偏移自动生成。")));
        suggestion.setChangeText(formatPercent(delta.getChangePercent()));

        List<String> risks = new ArrayList<>();
        if (!TextUtil.isBlank(card.getRiskNote())) {
            risks.add(card.getRiskNote());
        }
        if (!TextUtil.isBlank(card.getRatioNote())) {
            risks.add("复配或替代口径：" + card.getRatioNote());
        }
        appendRuleRisks(risks, action);
        suggestion.setRisk(String.join(" ", risks));
        return suggestion;
    }

    /**
     * 追加规则级的风险提示，无任何提示时补上默认说明。
     *
     * @param risks  出参：风险文本集合
     * @param action 规则动作 JSON
     */
    private void appendRuleRisks(List<String> risks, JsonObject action) {
        if (Boolean.TRUE.equals(optBoolean(action, "requireAftertasteCheck"))) {
            risks.add("代糖复配存在后味风险，需做后味与感官复核。");
        }
        if (Boolean.FALSE.equals(optBoolean(action, "restoreFatDirectly"))) {
            risks.add("不建议直接恢复原油脂用量，优先采用低用量增香或工艺补偿。");
        }
        if (risks.isEmpty()) {
            risks.add("建议为打样前的方向性参考，需经试产与感官评价确认。");
        }
    }

    private Suggestion build(FlavorRule rule, FlavorDelta delta, JsonObject action, AnalysisConstraints constraints) {
        Suggestion suggestion = new Suggestion();
        suggestion.setRuleCode(rule.getRuleCode());
        suggestion.setRuleName(rule.getRuleName());
        suggestion.setDimension(delta.getDimension());
        suggestion.setDimensionLabel(delta.getDimensionLabel());

        String actionType = TextUtil.defaultIfBlank(optString(action, "actionType"), Suggestion.ACTION_NOTICE);
        suggestion.setActionType(actionType);
        suggestion.setActionText(actionText(actionType));

        List<String> candidates = optStringList(action, "candidates");
        List<String> blocked = new ArrayList<>();
        List<String> allowed = new ArrayList<>();
        for (String candidate : candidates) {
            if (isExcluded(candidate, constraints)) {
                blocked.add(candidate);
            } else {
                allowed.add(candidate);
            }
        }
        suggestion.setCandidates(allowed);

        String category = optString(action, "ingredientCategory");
        if (category != null) {
            suggestion.setTarget("「" + category + "」类原料");
        } else if (!allowed.isEmpty()) {
            suggestion.setTarget(String.join("、", allowed));
        } else {
            suggestion.setTarget(delta.getDimensionLabel() + "相关原料");
        }

        suggestion.setDirection(suggestion.getActionText() + "：" + delta.getDimensionLabel()
                + "（当前" + delta.getDirection() + " " + formatPercent(delta.getChangePercent()) + "）");
        suggestion.setReferenceRange(buildReferenceRange(action));
        suggestion.setReason(TextUtil.defaultIfBlank(rule.getExplanation(),
                "该建议由规则「" + rule.getRuleName() + "」根据风味偏移自动生成。"));
        suggestion.setChangeText(formatPercent(delta.getChangePercent()));

        List<String> risks = new ArrayList<>();
        if (!blocked.isEmpty()) {
            risks.add("候选「" + String.join("、", blocked) + "」命中过敏原或禁用清单，已剔除。");
        }
        appendRuleRisks(risks, action);
        suggestion.setRisk(String.join(" ", risks));
        return suggestion;
    }

    private boolean matches(BigDecimal change, String operator, BigDecimal threshold) {
        if (change == null) {
            return false;
        }
        switch (operator) {
            case "LTE":
                return change.compareTo(threshold) <= 0;
            case "GTE":
                return change.compareTo(threshold) >= 0;
            case "ABS_GTE":
                return change.abs().compareTo(threshold.abs()) >= 0;
            case "RANGE_OUT":
                return change.abs().compareTo(threshold.abs()) > 0;
            default:
                return false;
        }
    }

    private String actionText(String actionType) {
        switch (actionType) {
            case Suggestion.ACTION_ADD:
                return "建议增加";
            case Suggestion.ACTION_REDUCE:
                return "建议减少";
            case Suggestion.ACTION_REPLACE:
                return "建议替换";
            default:
                return "提示关注";
        }
    }

    private String buildReferenceRange(JsonObject action) {
        BigDecimal min = optDecimal(action, "referencePercentMin");
        BigDecimal max = optDecimal(action, "referencePercentMax");
        if (min == null && max == null) {
            return "无固定幅度，按打样结果微调";
        }
        if (min != null && max != null) {
            return "参考幅度 " + min.stripTrailingZeros().toPlainString() + "% - "
                    + max.stripTrailingZeros().toPlainString() + "%（占配方总量）";
        }
        BigDecimal single = min == null ? max : min;
        return "参考幅度约 " + single.stripTrailingZeros().toPlainString() + "%（占配方总量）";
    }

    private boolean isExcluded(String candidate, AnalysisConstraints constraints) {
        if (constraints == null || constraints.getAllergens() == null || TextUtil.isBlank(candidate)) {
            return false;
        }
        for (String allergen : constraints.getAllergens()) {
            if (TextUtil.isBlank(allergen)) {
                continue;
            }
            String trimmed = allergen.trim();
            if (candidate.contains(trimmed) || trimmed.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) {
            return "0%";
        }
        String sign = value.signum() > 0 ? "+" : "";
        return sign + value.stripTrailingZeros().toPlainString() + "%";
    }

    private BigDecimal parseChange(String changeText) {
        if (TextUtil.isBlank(changeText)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(changeText.replace("%", "").replace("+", "").trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String optString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private BigDecimal optDecimal(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsBigDecimal();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Boolean optBoolean(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<String> optStringList(JsonObject json, String key) {
        List<String> result = new ArrayList<>();
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonArray()) {
            return result;
        }
        JsonArray array = element.getAsJsonArray();
        for (JsonElement item : array) {
            if (!item.isJsonNull()) {
                result.add(item.getAsString());
            }
        }
        return result;
    }
}
