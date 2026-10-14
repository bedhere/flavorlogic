/* ============================================================
   管理后台 · 外部来源文档审核
   ------------------------------------------------------------
   当前页面只处理已经清洗的文本，不在浏览器或 Servlet 中抓取外网。
   文档先进入 REVIEW_PENDING，管理员确认后才可被知识检索接口返回。
   ============================================================ */
(function ($, FL) {
  'use strict';

  var query = { keyword: '', reviewStatus: '', page: 1, pageSize: 10 };
  var rowCache = {};
  var selectedDocs = {};

  FL.start({}, function () {
    FL.layout({
      active: 'admin-knowledge',
      title: '来源审核',
      subtitle: '将公开资料变成可追溯的审核知识；当前阶段只允许人工导入，不自动访问外网',
      crumb: '管理后台 / <strong>来源审核</strong>',
      adminPage: true,
      content: renderShell()
    });
    bind();
    loadSites();
    load();
  });

  function renderShell() {
    return '<div class="knowledge-admin">' +
      '<div class="knowledge-intro"><div><span class="eyebrow">KNOWLEDGE CONTROL</span>' +
      '<h2>来源审核工作台</h2><p>先确认来源，再把公开资料转成可追溯的知识。所有文档和证据都保留审核状态，不会直接改写食材属性。</p></div>' +
      '<div class="knowledge-intro-note"><strong>审核原则</strong><span>来源可靠 · 字段清楚 · 结论可引用</span></div></div>' +
      '<div class="knowledge-toolbar">' +
      '<label class="search" for="knowledgeKeyword"><span aria-hidden="true">⌕</span>' +
      '<input type="search" id="knowledgeKeyword" placeholder="搜索标题、摘要或正文" autocomplete="off" /></label>' +
      '<div class="knowledge-toolbar-actions">' +
      '<select id="knowledgeStatus" aria-label="审核状态">' +
      '<option value="">全部状态</option><option value="REVIEW_PENDING">待审核</option>' +
      '<option value="APPROVED">已通过</option><option value="REJECTED">已拒绝</option>' +
      '<option value="ARCHIVED">已归档</option></select>' +
      '<button class="primary-btn" type="button" id="knowledgeSearchBtn">查询</button>' +
      '<button class="secondary-btn" type="button" id="knowledgeResetBtn">重置</button></div></div>' +
      '<div class="knowledge-stats" id="knowledgeStats"><div class="knowledge-stat"><span>待审核文档</span><strong>—</strong></div><div class="knowledge-stat"><span>已通过文档</span><strong>—</strong></div><div class="knowledge-stat"><span>来源站点</span><strong>—</strong></div></div>' +
      '<div class="knowledge-setup-grid"><section class="panel knowledge-form-panel"><div class="panel-head"><div><h3>人工导入审核草稿</h3><p>适合论文、标准和不能自动采集的资料</p></div>' +
      '<div class="tag">不会访问 URL</div></div><div class="panel-body">' +
      '<div class="form-grid"><div class="field"><label for="knowledgeTitle">标题 <span class="req">*</span></label>' +
      '<input id="knowledgeTitle" maxlength="300" placeholder="资料标题" /></div>' +
      '<div class="field"><label for="knowledgeUrl">原文 URL <span class="req">*</span></label>' +
      '<input id="knowledgeUrl" maxlength="500" placeholder="https://…" /></div>' +
      '<div class="field"><label for="knowledgeSourceType">来源类型</label>' +
      '<select id="knowledgeSourceType"><option value="WEB">WEB</option><option value="API">API</option>' +
      '<option value="PAPER">PAPER</option><option value="STANDARD">STANDARD</option><option value="DATASET">DATASET</option></select></div>' +
      '<div class="field"><label for="knowledgeEvidenceLevel">证据等级</label>' +
      '<select id="knowledgeEvidenceLevel"><option value="UNVERIFIED">UNVERIFIED</option><option value="LITERATURE">LITERATURE</option>' +
      '<option value="STANDARD">STANDARD</option><option value="EXPERIMENT">EXPERIMENT</option><option value="EXPERIENCE">EXPERIENCE</option></select></div>' +
      '<div class="field span-2"><label for="knowledgeSummary">摘要</label><textarea id="knowledgeSummary" maxlength="1000" placeholder="资料用途和范围"></textarea></div>' +
      '<div class="field span-2"><label for="knowledgeContent">清洗后的正文 <span class="req">*</span></label>' +
      '<textarea id="knowledgeContent" maxlength="200000" style="min-height:150px" placeholder="粘贴纯文本或 Markdown；不要直接粘贴整页 HTML"></textarea></div>' +
      '</div><div class="knowledge-form-actions"><button class="primary-btn" type="button" id="knowledgeImportBtn">进入审核队列</button></div>' +
      '</div></section>' +
      '<section class="panel knowledge-form-panel"><div class="panel-head"><div><h3>来源站点白名单</h3><p>登记后仍需单独确认是否允许自动获取</p></div>' +
      '<div class="tag">登记不会立即抓取</div></div><div class="panel-body">' +
      '<div class="form-grid"><div class="field"><label for="siteName">站点名称 <span class="req">*</span></label><input id="siteName" maxlength="120" placeholder="例如：FoodData Central" /></div>' +
      '<div class="field"><label for="siteBaseUrl">根地址 <span class="req">*</span></label><input id="siteBaseUrl" maxlength="500" placeholder="https://…" /></div>' +
      '<div class="field"><label for="siteRobotsUrl">robots 地址</label><input id="siteRobotsUrl" maxlength="500" placeholder="https://…/robots.txt" /></div>' +
      '<div class="field"><label for="siteTermsUrl">条款地址</label><input id="siteTermsUrl" maxlength="500" placeholder="https://…/terms" /></div>' +
      '<div class="field"><label for="siteSourceType">来源类型</label><select id="siteSourceType"><option value="WEB">WEB</option><option value="API">API</option><option value="DATASET">DATASET</option><option value="PAPER">PAPER</option><option value="STANDARD">STANDARD</option></select></div>' +
      '<div class="field"><label for="siteInterval">请求间隔（毫秒）</label><input id="siteInterval" type="number" min="500" max="600000" value="2000" /></div>' +
      '<div class="field span-2"><label for="siteLicense">许可说明</label><input id="siteLicense" maxlength="80" placeholder="按官方 API 和数据集条款使用" /></div>' +
      '<div class="field span-2"><label for="siteNotes">审核备注</label><textarea id="siteNotes" maxlength="500" placeholder="许可、字段映射和人工审核要求"></textarea></div>' +
      '</div><div class="knowledge-form-actions"><button class="primary-btn" type="button" id="siteCreateBtn">登记站点</button></div>' +
      '<div id="siteListRegion" style="margin-top:18px">' + FL.loading('正在加载站点白名单…') + '</div></div></section>' +
      '</div><div id="knowledgeListRegion">' + FL.loading('正在加载来源文档…') + '</div></div>';
  }

  function bind() {
    $('#knowledgeSearchBtn').on('click', function () { collect(); query.page = 1; load(); });
    $('#knowledgeResetBtn').on('click', function () {
      $('#knowledgeKeyword').val(''); $('#knowledgeStatus').val(''); collect(); query.page = 1; load();
    });
    $('#knowledgeKeyword').on('keydown', function (event) {
      if (event.key === 'Enter') { event.preventDefault(); $('#knowledgeSearchBtn').trigger('click'); }
    });
    $('#knowledgeImportBtn').on('click', importDraft);
    $('#siteCreateBtn').on('click', createSite);
    $(document).on('click', '#knowledgeListRegion [data-action="review"]', function () {
      review($(this).data('id'), $(this).data('status'));
    });
    $(document).on('change', '#knowledgeSelectAll', toggleAll);
    $(document).on('change', '#knowledgeListRegion [data-action="select-doc"]', toggleOne);
    $(document).on('click', '#knowledgeListRegion [data-action="bulk-approve"]', bulkApprove);
    $(document).on('click', '#siteListRegion [data-action="site-toggle"]', function () {
      toggleSite($(this).data('id'));
    });
  }

  function collect() {
    query.keyword = $.trim($('#knowledgeKeyword').val() || '');
    query.reviewStatus = $('#knowledgeStatus').val() || '';
  }

  function load() {
    $('#knowledgeListRegion').html(FL.loading('正在加载来源文档…'));
    FL.adminKnowledgeApi.getAdminKnowledgeDocuments('' + FL.qs(query)).then(render)['catch'](function (err) {
      $('#knowledgeListRegion').html(FL.empty('来源文档加载失败', err.message || '请稍后重试。'));
    });
  }

  function loadSites() {
    FL.adminKnowledgeApi.getAdminKnowledgeSites('').then(renderSites)['catch'](function (err) {
      $('#siteListRegion').html(FL.empty('站点白名单加载失败', err.message || '请稍后重试。'));
    });
  }

  function renderSites(list) {
    list = list || [];
    var rows = list.map(function (site) {
      var active = Number(site.status) === 1;
      var allowed = Number(site.crawlAllowed) === 1;
      return '<tr><td><strong>' + FL.escape(site.siteName) + '</strong><br><span style="color:var(--muted)">' + FL.escape(site.baseUrl) + '</span></td>' +
        '<td>' + FL.escape(site.sourceType || 'WEB') + '</td><td>' + (allowed ? '允许' : '人工导入') + '</td>' +
        '<td>' + (active ? '启用' : '停用') + '</td><td>' +
        (active ? '<button type="button" class="link-btn" data-action="site-toggle" data-id="' + FL.escape(site.id) + '">' + (allowed ? '改为人工' : '允许采集') + '</button>' : '<span style="color:var(--muted)">—</span>') +
        '</td></tr>';
    }).join('');
    $('#siteListRegion').html(rows ? '<div class="table-wrap"><table><thead><tr><th>站点</th><th>类型</th><th>自动获取</th><th>状态</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table></div>'
      : '<div class="empty-state">还没有登记来源站点。</div>');
  }

  function render(data) {
    var list = (data && data.list) || [];
    rowCache = {};
    list.forEach(function (item) { rowCache[String(item.id)] = item; });
    Object.keys(selectedDocs).forEach(function (id) {
      if (!rowCache[id] || rowCache[id].reviewStatus !== 'REVIEW_PENDING') { delete selectedDocs[id]; }
    });
    renderStats(data);
    var body = list.length ? '<div class="table-wrap"><table><thead><tr>' +
      '<th class="knowledge-select-col"><input type="checkbox" id="knowledgeSelectAll" aria-label="全选待审核文档" /></th>' +
      '<th>标题 / 来源</th><th>证据等级</th><th>审核状态</th><th>获取时间</th><th>操作</th>' +
      '</tr></thead><tbody>' + list.map(row).join('') + '</tbody></table></div>' +
      '<div class="knowledge-batch-slot"><div class="knowledge-batchbar" id="knowledgeBatchbar" hidden>' +
      '<span><strong id="knowledgeSelectedCount">0</strong> 篇待审核文档已选择</span><button type="button" class="primary-btn" data-action="bulk-approve">统一审核通过</button></div></div>' +
      FL.pager(data, 'knowledgePage') :
      FL.empty('没有来源文档', '先人工导入一篇已清洗的公开资料，文档会从待审核状态开始。');
    $('#knowledgeListRegion').html('<section class="panel knowledge-queue"><div class="panel-head"><div><h3>来源文档队列</h3><p>勾选待审核文档，可一次完成统一审核</p></div>' +
      '<div class="knowledge-queue-meta"><span class="tag">共 ' + FL.escape((data && data.total) || 0) + ' 篇</span><span class="tag">当前页 ' + FL.escape(list.length) + ' 篇</span></div></div>' + body + '</section>');
    syncSelection();
  }

  function row(item) {
    var statusText = { REVIEW_PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝', ARCHIVED: '已归档' }[item.reviewStatus] || item.reviewStatus;
    var id = FL.escape(item.id);
    var pending = item.reviewStatus === 'REVIEW_PENDING';
    var actions = item.reviewStatus === 'REVIEW_PENDING'
      ? '<button type="button" class="link-btn" data-action="review" data-id="' + id + '" data-status="APPROVED">通过</button>' +
        '<button type="button" class="link-btn danger" data-action="review" data-id="' + id + '" data-status="REJECTED">拒绝</button>'
      : '<button type="button" class="link-btn" data-action="review" data-id="' + id + '" data-status="ARCHIVED">归档</button>';
    return '<tr><td class="knowledge-select-col">' + (pending ? '<input type="checkbox" data-action="select-doc" data-id="' + id + '" aria-label="选择 ' + FL.escape(item.title) + '"' + (selectedDocs[String(item.id)] ? ' checked' : '') + ' />' : '<span class="knowledge-check-placeholder">—</span>') + '</td><td><strong>' + FL.escape(item.title) + '</strong><br><a class="knowledge-source-link" href="' + FL.escape(item.url) + '" target="_blank" rel="noopener">' + FL.escape(item.url) + '</a></td>' +
      '<td><span class="knowledge-evidence">' + FL.escape(item.evidenceLevel || 'UNVERIFIED') + '</span></td><td><span class="knowledge-status knowledge-status-' + FL.escape(item.reviewStatus || 'UNKNOWN').toLowerCase() + '">' + FL.escape(statusText) + '</span></td>' +
      '<td>' + FL.escape(item.fetchedAt ? FL.fmtDate(item.fetchedAt) : '—') + '</td><td><div class="row-actions">' + actions + '</div></td></tr>';
  }

  function renderStats(data) {
    var total = Number((data && data.total) || 0);
    var pending = (data && data.list || []).filter(function (item) { return item.reviewStatus === 'REVIEW_PENDING'; }).length;
    var approved = (data && data.list || []).filter(function (item) { return item.reviewStatus === 'APPROVED'; }).length;
    $('#knowledgeStats').html('<div class="knowledge-stat is-pending"><span>当前页待审核</span><strong>' + pending + '</strong><small>需要人工确认</small></div>' +
      '<div class="knowledge-stat is-approved"><span>当前页已通过</span><strong>' + approved + '</strong><small>可进入检索</small></div>' +
      '<div class="knowledge-stat"><span>来源文档总数</span><strong>' + total + '</strong><small>按当前筛选统计</small></div>');
  }

  function toggleAll() {
    var checked = $('#knowledgeSelectAll').prop('checked');
    $('#knowledgeListRegion [data-action="select-doc"]').each(function () {
      $(this).prop('checked', checked);
      var id = String($(this).data('id'));
      if (checked) { selectedDocs[id] = true; } else { delete selectedDocs[id]; }
    });
    syncSelection();
  }

  function toggleOne() {
    var id = String($(this).data('id'));
    if ($(this).prop('checked')) { selectedDocs[id] = true; } else { delete selectedDocs[id]; }
    syncSelection();
  }

  function syncSelection() {
    var ids = Object.keys(selectedDocs).filter(function (id) { return rowCache[id] && rowCache[id].reviewStatus === 'REVIEW_PENDING'; });
    var $bar = $('#knowledgeBatchbar');
    if (!$bar.length) { return; }
    $('#knowledgeSelectedCount').text(ids.length);
    $bar.prop('hidden', ids.length === 0);
    var $checks = $('#knowledgeListRegion [data-action="select-doc"]');
    var checked = $checks.length > 0 && $checks.filter(':checked').length === $checks.length;
    $('#knowledgeSelectAll').prop('checked', checked).prop('indeterminate', !checked && ids.length > 0);
  }

  function bulkApprove() {
    var ids = Object.keys(selectedDocs).filter(function (id) { return rowCache[id] && rowCache[id].reviewStatus === 'REVIEW_PENDING'; });
    if (!ids.length) { return; }
    FL.confirm('将已选的 ' + ids.length + ' 篇文档统一标记为已通过？', { title: '统一审核来源文档', okText: '统一审核', danger: false })
      .then(function (ok) {
        if (!ok) { return null; }
        return ids.reduce(function (chain, id) {
          return chain.then(function () { return FL.adminKnowledgeApi.updateAdminKnowledgeDocumentsById('' + id, { reviewStatus: 'APPROVED', reviewNote: '管理员统一审核通过' }); });
        }, Promise.resolve());
      }).then(function (result) {
        if (result === null) { return; }
        selectedDocs = {};
        FL.toast('已统一审核通过 ' + ids.length + ' 篇文档', 'success', '审核完成');
        load();
      })['catch'](function (err) { FL.toast(err.message || '统一审核失败', 'error', '未完成'); });
  }

  window.knowledgePage = function (page) { query.page = page; load(); };

  function importDraft() {
    var title = $.trim($('#knowledgeTitle').val() || '');
    var url = $.trim($('#knowledgeUrl').val() || '');
    var contentText = $.trim($('#knowledgeContent').val() || '');
    if (!title || !url || !contentText) { FL.toast('标题、原文 URL 和正文不能为空', 'error', '无法导入'); return; }
    var $btn = $('#knowledgeImportBtn').prop('disabled', true);
    FL.adminKnowledgeApi.createAdminKnowledgeDocuments('', {
      title: title, url: url, contentText: contentText,
      summary: $.trim($('#knowledgeSummary').val() || ''),
      sourceType: $('#knowledgeSourceType').val(), evidenceLevel: $('#knowledgeEvidenceLevel').val()
    }).then(function () {
      $('#knowledgeTitle,#knowledgeUrl,#knowledgeSummary,#knowledgeContent').val('');
      FL.toast('文档已进入待审核队列', 'success', '导入成功');
      query.page = 1; load();
    })['catch'](function (err) { FL.toast(err.message || '导入失败', 'error', '未完成'); })['finally'](function () { $btn.prop('disabled', false); });
  }

  function createSite() {
    var name = $.trim($('#siteName').val() || '');
    var baseUrl = $.trim($('#siteBaseUrl').val() || '');
    if (!name || !baseUrl) { FL.toast('站点名称和根地址不能为空', 'error', '无法登记'); return; }
    var $btn = $('#siteCreateBtn').prop('disabled', true);
    FL.adminKnowledgeApi.createAdminKnowledgeSites('', {
      siteName: name, baseUrl: baseUrl,
      robotsUrl: $.trim($('#siteRobotsUrl').val() || ''),
      termsUrl: $.trim($('#siteTermsUrl').val() || ''),
      licenseType: $.trim($('#siteLicense').val() || ''),
      sourceType: $('#siteSourceType').val(), crawlAllowed: 0,
      requestIntervalMs: Number($('#siteInterval').val() || 2000), status: 1,
      notes: $.trim($('#siteNotes').val() || '')
    }).then(function () {
      $('#siteName,#siteBaseUrl,#siteRobotsUrl,#siteTermsUrl,#siteLicense,#siteNotes').val('');
      FL.toast('站点已登记，当前仅允许人工导入', 'success', '登记成功');
      loadSites();
    })['catch'](function (err) { FL.toast(err.message || '登记失败', 'error', '未完成'); })['finally'](function () { $btn.prop('disabled', false); });
  }

  function toggleSite(id) {
    FL.adminKnowledgeApi.getAdminKnowledgeSitesById('' + id).then(function (site) {
      site.crawlAllowed = Number(site.crawlAllowed) === 1 ? 0 : 1;
      return FL.adminKnowledgeApi.updateAdminKnowledgeSitesById('' + id, site);
    }).then(function () {
      FL.toast('站点获取策略已更新', 'success', '操作完成');
      loadSites();
    })['catch'](function (err) { FL.toast(err.message || '更新失败', 'error', '未完成'); });
  }

  function review(id, status) {
    var item = rowCache[String(id)] || {};
    var label = { APPROVED: '通过', REJECTED: '拒绝', ARCHIVED: '归档' }[status] || status;
    FL.confirm(label + '「' + (item.title || '该文档') + '」？', { title: '审核来源文档', okText: label, danger: status === 'REJECTED' })
      .then(function (ok) {
        if (!ok) { return; }
        return FL.adminKnowledgeApi.updateAdminKnowledgeDocumentsById('' + id, { reviewStatus: status, reviewNote: '' });
      }).then(function (result) { if (result) { FL.toast('审核状态已更新', 'success', '操作完成'); load(); } })
      ['catch'](function (err) { FL.toast(err.message || '审核失败', 'error', '未完成'); });
  }
})(jQuery, window.FL);
