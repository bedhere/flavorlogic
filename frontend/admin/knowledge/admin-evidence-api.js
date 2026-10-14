/** admin-evidence-api：本模块实际接口地址集中定义在这里。
 * 页面传入 suffix（编号/子路径/查询串）、body、options；协议处理交给 shared/core/api.js。
 */
(function (FL) {
  'use strict';
  FL.adminEvidenceApi = {
    /** GET /api/admin/knowledge/claims */
    getAdminKnowledgeClaims: function (suffix, options) { return FL.get('/api/admin/knowledge/claims' + (suffix || ""), options); },
    /** GET /api/rules */
    getRules: function (suffix, options) { return FL.get('/api/rules' + (suffix || ""), options); },
    /** GET /api/admin/knowledge/evidence-links */
    getAdminKnowledgeEvidenceLinks: function (suffix, options) { return FL.get('/api/admin/knowledge/evidence-links' + (suffix || ""), options); },
    /** PUT /api/admin/knowledge/claims/ */
    updateAdminKnowledgeClaimsById: function (suffix, body, options) { return FL.put('/api/admin/knowledge/claims/' + (suffix || ""), body, options); },
    /** GET /api/admin/knowledge/attributes */
    getAdminKnowledgeAttributes: function (suffix, options) { return FL.get('/api/admin/knowledge/attributes' + (suffix || ""), options); },
    /** POST /api/admin/knowledge/claims */
    createAdminKnowledgeClaims: function (suffix, body, options) { return FL.post('/api/admin/knowledge/claims' + (suffix || ""), body, options); },
    /** POST /api/admin/knowledge/attributes */
    createAdminKnowledgeAttributes: function (suffix, body, options) { return FL.post('/api/admin/knowledge/attributes' + (suffix || ""), body, options); },
    /** POST /api/admin/knowledge/evidence-links */
    createAdminKnowledgeEvidenceLinks: function (suffix, body, options) { return FL.post('/api/admin/knowledge/evidence-links' + (suffix || ""), body, options); },
    /** DELETE /api/admin/knowledge/evidence-links/ */
    removeAdminKnowledgeEvidenceLinksById: function (suffix, options) { return FL.del('/api/admin/knowledge/evidence-links/' + (suffix || ""), options); },
    /** PUT /api/admin/knowledge/attributes/ */
    updateAdminKnowledgeAttributesById: function (suffix, body, options) { return FL.put('/api/admin/knowledge/attributes/' + (suffix || ""), body, options); }
  };
}(window.FL));
