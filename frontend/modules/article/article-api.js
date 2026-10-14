/** article-api：本模块实际接口地址集中定义在这里。
 * 页面传入 suffix（编号/子路径/查询串）、body、options；协议处理交给 shared/core/api.js。
 */
(function (FL) {
  'use strict';
  FL.articleApi = {
    /** GET /api/articles/ */
    getArticlesById: function (suffix, options) { return FL.get('/api/articles/' + (suffix || ""), options); },
    /** GET /api/categories */
    getCategories: function (suffix, options) { return FL.get('/api/categories' + (suffix || ""), options); },
    /** GET /api/articles */
    getArticles: function (suffix, options) { return FL.get('/api/articles' + (suffix || ""), options); }
  };
}(window.FL));
