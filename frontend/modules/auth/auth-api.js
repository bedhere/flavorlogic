/** auth-api：本模块实际接口地址集中定义在这里。
 * 页面传入 suffix（编号/子路径/查询串）、body、options；协议处理交给 shared/core/api.js。
 */
(function (FL) {
  'use strict';
  FL.authApi = {
    /** GET /api/auth/me */
    getAuthMe: function (suffix, options) { return FL.get('/api/auth/me' + (suffix || ""), options); },
    /** POST /api/auth/login */
    createAuthLogin: function (suffix, body, options) { return FL.post('/api/auth/login' + (suffix || ""), body, options); },
    /** POST /api/auth/register */
    createAuthRegister: function (suffix, body, options) { return FL.post('/api/auth/register' + (suffix || ""), body, options); }
  };
}(window.FL));
