/** admin-articles-api：本模块实际接口地址集中定义在这里。
 * 页面传入 suffix（编号/子路径/查询串）、body、options；协议处理交给 shared/core/api.js。
 */
(function (FL) {
  'use strict';
  FL.adminArticlesApi = {
    /** GET /api/admin/articles/categories */
    getAdminArticlesCategories: function (suffix, options) { return FL.get('/api/admin/articles/categories' + (suffix || ""), options); },
    /** GET /api/admin/articles */
    getAdminArticles: function (suffix, options) { return FL.get('/api/admin/articles' + (suffix || ""), options); },
    /** PUT /api/admin/articles/ */
    updateAdminArticlesById: function (suffix, body, options) { return FL.put('/api/admin/articles/' + (suffix || ""), body, options); },
    /** DELETE /api/admin/articles/ */
    removeAdminArticlesById: function (suffix, options) { return FL.del('/api/admin/articles/' + (suffix || ""), options); },
    /** POST /api/admin/articles */
    createAdminArticles: function (suffix, body, options) { return FL.post('/api/admin/articles' + (suffix || ""), body, options); },
    /** POST /api/admin/articles/sync */
    createAdminArticlesSync: function (suffix, body, options) { return FL.post('/api/admin/articles/sync' + (suffix || ""), body, options); }
  };
}(window.FL));
