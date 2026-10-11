/* 管理后台 · 证据声明与食材属性版本 */
(function ($, FL) {
  'use strict';

  var selectedClaims = {};
  var claimCache = {};

  FL.start({}, function () {
    FL.layout({
      active: 'admin-evidence',
      title: '证据与属性',
      subtitle: '把来源文档拆成可引用声明，再由管理员决定是否应用到食材主表',
      crumb: '管理后台 / <strong>证据与属性</strong>',
      adminPage: true,
      content: renderShell()
    });
    bind();
    loadClaims();
    loadAttributes();
    loadLinkData();
  });

  function renderShell() {
    return '<section class="panel"><div class="panel-head"><h3>新增证据声明</h3><div class="tag">来源文档需先通过审核</div></div><div class="panel-body">' +
      '<div class="form-grid"><div class="field"><label for="claimDocumentId">来源文档编号 <span class="req">*</span></label><input id="claimDocumentId" type="number" min="1" /></div>' +
      '<div class="field"><label for="claimIngredientId">食材编号</label><input id="claimIngredientId" type="number" min="1" placeholder="背景知识可留空" /></div>' +
      '<div class="field"><label for="claimDimension">风味维度</label><select id="claimDimension"><option value="">不关联维度</option>' + dimensions() + '</select></div>' +
      '<div class="field"><label for="claimType">声明类型</label><select id="claimType"><option value="MEASUREMENT">MEASUREMENT</option><option value="PRODUCT_LABEL">PRODUCT_LABEL（产品标签）</option><option value="KNOWLEDGE">KNOWLEDGE</option><option value="PAIRING">PAIRING</option><option value="LEGAL">LEGAL</option></select></div>' +
      '<div class="field span-2"><label for="claimText">结构化结论 <span class="req">*</span></label><textarea id="claimText" maxlength="2000"></textarea></div>' +
      '<div class="field span-2"><label for="claimQuote">原文短摘录</label><textarea id="claimQuote" maxlength="3000"></textarea></div>' +
      '<div class="field"><label for="claimEvidenceLevel">证据等级</label><select id="claimEvidenceLevel"><option>LITERATURE</option><option>STANDARD</option><option>EXPERIMENT</option><option>EXPERIENCE</option><option>UNVERIFIED</option></select></div>' +
      '<div class="field"><label for="claimMethod">方法</label><input id="claimMethod" maxlength="255" placeholder="感官评价 / 检测方法" /></div></div>' +
      '<div style="display:flex;justify-content:flex-end;margin-top:12px"><button class="primary-btn" type="button" id="claimCreateBtn">进入声明审核</button></div></div></section>' +
      '<section class="panel" style="margin-top:18px"><div class="panel-head"><h3>新增食材属性版本</h3><div class="tag">审核通过后仍需显式应用</div></div><div class="panel-body">' +
      '<div class="form-grid"><div class="field"><label for="attrIngredientId">食材编号 <span class="req">*</span></label><input id="attrIngredientId" type="number" min="1" /></div>' +
      '<div class="field"><label for="attrDocumentId">来源文档编号 <span class="req">*</span></label><input id="attrDocumentId" type="number" min="1" /></div>' +
      '<div class="field"><label for="attrDimension">风味维度 <span class="req">*</span></label><select id="attrDimension">' + dimensions() + '</select></div>' +
      '<div class="field"><label for="attrValue">相对值（0-100） <span class="req">*</span></label><input id="attrValue" type="number" min="0" max="100" step="0.01" /></div>' +
      '<div class="field"><label for="attrBasis">数据口径</label><input id="attrBasis" maxlength="50" placeholder="鲜重 / 干重" /></div>' +
      '<div class="field"><label for="attrMethod">测量方法</label><input id="attrMethod" maxlength="255" /></div>' +
      '<div class="field"><label for="attrConcentration">测试浓度</label><input id="attrConcentration" maxlength="100" /></div>' +
      '<div class="field"><label for="attrTemperature">测试温度</label><input id="attrTemperature" maxlength="50" /></div>' +
      '</div><div style="display:flex;justify-content:flex-end;margin-top:12px"><button class="primary-btn" type="button" id="attrCreateBtn">进入属性审核</button></div></div></section>' +
      '<section class="panel evidence-link-panel" style="margin-top:18px"><div class="panel-head"><div><h3>关联规则证据</h3><p>只有已批准的证据声明才能关联到启用规则</p></div><div class="tag">规则引擎可追溯</div></div><div class="panel-body">' +
      '<div class="evidence-link-grid"><div class="field"><label for="linkClaimId">已批准证据声明 <span class="req">*</span></label><select id="linkClaimId"><option value="">正在加载…</option></select></div>' +
      '<div class="field"><label for="linkRuleId">风味规则 <span class="req">*</span></label><select id="linkRuleId"><option value="">正在加载…</option></select></div>' +
      '<div class="field"><label for="linkRelationType">关联类型</label><select id="linkRelationType"><option value="SUPPORTS">SUPPORTS（支持）</option><option value="LIMITS">LIMITS（限制）</option><option value="CONTEXT_ONLY">CONTEXT_ONLY（背景）</option></select></div></div>' +
      '<div class="knowledge-form-actions"><button class="primary-btn" type="button" id="linkCreateBtn">保存规则关联</button></div>' +
      '<div id="linkHint" class="evidence-link-hint">正在加载已批准声明和启用规则…</div><div id="linkListRegion"></div></div></section>' +
      '<div id="claimListRegion" style="margin-top:18px">' + FL.loading('正在加载证据声明…') + '</div>' +
      '<div id="attrListRegion" style="margin-top:18px">' + FL.loading('正在加载属性版本…') + '</div>';
  }

  function dimensions() {
    return ['sweet', 'salty', 'sour', 'bitter', 'umami', 'spicy', 'numbing', 'fat_aroma']
      .map(function (value) { return '<option value="' + value + '">' + value + '</option>'; }).join('');
  }

  function bind() {
    $('#claimCreateBtn').on('click', createClaim);
    $('#attrCreateBtn').on('click', createAttribute);
    $('#linkCreateBtn').on('click', createLink);
    $(document).on('change', '#claimSelectAll', toggleAllClaims);
    $(document).on('change', '#claimListRegion [data-action="select-claim"]', toggleClaim);
    $(document).on('click', '#claimListRegion [data-action="bulk-approve-claims"]', bulkApproveClaims);
    $(document).on('click', '#claimListRegion [data-action="claim-review"]', function () {
      reviewClaim($(this).data('id'), $(this).data('status'));
    });
    $(document).on('click', '#attrListRegion [data-action="attr-review"]', function () {
      reviewAttribute($(this).data('id'), $(this).data('status'));
    });
    $(document).on('click', '#attrListRegion [data-action="attr-apply"]', function () {
      applyAttribute($(this).data('id'));
    });
    $(document).on('click', '#linkListRegion [data-action="unlink-evidence"]', function () {
      unlinkEvidence($(this).data('id'));
    });
  }

  function loadClaims() {
    FL.get('/api/admin/knowledge/claims?reviewStatus=REVIEW_PENDING').then(function (list) {
      claimCache = {};
      (list || []).forEach(function (item) { claimCache[String(item.id)] = item; });
      Object.keys(selectedClaims).forEach(function (id) {
        if (!claimCache[id]) { delete selectedClaims[id]; }
      });
      var rows = (list || []).map(function (item) {
        return '<tr><td class="claim-select-col"><input type="checkbox" data-action="select-claim" data-id="' + FL.escape(item.id) + '" aria-label="选择声明 #' + FL.escape(item.id) + '"' + (selectedClaims[String(item.id)] ? ' checked' : '') + ' /></td>' +
          '<td>#' + FL.escape(item.id) + '<br>' + FL.escape(item.documentTitle || '') + '</td>' +
          '<td>' + FL.escape(item.ingredientName || '背景知识') + '</td><td>' + FL.escape(item.dimension || '—') + '</td>' +
          '<td>' + FL.escape(item.claimText) + '</td><td><div class="evidence-row-actions"><button class="link-btn" data-action="claim-review" data-id="' + FL.escape(item.id) + '" data-status="APPROVED">通过</button>' +
          '<button class="link-btn danger" data-action="claim-review" data-id="' + FL.escape(item.id) + '" data-status="REJECTED">拒绝</button></div></td></tr>';
      }).join('');
      $('#claimListRegion').html('<section class="panel"><div class="panel-head evidence-panel-head"><div class="evidence-panel-title"><h3>待审核证据声明</h3><p>审核通过后，声明才能进入规则证据关联</p></div>' +
        (rows ? '<div class="tag">共 ' + FL.escape(list.length) + ' 条</div>' : '') + '</div>' +
        (rows ? '<div class="table-wrap"><table class="evidence-claim-table"><thead><tr><th class="claim-select-col"><input type="checkbox" id="claimSelectAll" aria-label="全选待审核声明" /></th><th>来源</th><th>食材</th><th>维度</th><th>结论</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table></div>' +
          '<div class="knowledge-batch-slot"><div class="knowledge-batchbar" id="claimBatchbar" hidden><span><strong id="claimSelectedCount">0</strong> 条声明已选择</span><button type="button" class="primary-btn" data-action="bulk-approve-claims">统一审核通过</button></div></div>' : FL.empty('暂无待审核声明', '可以先从已审核来源文档创建一条证据声明。')) + '</section>');
      syncClaimSelection();
    })['catch'](loadError('#claimListRegion', '证据声明加载失败'));
  }

  function loadLinkData() {
    Promise.all([
      FL.get('/api/admin/knowledge/claims?reviewStatus=APPROVED'),
      FL.get('/api/rules'),
      FL.get('/api/admin/knowledge/evidence-links')
    ]).then(function (result) {
      var claims = result[0] || [];
      var rules = result[1] || [];
      var links = result[2] || [];
      var claimOptions = '<option value="">请选择已批准声明</option>' + claims.map(function (item) {
        return '<option value="' + FL.escape(item.id) + '">#' + FL.escape(item.id) + ' · ' + FL.escape(item.claimText) + '</option>';
      }).join('');
      var ruleOptions = '<option value="">请选择风味规则</option>' + rules.map(function (item) {
        return '<option value="' + FL.escape(item.id) + '">' + FL.escape(item.ruleCode) + ' · ' + FL.escape(item.ruleName) + '</option>';
      }).join('');
      $('#linkClaimId').html(claimOptions).prop('disabled', !claims.length);
      $('#linkRuleId').html(ruleOptions).prop('disabled', !rules.length);
      $('#linkCreateBtn').prop('disabled', !claims.length || !rules.length);
      $('#linkHint').text(claims.length
        ? '已批准声明 ' + claims.length + ' 条，启用规则 ' + rules.length + ' 条；保存后会写入 rule_evidence。'
        : '当前没有已批准声明，请先在下方审核通过证据声明。');
      var rows = links.map(function (item) {
        return '<tr><td>' + FL.escape(item.ruleCode || ('规则 #' + item.ruleId)) + '</td><td>#' + FL.escape(item.evidenceClaimId) + '</td>' +
          '<td>' + FL.escape(item.relationType) + '</td><td>' + FL.escape(item.claimText || '') + '</td>' +
          '<td><button type="button" class="link-btn danger" data-action="unlink-evidence" data-id="' + FL.escape(item.id) + '">解除</button></td></tr>';
      }).join('');
      $('#linkListRegion').html(rows ? '<div class="table-wrap"><table class="evidence-link-table"><thead><tr><th>规则</th><th>声明</th><th>关系</th><th>声明内容</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table></div>' : '<div class="evidence-link-empty">还没有规则证据关联。</div>');
    })['catch'](function (err) {
      $('#linkHint').text(err.message || '规则证据关联加载失败');
      $('#linkCreateBtn').prop('disabled', true);
    });
  }

  function toggleAllClaims() {
    var checked = $('#claimSelectAll').prop('checked');
    $('#claimListRegion [data-action="select-claim"]').each(function () {
      $(this).prop('checked', checked);
      var id = String($(this).data('id'));
      if (checked) { selectedClaims[id] = true; } else { delete selectedClaims[id]; }
    });
    syncClaimSelection();
  }

  function toggleClaim() {
    var id = String($(this).data('id'));
    if ($(this).prop('checked')) { selectedClaims[id] = true; } else { delete selectedClaims[id]; }
    syncClaimSelection();
  }

  function syncClaimSelection() {
    var ids = Object.keys(selectedClaims).filter(function (id) { return claimCache[id]; });
    var $bar = $('#claimBatchbar');
    if (!$bar.length) { return; }
    $('#claimSelectedCount').text(ids.length);
    $bar.prop('hidden', ids.length === 0);
    var $checks = $('#claimListRegion [data-action="select-claim"]');
    var checked = $checks.length > 0 && $checks.filter(':checked').length === $checks.length;
    $('#claimSelectAll').prop('checked', checked).prop('indeterminate', !checked && ids.length > 0);
  }

  function bulkApproveClaims() {
    var ids = Object.keys(selectedClaims).filter(function (id) { return claimCache[id]; });
    if (!ids.length) { return; }
    FL.confirm('将已选的 ' + ids.length + ' 条声明统一标记为已通过？', { title: '统一审核证据声明', okText: '统一审核' })
      .then(function (ok) {
        if (!ok) { return null; }
        return ids.reduce(function (chain, id) {
          return chain.then(function () {
            return FL.put('/api/admin/knowledge/claims/' + id, { reviewStatus: 'APPROVED', reviewNote: '管理员统一审核通过' });
          });
        }, Promise.resolve());
      }).then(function (result) {
        if (result === null) { return; }
        selectedClaims = {};
        FL.toast('已统一审核通过 ' + ids.length + ' 条证据声明', 'success', '审核完成');
        loadClaims();
        loadLinkData();
      })['catch'](function (err) { FL.toast(err.message || '统一审核失败', 'error', '未完成'); });
  }

  function loadAttributes() {
    Promise.all([
      FL.get('/api/admin/knowledge/attributes?reviewStatus=REVIEW_PENDING'),
      FL.get('/api/admin/knowledge/attributes?reviewStatus=APPROVED')
    ]).then(function (result) {
      var pending = result[0] || [];
      var approved = result[1] || [];
      var pendingRows = pending.map(function (item) {
        return '<tr><td>#' + FL.escape(item.id) + '</td><td>' + FL.escape(item.ingredientName || item.ingredientId) + '</td>' +
          '<td>' + FL.escape(item.dimension) + '</td><td>' + FL.escape(item.value) + '</td><td>' + FL.escape(item.sourceDocumentTitle || item.sourceDocumentId) + '</td>' +
          '<td><div class="evidence-row-actions"><button class="link-btn" data-action="attr-review" data-id="' + FL.escape(item.id) + '" data-status="APPROVED">通过</button>' +
          '<button class="link-btn danger" data-action="attr-review" data-id="' + FL.escape(item.id) + '" data-status="REJECTED">拒绝</button></div></td></tr>';
      }).join('');
      var approvedRows = approved.map(function (item) {
        return '<tr><td>#' + FL.escape(item.id) + '</td><td>' + FL.escape(item.ingredientName || item.ingredientId) + '</td>' +
          '<td>' + FL.escape(item.dimension) + '</td><td>' + FL.escape(item.value) + '</td><td>' + FL.escape(item.sourceDocumentTitle || item.sourceDocumentId) + '</td>' +
          '<td><div class="evidence-row-actions"><span class="status">已批准</span><button class="link-btn" data-action="attr-apply" data-id="' + FL.escape(item.id) + '">应用到食材</button></div></td></tr>';
      }).join('');
      var pendingPanel = '<section class="panel"><div class="panel-head"><div><h3>待审核食材属性版本</h3><p>审核通过后仍需显式应用，才会更新 ingredient 主表</p></div></div>' +
        (pendingRows ? '<div class="table-wrap"><table><thead><tr><th>编号</th><th>食材</th><th>维度</th><th>数值</th><th>来源</th><th>操作</th></tr></thead><tbody>' + pendingRows + '</tbody></table></div>' : FL.empty('暂无待审核属性', '可以从已审核来源文档创建属性版本。')) + '</section>';
      var approvedPanel = '<section class="panel" style="margin-top:18px"><div class="panel-head"><div><h3>已批准属性版本</h3><p>应用动作会把该版本写入食材主表，并保留来源文档编号</p></div></div>' +
        (approvedRows ? '<div class="table-wrap"><table><thead><tr><th>编号</th><th>食材</th><th>维度</th><th>数值</th><th>来源</th><th>操作</th></tr></thead><tbody>' + approvedRows + '</tbody></table></div>' : FL.empty('暂无已批准属性', '')) + '</section>';
      $('#attrListRegion').html(pendingPanel + approvedPanel);
    })['catch'](loadError('#attrListRegion', '属性版本加载失败'));
  }

  function createClaim() {
    var documentId = Number($('#claimDocumentId').val());
    var ingredientRaw = $.trim($('#claimIngredientId').val() || '');
    var claimText = $.trim($('#claimText').val() || '');
    if (!documentId || !claimText) { FL.toast('来源文档编号和结构化结论不能为空', 'error', '无法提交'); return; }
    FL.post('/api/admin/knowledge/claims', {
      documentId: documentId, ingredientId: ingredientRaw ? Number(ingredientRaw) : null,
      dimension: $('#claimDimension').val() || null, claimType: $('#claimType').val(), claimText: claimText,
      quoteText: $.trim($('#claimQuote').val() || ''), method: $.trim($('#claimMethod').val() || ''),
      evidenceLevel: $('#claimEvidenceLevel').val()
    }).then(function () { FL.toast('声明已进入审核队列', 'success', '提交成功'); $('#claimText,#claimQuote,#claimMethod').val(''); loadClaims(); })
      ['catch'](function (err) { FL.toast(err.message || '提交失败', 'error', '未完成'); });
  }

  function createAttribute() {
    var ingredientId = Number($('#attrIngredientId').val());
    var documentId = Number($('#attrDocumentId').val());
    var value = Number($('#attrValue').val());
    if (!ingredientId || !documentId || isNaN(value)) { FL.toast('食材、来源文档和属性值不能为空', 'error', '无法提交'); return; }
    FL.post('/api/admin/knowledge/attributes', {
      ingredientId: ingredientId, sourceDocumentId: documentId, dimension: $('#attrDimension').val(), value: value,
      basis: $.trim($('#attrBasis').val() || ''), measurementMethod: $.trim($('#attrMethod').val() || ''),
      concentration: $.trim($('#attrConcentration').val() || ''), temperature: $.trim($('#attrTemperature').val() || ''),
      evidenceLevel: 'LITERATURE'
    }).then(function () { FL.toast('属性版本已进入审核队列', 'success', '提交成功'); loadAttributes(); })
      ['catch'](function (err) { FL.toast(err.message || '提交失败', 'error', '未完成'); });
  }

  function reviewClaim(id, status) {
    FL.put('/api/admin/knowledge/claims/' + id, { reviewStatus: status, reviewNote: '' })
      .then(function () { FL.toast('证据声明状态已更新', 'success', '操作完成'); loadClaims(); loadLinkData(); })
      ['catch'](function (err) { FL.toast(err.message || '审核失败', 'error', '未完成'); });
  }

  function createLink() {
    var claimId = Number($('#linkClaimId').val());
    var ruleId = Number($('#linkRuleId').val());
    if (!claimId || !ruleId) { FL.toast('请选择已批准声明和风味规则', 'error', '无法关联'); return; }
    var $button = $('#linkCreateBtn').prop('disabled', true);
    FL.post('/api/admin/knowledge/evidence-links', {
      ruleId: ruleId, evidenceClaimId: claimId, relationType: $('#linkRelationType').val()
    }).then(function () {
      FL.toast('规则证据关联已保存', 'success', '关联完成');
      loadLinkData();
    })['catch'](function (err) { FL.toast(err.message || '关联失败', 'error', '未完成'); })
      ['finally'](function () { $button.prop('disabled', false); });
  }

  function unlinkEvidence(id) {
    FL.confirm('解除后规则仍然保留，但不再显示这条证据关联。', { title: '解除规则证据关联', okText: '解除', danger: true })
      .then(function (ok) {
        if (!ok) { return null; }
        return FL.del('/api/admin/knowledge/evidence-links/' + id);
      }).then(function (result) {
        if (result === null) { return; }
        FL.toast('规则证据关联已解除', 'success', '操作完成');
        loadLinkData();
      })['catch'](function (err) { FL.toast(err.message || '解除失败', 'error', '未完成'); });
  }

  function reviewAttribute(id, status) {
    FL.put('/api/admin/knowledge/attributes/' + id, { reviewStatus: status, reviewNote: '' })
      .then(function () { FL.toast('属性版本状态已更新', 'success', '操作完成'); loadAttributes(); })
      ['catch'](function (err) { FL.toast(err.message || '审核失败', 'error', '未完成'); });
  }

  function applyAttribute(id) {
    FL.put('/api/admin/knowledge/attributes/' + id + '/apply', {})
      .then(function () { FL.toast('属性已应用到食材主表', 'success', '操作完成'); })
      ['catch'](function (err) { FL.toast(err.message || '应用失败', 'error', '未完成'); });
  }

  function loadError(selector, title) {
    return function (err) { $(selector).html(FL.empty(title, err.message || '请稍后重试。')); };
  }
})(jQuery, window.FL);
