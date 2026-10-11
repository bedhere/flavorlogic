/* ============================================================
   审核知识检索
   ------------------------------------------------------------
   只展示 source_document.review_status = APPROVED 的资料。
   当前由 MySQL FULLTEXT 提供检索，后续接入 Yuxi 时保持页面和接口不变。
   ============================================================ */
(function ($, FL) {
  'use strict';

  var state = { query: '', limit: 10 };

  FL.start({}, function () {
    FL.layout({
      active: 'knowledge-search',
      title: '审核知识',
      subtitle: '检索已经过管理员审核的公开资料，结果保留来源和证据等级，便于回到原文核对',
      crumb: '研发工作区 / <strong>审核知识</strong>',
      actions: '<a class="secondary-btn" href="' + FL.ctx + '/articles.html">研发文章</a>',
      content: renderShell()
    });
    bind();
  });

  function renderShell() {
    return '<div class="knowledge-search-page">' +
      '<section class="knowledge-search-hero"><div><span class="eyebrow">REVIEWED KNOWLEDGE</span>' +
      '<h2>只检索已经审核通过的资料</h2>' +
      '<p>资料先经过来源确认和人工审核，再进入检索结果。每条结果都保留原文地址，方便在研发讨论中复核。</p></div>' +
      '<div class="knowledge-search-rule"><strong>当前检索层</strong><span>MySQL FULLTEXT · 可替换为 Yuxi</span></div></section>' +
      '<div class="knowledge-search-toolbar"><label class="search" for="knowledgeSearchQuery"><span aria-hidden="true">⌕</span>' +
      '<input type="search" id="knowledgeSearchQuery" placeholder="例如：Nutella、燕麦、甜味、配料" autocomplete="off" />' +
      '</label><div class="knowledge-search-actions"><select id="knowledgeSearchLimit" aria-label="结果数量">' +
      '<option value="5">5 条</option><option value="10" selected>10 条</option><option value="20">20 条</option></select>' +
      '<button class="primary-btn" type="button" id="knowledgeSearchSubmit">开始检索</button></div></div>' +
      '<div id="knowledgeSearchHint" class="knowledge-search-hint">请输入关键词，结果只包含已审核通过的来源文档。</div>' +
      '<div id="knowledgeSearchResults"></div></div>';
  }

  function bind() {
    $('#knowledgeSearchSubmit').on('click', search);
    $('#knowledgeSearchLimit').on('change', function () { state.limit = Number($(this).val()) || 10; });
    $('#knowledgeSearchQuery').on('keydown', function (event) {
      if (event.key === 'Enter') { event.preventDefault(); search(); }
    });
  }

  function search() {
    state.query = $.trim($('#knowledgeSearchQuery').val() || '');
    if (!state.query) {
      $('#knowledgeSearchHint').text('请输入关键词后再检索。');
      $('#knowledgeSearchResults').html(FL.empty('等待检索', '可以搜索食材名称、风味维度、产品标签或资料摘要。'));
      return;
    }
    var $button = $('#knowledgeSearchSubmit').prop('disabled', true);
    $('#knowledgeSearchHint').text('正在检索已审核资料…');
    $('#knowledgeSearchResults').html(FL.loading('正在检索来源文档…'));
    FL.get('/api/knowledge/search' + FL.qs({ q: state.query, limit: state.limit }))
      .then(render)['catch'](function (err) {
        $('#knowledgeSearchHint').text('检索失败');
        $('#knowledgeSearchResults').html(FL.empty('审核知识加载失败', err.message || '请稍后重试。'));
      })['finally'](function () { $button.prop('disabled', false); });
  }

  function render(data) {
    var items = (data && data.items) || [];
    var provider = (data && data.provider) || 'MYSQL_FULLTEXT';
    $('#knowledgeSearchHint').text(items.length
      ? '找到 ' + items.length + ' 条结果 · 检索层：' + provider
      : '没有找到匹配的已审核资料 · 检索层：' + provider);
    if (!items.length) {
      $('#knowledgeSearchResults').html(FL.empty('没有找到相关资料', '换个关键词试试，或先确认资料是否已经通过管理员审核。'));
      return;
    }
    var cards = items.map(function (item) {
      var score = item.score === null || item.score === undefined ? '' : '匹配度 ' + FL.fmtNumber(item.score, 2);
      return '<article class="knowledge-result-card"><div class="knowledge-result-head"><div>' +
        '<span class="tag">' + FL.escape(item.sourceType || 'SOURCE') + '</span>' +
        '<span class="knowledge-result-level">' + FL.escape(item.evidenceLevel || 'UNVERIFIED') + '</span>' +
        '<h3>' + FL.escape(item.title || '未命名资料') + '</h3></div>' +
        '<span class="knowledge-result-score">' + FL.escape(score) + '</span></div>' +
        (item.summary ? '<p class="knowledge-result-summary">' + FL.escape(item.summary) + '</p>' : '') +
        '<blockquote>' + FL.escape(item.excerpt || '暂无可展示摘录') + '</blockquote>' +
        '<div class="knowledge-result-foot"><span>文档 #' + FL.escape(item.documentId) + '</span>' +
        '<a href="' + FL.escape(item.url) + '" target="_blank" rel="noopener">打开原文 ↗</a></div></article>';
    }).join('');
    $('#knowledgeSearchResults').html('<div class="knowledge-result-list">' + cards + '</div>');
  }
}(window.jQuery, window.FL));
