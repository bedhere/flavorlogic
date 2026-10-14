package com.flavorlogic.module.ingredient.service.impl;

import com.flavorlogic.module.ingredient.dao.IngredientDao;
import com.flavorlogic.module.ingredient.dao.impl.IngredientDaoImpl;
import com.flavorlogic.common.model.FlavorDimensions;
import com.flavorlogic.module.ingredient.model.Ingredient;
import com.flavorlogic.common.service.BaseService;
import com.flavorlogic.module.ingredient.service.IngredientService;
import com.flavorlogic.common.util.BizException;
import com.flavorlogic.common.util.PageResult;
import com.flavorlogic.common.util.TextUtil;
import com.flavorlogic.common.util.ValidationUtil;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/** Ingredient 业务：从原 MetaService 按实际职责拆分，保留原有校验和事务。 */
public class IngredientServiceImpl extends BaseService implements IngredientService {
    private static final BigDecimal DIMENSION_MIN = BigDecimal.ZERO;
    private static final BigDecimal DIMENSION_MAX = new BigDecimal("100");
    private final IngredientDao ingredientDao = new IngredientDaoImpl();

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
