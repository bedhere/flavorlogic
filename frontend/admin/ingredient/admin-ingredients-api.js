/** admin-ingredients-api：本模块实际接口地址集中定义在这里。
 * 页面传入 suffix（编号/子路径/查询串）、body、options；协议处理交给 shared/core/api.js。
 */
(function (FL) {
  'use strict';
  FL.adminIngredientsApi = {
    /** GET /api/admin/ingredients/categories */
    getAdminIngredientsCategories: function (suffix, options) { return FL.get('/api/admin/ingredients/categories' + (suffix || ""), options); },
    /** GET /api/admin/ingredients */
    getAdminIngredients: function (suffix, options) { return FL.get('/api/admin/ingredients' + (suffix || ""), options); },
    /** DELETE /api/admin/ingredients/ */
    removeAdminIngredientsById: function (suffix, options) { return FL.del('/api/admin/ingredients/' + (suffix || ""), options); },
    /** GET /api/rules */
    getRules: function (suffix, options) { return FL.get('/api/rules' + (suffix || ""), options); },
    /** PUT /api/admin/ingredients/ */
    updateAdminIngredientsById: function (suffix, body, options) { return FL.put('/api/admin/ingredients/' + (suffix || ""), body, options); },
    /** POST /api/admin/ingredients */
    createAdminIngredients: function (suffix, body, options) { return FL.post('/api/admin/ingredients' + (suffix || ""), body, options); }
  };
}(window.FL));
