/** admin-users-api：本模块实际接口地址集中定义在这里。
 * 页面传入 suffix（编号/子路径/查询串）、body、options；协议处理交给 shared/core/api.js。
 */
(function (FL) {
  'use strict';
  FL.adminUsersApi = {
    /** GET /api/admin/users */
    getAdminUsers: function (suffix, options) { return FL.get('/api/admin/users' + (suffix || ""), options); },
    /** PUT /api/admin/users/ */
    updateAdminUsersById: function (suffix, body, options) { return FL.put('/api/admin/users/' + (suffix || ""), body, options); }
  };
}(window.FL));
