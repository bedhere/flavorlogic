package com.flavorlogic.module.ingredient.service;

import com.flavorlogic.module.ingredient.model.Ingredient;
import com.flavorlogic.common.util.PageResult;
import java.util.List;

/** Ingredient 业务接口。 */
public interface IngredientService {
    List<Ingredient> listEnabledIngredients();

    List<String> ingredientCategories();

    PageResult<Ingredient> adminPageIngredients(String keyword, String category, Integer status,
                                                       int page, int pageSize);

    Ingredient adminCreateIngredient(Ingredient ingredient);

    Ingredient adminUpdateIngredient(Ingredient ingredient);

    void adminUpdateIngredientStatus(Long id, int status);

    long countIngredients();

    long countEnabledIngredients();
}
