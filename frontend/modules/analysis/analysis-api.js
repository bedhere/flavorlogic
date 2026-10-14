/** analysis-api：本模块实际接口地址集中定义在这里。
 * 页面传入 suffix（编号/子路径/查询串）、body、options；协议处理交给 shared/core/api.js。
 */
(function (FL) {
  'use strict';
  FL.analysisApi = {
    /** GET /api/analysis/history */
    getAnalysisHistory: function (suffix, options) { return FL.get('/api/analysis/history' + (suffix || ""), options); },
    /** DELETE /api/analysis/ */
    removeAnalysisById: function (suffix, options) { return FL.del('/api/analysis/' + (suffix || ""), options); },
    /** GET /api/analysis/ */
    getAnalysisById: function (suffix, options) { return FL.get('/api/analysis/' + (suffix || ""), options); },
    /** POST /api/analysis/ */
    saveFeedback: function (suffix, body, options) { return FL.post('/api/analysis/' + (suffix || ""), body, options); },
    /** GET /api/ingredients */
    getIngredients: function (suffix, options) { return FL.get('/api/ingredients' + (suffix || ""), options); },
    /** GET /api/regions */
    getRegions: function (suffix, options) { return FL.get('/api/regions' + (suffix || ""), options); },
    /** GET /api/recipes */
    getRecipes: function (suffix, options) { return FL.get('/api/recipes' + (suffix || ""), options); },
    /** GET /api/recipes/ */
    getRecipesById: function (suffix, options) { return FL.get('/api/recipes/' + (suffix || ""), options); },
    /** POST /api/analysis */
    createAnalysis: function (suffix, body, options) { return FL.post('/api/analysis' + (suffix || ""), body, options); }
  };
}(window.FL));
