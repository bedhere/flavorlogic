package com.flavorlogic.service.impl;

import com.flavorlogic.dao.FlavorRuleDao;
import com.flavorlogic.dao.IngredientDao;
import com.flavorlogic.dao.RegionProfileDao;
import com.flavorlogic.dao.impl.FlavorRuleDaoImpl;
import com.flavorlogic.dao.impl.IngredientDaoImpl;
import com.flavorlogic.dao.impl.RegionProfileDaoImpl;
import com.flavorlogic.model.FlavorDimensions;
import com.flavorlogic.model.FlavorRule;
import com.flavorlogic.model.Ingredient;
import com.flavorlogic.model.RegionProfile;
import com.flavorlogic.service.BaseService;
import com.flavorlogic.service.MetaService;
import com.flavorlogic.util.BizException;
import com.flavorlogic.util.JsonUtil;
import com.flavorlogic.util.PageResult;
import com.flavorlogic.util.TextUtil;
import com.flavorlogic.util.ValidationUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 基础数据业务实现。
 */
public class MetaServiceImpl extends BaseService implements MetaService {

    private static final BigDecimal DIMENSION_MIN = BigDecimal.ZERO;
    private static final BigDecimal DIMENSION_MAX = new BigDecimal("100");

    private final IngredientDao ingredientDao = new IngredientDaoImpl();
    private final RegionProfileDao regionDao = new RegionProfileDaoImpl();
    private final FlavorRuleDao ruleDao = new FlavorRuleDaoImpl();

    @Override
    public List<Ingredient> listEnabledIngredients() {
        return read(ingredientDao::listEnabled);
    }

    @Override
    public List<String> ingredientCategories() {
        return read(ingredientDao::listCategories);
    }

    @Override
    public PageResult<Ingredient> adminPageIngredients(String keyword, String category, Integer status,
                                                       int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        String trimmedKeyword = TextUtil.isBlank(keyword) ? null : keyword.trim();
        String trimmedCategory = TextUtil.isBlank(category) ? null : category.trim();
        return read(conn -> {
            long total = ingredientDao.count(conn, trimmedKeyword, trimmedCategory, status);
            List<Ingredient> list = total == 0
                    ? Collections.emptyList()
                    : ingredientDao.page(conn, trimmedKeyword, trimmedCategory, status,
                            (safePage - 1) * safeSize, safeSize);
            return new PageResult<>(list, total, safePage, safeSize);
        });
    }

    @Override
    public Ingredient adminCreateIngredient(Ingredient ingredient) {
        normalize(ingredient);
        return write(conn -> {
            if (ingredientDao.findByName(conn, ingredient.getName()) != null) {
                throw BizException.conflict("食材「" + ingredient.getName() + "」已存在，请直接编辑该食材");
            }
            ingredient.setId(ingredientDao.insert(conn, ingredient));
            return ingredient;
        });
    }

    @Override
    public Ingredient adminUpdateIngredient(Ingredient ingredient) {
        if (ingredient.getId() == null) {
            throw BizException.badRequest("缺少食材编号");
        }
        normalize(ingredient);
        return write(conn -> {
            Ingredient existing = ingredientDao.findById(conn, ingredient.getId());
            if (existing == null) {
                throw BizException.notFound("食材不存在");
            }
            Ingredient sameName = ingredientDao.findByName(conn, ingredient.getName());
            if (sameName != null && !sameName.getId().equals(ingredient.getId())) {
                throw BizException.conflict("食材名称「" + ingredient.getName() + "」已被其他记录占用");
            }
            if (ingredient.getStatus() == null) {
                ingredient.setStatus(existing.getStatus());
            }
            ingredientDao.update(conn, ingredient);
            return ingredient;
        });
    }

    @Override
    public void adminUpdateIngredientStatus(Long id, int status) {
        if (status != 0 && status != 1) {
            throw BizException.badRequest("状态取值不合法");
        }
        writeVoid(conn -> {
            if (ingredientDao.findById(conn, id) == null) {
                throw BizException.notFound("食材不存在");
            }
            ingredientDao.updateStatus(conn, id, status);
        });
    }

    @Override
    public long countIngredients() {
        return read(ingredientDao::countAll);
    }

    @Override
    public long countEnabledIngredients() {
        return read(ingredientDao::countEnabled);
    }

    @Override
    public List<RegionProfile> listRegions() {
        return read(regionDao::listEnabled);
    }

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

    /**
     * 校验规则 JSON 结构与维度字段，避免写入无法解析的规则。
     *
     * @param json   JSON 文本
     * @param field  字段名
     * @param rule   规则（条件 JSON 需要校验 dimension）
     */
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

    /**
     * 规范化食材字段并校验八维取值区间 0-100。
     *
     * @param ingredient 食材
     */
    private void normalize(Ingredient ingredient) {
        ingredient.setName(ValidationUtil.requireText(ingredient.getName(), "食材名称", 100));
        ingredient.setCategory(ValidationUtil.optionalText(ingredient.getCategory(), 50, "食材分类"));
        ingredient.setDefaultUnit(TextUtil.defaultIfBlank(
                ValidationUtil.optionalText(ingredient.getDefaultUnit(), 20, "默认单位"), "g"));
        ingredient.setDescription(ValidationUtil.optionalText(ingredient.getDescription(), 500, "食材说明"));
        ingredient.setDataSource(ValidationUtil.optionalText(ingredient.getDataSource(), 255, "数据来源"));
        if (ingredient.getStatus() == null) {
            ingredient.setStatus(1);
        }
        for (String dimension : FlavorDimensions.ALL) {
            BigDecimal value = dimensionValue(ingredient, dimension);
            if (value == null) {
                value = BigDecimal.ZERO;
            }
            if (value.compareTo(DIMENSION_MIN) < 0 || value.compareTo(DIMENSION_MAX) > 0) {
                throw BizException.badRequest("「" + FlavorDimensions.label(dimension) + "」属性值必须在 0-100 之间");
            }
            setDimensionValue(ingredient, dimension, value);
        }
    }

    private BigDecimal dimensionValue(Ingredient ingredient, String dimension) {
        switch (dimension) {
            case FlavorDimensions.SWEET: return ingredient.getSweet();
            case FlavorDimensions.SALTY: return ingredient.getSalty();
            case FlavorDimensions.SOUR: return ingredient.getSour();
            case FlavorDimensions.BITTER: return ingredient.getBitter();
            case FlavorDimensions.UMAMI: return ingredient.getUmami();
            case FlavorDimensions.SPICY: return ingredient.getSpicy();
            case FlavorDimensions.NUMBING: return ingredient.getNumbing();
            case FlavorDimensions.FAT_AROMA: return ingredient.getFatAroma();
            default: return null;
        }
    }

    private void setDimensionValue(Ingredient ingredient, String dimension, BigDecimal value) {
        switch (dimension) {
            case FlavorDimensions.SWEET: ingredient.setSweet(value); break;
            case FlavorDimensions.SALTY: ingredient.setSalty(value); break;
            case FlavorDimensions.SOUR: ingredient.setSour(value); break;
            case FlavorDimensions.BITTER: ingredient.setBitter(value); break;
            case FlavorDimensions.UMAMI: ingredient.setUmami(value); break;
            case FlavorDimensions.SPICY: ingredient.setSpicy(value); break;
            case FlavorDimensions.NUMBING: ingredient.setNumbing(value); break;
            case FlavorDimensions.FAT_AROMA: ingredient.setFatAroma(value); break;
            default: break;
        }
    }
}
