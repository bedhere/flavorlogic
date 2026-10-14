/** recipe-api：本模块实际接口地址集中定义在这里。
 * 页面传入 suffix（编号/子路径/查询串）、body、options；协议处理交给 shared/core/api.js。
 */
(function (FL) {
  'use strict';
  FL.recipeApi = {
    /** GET /api/ingredients */
    getIngredients: function (suffix, options) { return FL.get('/api/ingredients' + (suffix || ""), options); },
    /** GET /api/recipes/ */
    getRecipesById: function (suffix, options) { return FL.get('/api/recipes/' + (suffix || ""), options); },
    /** PUT /api/recipes/ */
    updateRecipesById: function (suffix, body, options) { return FL.put('/api/recipes/' + (suffix || ""), body, options); },
    /** POST /api/recipes */
    createRecipes: function (suffix, body, options) { return FL.post('/api/recipes' + (suffix || ""), body, options); },
    /** GET /api/recipes */
    getRecipes: function (suffix, options) { return FL.get('/api/recipes' + (suffix || ""), options); },
    /** GET /api/regions */
    getRegions: function (suffix, options) { return FL.get('/api/regions' + (suffix || ""), options); },
    /** GET /api/recipes/profiles */
    getRecipesProfiles: function (suffix, options) { return FL.get('/api/recipes/profiles' + (suffix || ""), options); },
    /** DELETE /api/recipes/ */
    removeRecipesById: function (suffix, options) { return FL.del('/api/recipes/' + (suffix || ""), options); }
  };
}(window.FL));
