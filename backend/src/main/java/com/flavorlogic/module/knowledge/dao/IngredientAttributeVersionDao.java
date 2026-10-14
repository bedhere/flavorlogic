package com.flavorlogic.module.knowledge.dao;

import com.flavorlogic.module.knowledge.model.IngredientAttributeVersion;

import java.sql.Connection;
import java.util.List;

/** 食材属性版本数据访问接口。 */
public interface IngredientAttributeVersionDao {

    List<IngredientAttributeVersion> list(Connection conn, Long ingredientId, String dimension, String reviewStatus);

    IngredientAttributeVersion findById(Connection conn, Long id);

    int nextVersionNo(Connection conn, Long ingredientId, String dimension);

    long insert(Connection conn, IngredientAttributeVersion version);

    int updateReview(Connection conn, Long id, String status, Long reviewerId, String note);
}
