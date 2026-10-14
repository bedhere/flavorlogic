package com.flavorlogic.module.analysis.service.impl;

import com.flavorlogic.module.analysis.dao.FlavorRuleDao;
import com.flavorlogic.module.analysis.dao.impl.FlavorRuleDaoImpl;
import com.flavorlogic.common.model.FlavorDimensions;
import com.flavorlogic.module.analysis.model.FlavorRule;
import com.flavorlogic.common.service.BaseService;
import com.flavorlogic.module.analysis.service.RuleService;
import com.flavorlogic.common.util.BizException;
import com.flavorlogic.common.util.PageResult;
import com.flavorlogic.common.util.TextUtil;
import com.flavorlogic.common.util.ValidationUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Collections;
import java.util.List;

/** Rule 业务：从原 MetaService 按实际职责拆分，保留原有校验和事务。 */
public class RuleServiceImpl extends BaseService implements RuleService {
    private final FlavorRuleDao ruleDao = new FlavorRuleDaoImpl();

    @Override
    public List<FlavorRule> listRules() {
        return read(ruleDao::listEnabled);
    }

    @Override
    public PageResult<FlavorRule> adminPageRules(String keyword, String goalType, Integer status,
                                                 int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        String trimmedKeyword = TextUtil.isBlank(keyword) ? null : keyword.trim();
        String trimmedGoal = TextUtil.isBlank(goalType) ? null : goalType.trim();
        return read(conn -> {
            long total = ruleDao.count(conn, trimmedKeyword, trimmedGoal, status);
            List<FlavorRule> list = total == 0
                    ? Collections.emptyList()
                    : ruleDao.page(conn, trimmedKeyword, trimmedGoal, status, (safePage - 1) * safeSize, safeSize);
            return new PageResult<>(list, total, safePage, safeSize);
        });
    }

    @Override
    public FlavorRule adminUpdateRule(FlavorRule rule) {
        if (rule.getId() == null) {
            throw BizException.badRequest("缺少规则编号");
        }
        rule.setRuleName(ValidationUtil.requireText(rule.getRuleName(), "规则名称", 100));
        rule.setGoalType(ValidationUtil.requireOneOf(TextUtil.defaultIfBlank(rule.getGoalType(), "GENERAL"),
                "适用目标", "GENERAL", "REPLACE", "REDUCE_SUGAR", "REDUCE_FAT", "ADJUST_STIMULATION", "REGION_ADAPT"));
        rule.setExplanation(ValidationUtil.optionalText(rule.getExplanation(), 500, "规则说明"));
        validateJson(rule.getConditionJson(), "触发条件", rule);
        validateJson(rule.getActionJson(), "建议动作", rule);
        return write(conn -> {
            FlavorRule existing = ruleDao.findById(conn, rule.getId());
            if (existing == null) {
                throw BizException.notFound("规则不存在");
            }
            if (rule.getStatus() == null) {
                rule.setStatus(existing.getStatus());
            }
            if (rule.getPriority() == null) {
                rule.setPriority(existing.getPriority());
            }
            ruleDao.update(conn, rule);
            return rule;
        });
    }

    @Override
    public void adminUpdateRuleStatus(Long id, int status) {
        if (status != 0 && status != 1) {
            throw BizException.badRequest("状态取值不合法");
        }
        writeVoid(conn -> {
            if (ruleDao.findById(conn, id) == null) {
                throw BizException.notFound("规则不存在");
            }
            ruleDao.updateStatus(conn, id, status);
        });
    }

    private void validateJson(String json, String field, FlavorRule rule) {
        if (TextUtil.isBlank(json)) {
            throw BizException.badRequest(field + "不能为空");
        }
        JsonObject object;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw BizException.badRequest(field + "必须是 JSON 对象");
            }
            object = element.getAsJsonObject();
        } catch (RuntimeException e) {
            throw BizException.badRequest(field + "不是合法的 JSON");
        }
        if ("触发条件".equals(field)) {
            JsonElement dimension = object.get("dimension");
            if (dimension == null || dimension.isJsonNull() || !FlavorDimensions.isDimension(dimension.getAsString())) {
                throw BizException.badRequest("触发条件中的 dimension 必须是八维之一："
                        + String.join("、", FlavorDimensions.ALL));
            }
            JsonElement operator = object.get("operator");
            if (operator == null || operator.isJsonNull()) {
                throw BizException.badRequest("触发条件缺少 operator（LTE / GTE / ABS_GTE）");
            }
            JsonElement change = object.get("changePercent");
            if (change == null || change.isJsonNull()) {
                throw BizException.badRequest("触发条件缺少 changePercent");
            }
        }
    }
}
