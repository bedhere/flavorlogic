package com.flavorlogic.service.impl;

import com.flavorlogic.dao.IngredientDao;
import com.flavorlogic.dao.RecipeDao;
import com.flavorlogic.dao.RecipeItemDao;
import com.flavorlogic.dao.impl.IngredientDaoImpl;
import com.flavorlogic.dao.impl.RecipeDaoImpl;
import com.flavorlogic.dao.impl.RecipeItemDaoImpl;
import com.flavorlogic.engine.FlavorEngine;
import com.flavorlogic.model.FlavorDimensions;
import com.flavorlogic.model.FlavorVector;
import com.flavorlogic.model.Ingredient;
import com.flavorlogic.model.Recipe;
import com.flavorlogic.model.RecipeItem;
import com.flavorlogic.model.RecipeProfile;
import com.flavorlogic.model.RecipeSnapshot;
import com.flavorlogic.service.BaseService;
import com.flavorlogic.service.RecipeService;
import com.flavorlogic.util.BizException;
import com.flavorlogic.util.DateTimeUtil;
import com.flavorlogic.util.PageResult;
import com.flavorlogic.util.TextUtil;
import com.flavorlogic.util.ValidationUtil;

import java.math.BigDecimal;
import java.sql.Connection;
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
 * 配方管理业务实现。
 *
 * <p>关键约束：</p>
 * <ul>
 *   <li>配方与明细必须同事务写入，避免出现“有配方无配料”的脏数据；</li>
 *   <li>所有读写都校验数据归属，防止通过修改 URL 越权访问他人配方；</li>
 *   <li>删除采用软删除（status = 0），保证历史分析记录仍可回看当时的配方。</li>
 * </ul>
 */
public class RecipeServiceImpl extends BaseService implements RecipeService {

    private static final Logger LOG = Logger.getLogger(RecipeServiceImpl.class.getName());

    private static final int MAX_ITEMS = 50;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999.999");

    /** 批量画像一次最多计算的配方数，避免列表接口被超大配方库拖慢 */
    private static final int MAX_PROFILE_RECIPES = 200;

    private final RecipeDao recipeDao = new RecipeDaoImpl();
    private final RecipeItemDao recipeItemDao = new RecipeItemDaoImpl();
    private final IngredientDao ingredientDao = new IngredientDaoImpl();
    /** 画像复用与正式分析完全相同的计算口径，保证两处数值一致 */
    private final FlavorEngine flavorEngine = new FlavorEngine();

    @Override
    public Recipe create(Long userId, Recipe recipe) {
        if (userId == null) {
            throw BizException.unauthorized("请先登录");
        }
        normalize(recipe);
        return write(conn -> {
            recipe.setUserId(userId);
            recipe.setVersionNo(1);
            recipe.setStatus(Recipe.STATUS_ACTIVE);
            recipe.setId(recipeDao.insert(conn, recipe));
            saveItems(conn, recipe.getId(), recipe.getItems());
            return recipe;
        });
    }

    @Override
    public Recipe update(Long userId, Recipe recipe) {
        if (recipe.getId() == null) {
            throw BizException.badRequest("缺少配方编号");
        }
        normalize(recipe);
        return write(conn -> {
            Recipe existing = recipeDao.findById(conn, recipe.getId());
            if (existing == null || existing.getStatus() == null
                    || existing.getStatus() != Recipe.STATUS_ACTIVE) {
                throw BizException.notFound("配方不存在或已删除");
            }
            if (!existing.getUserId().equals(userId)) {
                throw BizException.forbidden("无权修改他人的配方");
            }
            recipe.setUserId(existing.getUserId());
            recipe.setVersionNo((existing.getVersionNo() == null ? 1 : existing.getVersionNo()) + 1);
            recipe.setStatus(Recipe.STATUS_ACTIVE);
            recipeDao.update(conn, recipe);
            recipeItemDao.deleteByRecipe(conn, recipe.getId());
            saveItems(conn, recipe.getId(), recipe.getItems());
            return recipe;
        });
    }

    @Override
    public PageResult<Recipe> page(Long userId, String keyword, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        String trimmedKeyword = TextUtil.isBlank(keyword) ? null : keyword.trim();
        return read(conn -> {
            long total = recipeDao.countByUser(conn, userId, trimmedKeyword);
            List<Recipe> list = total == 0
                    ? Collections.emptyList()
                    : recipeDao.pageByUser(conn, userId, trimmedKeyword, (safePage - 1) * safeSize, safeSize);
            return new PageResult<>(list, total, safePage, safeSize);
        });
    }

    @Override
    public Recipe detail(Long userId, Long recipeId) {
        if (recipeId == null) {
            throw BizException.badRequest("缺少配方编号");
        }
        return read(conn -> {
            Recipe recipe = recipeDao.findById(conn, recipeId);
            if (recipe == null || recipe.getStatus() == null
                    || recipe.getStatus() != Recipe.STATUS_ACTIVE) {
                throw BizException.notFound("配方不存在或已删除");
            }
            if (!recipe.getUserId().equals(userId)) {
                throw BizException.forbidden("无权查看他人的配方");
            }
            List<RecipeItem> items = recipeItemDao.listByRecipe(conn, recipeId);
            recipe.setItems(items);
            recipe.setItemCount(items.size());
            recipe.setHasMissingAttribute(items.stream()
                    .anyMatch(item -> Boolean.FALSE.equals(item.getAttributeAvailable())));
            return recipe;
        });
    }

    @Override
    public void delete(Long userId, Long recipeId) {
        if (recipeId == null) {
            throw BizException.badRequest("缺少配方编号");
        }
        writeVoid(conn -> {
            Recipe recipe = recipeDao.findById(conn, recipeId);
            if (recipe == null || recipe.getStatus() == null
                    || recipe.getStatus() != Recipe.STATUS_ACTIVE) {
                throw BizException.notFound("配方不存在或已删除");
            }
            if (!recipe.getUserId().equals(userId)) {
                throw BizException.forbidden("无权删除他人的配方");
            }
            recipeDao.softDelete(conn, recipeId);
        });
    }

    @Override
    public List<Recipe> recent(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        return read(conn -> recipeDao.listRecentByUser(conn, userId, safeLimit));
    }

    @Override
    public long countByUser(Long userId) {
        return read(conn -> recipeDao.countActiveByUser(conn, userId));
    }

    @Override
    public RecipeSnapshot buildSnapshot(Recipe recipe) {
        if (recipe == null) {
            throw BizException.badRequest("缺少配方");
        }
        return read(conn -> buildSnapshot(conn, recipe));
    }

    /**
     * 在指定连接上生成配方快照。
     *
     * <p>供本类内部复用（批量计算配方画像时逐条调用），避免对每个配方各开一次连接。</p>
     *
     * @param conn   数据库连接
     * @param recipe 配方（含明细）
     * @return 配方快照
     */
    private RecipeSnapshot buildSnapshot(Connection conn, Recipe recipe) {
        RecipeSnapshot snapshot = new RecipeSnapshot();
        snapshot.setRecipeId(recipe.getId());
        snapshot.setRecipeName(recipe.getName());
        snapshot.setProductType(recipe.getProductType());
        snapshot.setProcessNote(recipe.getProcessNote());
        snapshot.setSnapshotAt(DateTimeUtil.now());

        List<RecipeItem> items = recipe.getItems() == null ? new ArrayList<>() : recipe.getItems();
        List<Long> ingredientIds = new ArrayList<>();
        for (RecipeItem item : items) {
            if (item.getIngredientId() != null) {
                ingredientIds.add(item.getIngredientId());
            }
        }
        Map<Long, Ingredient> ingredientMap = new LinkedHashMap<>();
        if (!ingredientIds.isEmpty()) {
            for (Ingredient ingredient : ingredientDao.findByIds(conn, ingredientIds)) {
                ingredientMap.put(ingredient.getId(), ingredient);
            }
        }

        BigDecimal total = BigDecimal.ZERO;
        for (RecipeItem item : items) {
            RecipeSnapshot.SnapshotItem snapshotItem = new RecipeSnapshot.SnapshotItem();
            snapshotItem.setIngredientId(item.getIngredientId());
            snapshotItem.setAmount(item.getAmount());
            snapshotItem.setUnit(TextUtil.defaultIfBlank(item.getUnit(), "g"));

            Ingredient ingredient = item.getIngredientId() == null ? null : ingredientMap.get(item.getIngredientId());
            if (ingredient != null) {
                snapshotItem.setIngredientName(ingredient.getName());
                snapshotItem.setIngredientCategory(ingredient.getCategory());
                snapshotItem.setAttributeAvailable(Boolean.TRUE);
                Map<String, BigDecimal> attributes = new LinkedHashMap<>();
                for (String dimension : FlavorDimensions.ALL) {
                    attributes.put(dimension, ingredient.valueOf(dimension));
                }
                snapshotItem.setAttributes(attributes);
            } else {
                snapshotItem.setIngredientName(item.getIngredientName());
                snapshotItem.setIngredientCategory(null);
                snapshotItem.setAttributeAvailable(Boolean.FALSE);
                snapshotItem.setAttributes(new LinkedHashMap<>());
            }
            if (snapshotItem.getAmount() != null && snapshotItem.getAmount().signum() > 0) {
                total = total.add(snapshotItem.getAmount());
            }
            snapshot.getItems().add(snapshotItem);
        }
        snapshot.setTotalAmount(total);
        return snapshot;
    }

    @Override
    public List<RecipeProfile> profiles(Long userId) {
        if (userId == null) {
            throw BizException.unauthorized("请先登录");
        }
        return read(conn -> {
            List<Recipe> recipes = recipeDao.listRecentByUser(conn, userId, MAX_PROFILE_RECIPES);
            if (recipes.isEmpty()) {
                return new ArrayList<RecipeProfile>();
            }
            List<Long> recipeIds = new ArrayList<>();
            for (Recipe recipe : recipes) {
                recipeIds.add(recipe.getId());
            }
            // 一次查回全部明细再按配方分组，避免逐条配方发 SQL
            Map<Long, List<RecipeItem>> itemsByRecipe = new LinkedHashMap<>();
            for (RecipeItem item : recipeItemDao.listByRecipeIds(conn, recipeIds)) {
                itemsByRecipe.computeIfAbsent(item.getRecipeId(), key -> new ArrayList<>()).add(item);
            }

            List<RecipeProfile> profiles = new ArrayList<>();
            for (Recipe recipe : recipes) {
                List<RecipeItem> items = itemsByRecipe.get(recipe.getId());
                if (items == null || items.isEmpty()) {
                    // 没有配料的配方算不出画像，跳过而不是报错 —— 列表里少一个标签不影响使用
                    continue;
                }
                try {
                    recipe.setItems(items);
                    RecipeSnapshot snapshot = buildSnapshot(conn, recipe);
                    FlavorVector vector = flavorEngine.compute(snapshot);
                    RecipeProfile profile = new RecipeProfile();
                    profile.setRecipeId(recipe.getId());
                    profile.setRecipeName(recipe.getName());
                    profile.setVersionNo(recipe.getVersionNo());
                    profile.setScores(vector.getScores());
                    profile.setTotalAmount(vector.getTotalAmount());
                    profile.setItemCount(items.size());
                    profile.setCompleteness(flavorEngine.dataCompleteness(snapshot));
                    profile.setHasMissingAttribute(items.stream()
                            .anyMatch(item -> Boolean.FALSE.equals(item.getAttributeAvailable())));
                    profiles.add(profile);
                } catch (RuntimeException e) {
                    // 单个配方算不出来（如全部配料都缺属性数据）不应影响整个列表
                    LOG.log(Level.WARNING, "计算配方画像失败，已跳过：recipeId=" + recipe.getId(), e);
                }
            }
            return profiles;
        });
    }

    /**
     * 校验并规范化配方主体与明细。
     *
     * @param recipe 配方
     */
    private void normalize(Recipe recipe) {
        recipe.setName(ValidationUtil.requireText(recipe.getName(), "配方名称", 100));
        recipe.setProductType(ValidationUtil.optionalText(recipe.getProductType(), 50, "产品类型"));
        recipe.setProcessNote(ValidationUtil.optionalText(recipe.getProcessNote(), 2000, "工艺说明"));
        recipe.setRemark(ValidationUtil.optionalText(recipe.getRemark(), 500, "配方备注"));

        List<RecipeItem> items = recipe.getItems();
        if (items == null || items.isEmpty()) {
            throw BizException.badRequest("配方至少需要一项配料");
        }
        if (items.size() > MAX_ITEMS) {
            throw BizException.badRequest("单个配方最多支持 " + MAX_ITEMS + " 项配料");
        }
        Set<String> seen = new LinkedHashSet<>();
        int sort = 0;
        for (RecipeItem item : items) {
            if (TextUtil.isBlank(item.getIngredientName())) {
                throw BizException.badRequest("第 " + (sort + 1) + " 项配料缺少食材名称");
            }
            item.setIngredientName(ValidationUtil.requireText(item.getIngredientName(), "食材名称", 100));
            item.setAmount(ValidationUtil.requireDecimal(
                    item.getAmount() == null ? null : item.getAmount().toPlainString(),
                    "「" + item.getIngredientName() + "」的用量", new BigDecimal("0.001"), MAX_AMOUNT));
            item.setUnit(TextUtil.defaultIfBlank(
                    ValidationUtil.optionalText(item.getUnit(), 20, "用量单位"), "g"));
            if (item.getCostPerUnit() != null && item.getCostPerUnit().signum() < 0) {
                throw BizException.badRequest("单位成本不能为负数");
            }
            item.setSortNo(sort++);
            seen.add(item.getIngredientName());
        }
        if (seen.size() != items.size()) {
            throw BizException.badRequest("同一配方中存在重复食材，请合并后再提交");
        }
    }

    /**
     * 批量写入配料明细。
     *
     * @param conn     数据库连接
     * @param recipeId 配方编号
     * @param items    明细列表
     */
    private void saveItems(java.sql.Connection conn, Long recipeId, List<RecipeItem> items) {
        for (RecipeItem item : items) {
            item.setRecipeId(recipeId);
            recipeItemDao.insert(conn, item);
        }
    }
}
