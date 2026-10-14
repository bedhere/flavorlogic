/** admin-knowledge-api：本模块实际接口地址集中定义在这里。
 * 页面传入 suffix（编号/子路径/查询串）、body、options；协议处理交给 shared/core/api.js。
 */
(function (FL) {
  'use strict';
  FL.adminKnowledgeApi = {
    /** GET /api/admin/knowledge/documents */
    getAdminKnowledgeDocuments: function (suffix, options) { return FL.get('/api/admin/knowledge/documents' + (suffix || ""), options); },
    /** GET /api/admin/knowledge/sites */
    getAdminKnowledgeSites: function (suffix, options) { return FL.get('/api/admin/knowledge/sites' + (suffix || ""), options); },
    /** PUT /api/admin/knowledge/documents/ */
    updateAdminKnowledgeDocumentsById: function (suffix, body, options) { return FL.put('/api/admin/knowledge/documents/' + (suffix || ""), body, options); },
    /** POST /api/admin/knowledge/documents */
    createAdminKnowledgeDocuments: function (suffix, body, options) { return FL.post('/api/admin/knowledge/documents' + (suffix || ""), body, options); },
    /** POST /api/admin/knowledge/sites */
    createAdminKnowledgeSites: function (suffix, body, options) { return FL.post('/api/admin/knowledge/sites' + (suffix || ""), body, options); },
    /** GET /api/admin/knowledge/sites/ */
    getAdminKnowledgeSitesById: function (suffix, options) { return FL.get('/api/admin/knowledge/sites/' + (suffix || ""), options); },
    /** PUT /api/admin/knowledge/sites/ */
    updateAdminKnowledgeSitesById: function (suffix, body, options) { return FL.put('/api/admin/knowledge/sites/' + (suffix || ""), body, options); }
  };
}(window.FL));
