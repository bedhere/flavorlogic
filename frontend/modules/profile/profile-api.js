/** profile-api：本模块实际接口地址集中定义在这里。
 * 页面传入 suffix（编号/子路径/查询串）、body、options；协议处理交给 shared/core/api.js。
 */
(function (FL) {
  'use strict';
  FL.profileApi = {
    /** POST /api/auth/password */
    createAuthPassword: function (suffix, body, options) { return FL.post('/api/auth/password' + (suffix || ""), body, options); },
    /** GET /api/dashboard */
    getDashboard: function (suffix, options) { return FL.get('/api/dashboard' + (suffix || ""), options); }
  };
}(window.FL));
