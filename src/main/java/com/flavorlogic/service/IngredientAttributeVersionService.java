package com.flavorlogic.service;

import com.flavorlogic.model.IngredientAttributeVersion;

import java.util.List;

/** 食材属性版本录入、审核和显式应用业务。 */
public interface IngredientAttributeVersionService {

    List<IngredientAttributeVersion> list(Long ingredientId, String dimension, String reviewStatus);

    IngredientAttributeVersion create(IngredientAttributeVersion version);

    IngredientAttributeVersion review(Long operatorId, Long id, String status, String note);

    IngredientAttributeVersion apply(Long operatorId, Long id);
}
