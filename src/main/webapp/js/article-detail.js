/* ============================================================
   文章详情：hero 信息头 + 两列正文布局（正文 + 相关文章）
   ------------------------------------------------------------
   接口：GET /api/articles/{id} → {article, related}
   安全：除正文 content 由后端 TextUtil.sanitizeHtml 白名单过滤后
        直接 .html() 注入外，其余字段一律 FL.escape()
   ============================================================ */
(function ($, FL) {
  'use strict';

  /** 文章编号：来自列表页 article-detail.html?id=x */
  var articleId = FL.query('id');

  FL.startPublic({}, function () {
    FL.layout({
      active: 'articles',
      title: '文章详情',
      subtitle: '文章正文、来源信息与同分类的相关阅读',
      crumb: '<a href="' + FL.ctx + '/articles.html">研发知识库</a> / <strong>文章详情</strong>',
      actions: '<a class="secondary-btn" href="' + FL.ctx + '/articles.html">返回知识库</a>',
      content: FL.loading('正在加载文章…')
    });
    if (!articleId || !/^\d+$/.test(String(articleId))) {
      $('#pageBody').html(FL.empty('缺少或无效的文章编号', '请从研发知识库列表点击文章卡片进入详情。') +
        '<div style="padding-bottom:40px;text-align:center">' + backLink() + '</div>');
      return;
    }
    load();
  });

  /** 拉取文章详情与相关文章 */
  function load() {
    FL.get('/api/articles/' + encodeURIComponent(articleId)).then(render)['catch'](function (err) {
      $('#pageBody').html(FL.empty('文章加载失败', (err && err.message) || '请稍后重试。') +
        '<div style="padding-bottom:40px;text-align:center">' + backLink() + '</div>');
    });
  }

  /**
   * 渲染详情页。
   *
   * @param {Object} data {article: 文章详情, related: 相关文章列表}
   */
  function render(data) {
    var article = (data && data.article) || {};
    var related = (data && data.related) || [];

    var html = hero(article) +
      '<div class="article-layout" style="padding:26px 0 0">' +
      '  <div class="prose" id="articleProse"></div>' +
      relatedPanel(related) +
      '</div>' +
      '<div class="notice" style="margin-top:20px">本文用于研发方向参考，配方落地前仍需结合原料批次、工艺条件与食品安全要求进行验证。</div>' +
      '<div class="form-actions" style="justify-content:space-between;margin-top:18px">' +
      backLink() + sourceLink(article) +
      '</div>';
    $('#pageBody').html(html);

    // 正文：后端已完成 HTML 安全过滤（白名单），此处按约定直接注入，不再二次转义
    $('#articleProse').html(article.content || '<p>该文章暂无正文内容，可联系管理员补充。</p>');

    document.title = (article.title || '文章详情') + ' · 食之有理 FlavorLogic';
  }

  /** 顶部信息头：分类标签、标题、来源 / 作者 / 发布时间 / 阅读量 */
  function hero(article) {
    var cover = safeCover(article.coverUrl);
    var meta = [];
    if (article.sourceName) {
      meta.push('<span>来源：' + FL.escape(article.sourceName) + '</span>');
    }
    meta.push('<span>作者：' + FL.escape(article.authorName || '平台编辑') + '</span>');
    meta.push('<span>发布时间：' + FL.escape(FL.fmtDate(article.publishedAt || article.createdAt)) + '</span>');
    meta.push('<span>阅读 ' + FL.escape(article.viewCount === null || article.viewCount === undefined ? 0 : article.viewCount) + '</span>');

    return '<section class="article-hero" style="padding:34px 32px 30px;border-radius:var(--radius)' +
      (cover ? ';--cover:url(\'' + FL.escape(cover) + '\')' : '') + '">' +
      '<span class="tag">' + FL.escape(article.categoryName || '未分类') + '</span>' +
      '<h1>' + FL.escape(article.title) + '</h1>' +
      '<div class="meta">' + meta.join('') + '</div>' +
      '</section>';
  }

  /** 右列：相关文章（同分类，最多 4 条） */
  function relatedPanel(related) {
    var items = (related || []).map(function (item) {
      return '<a class="side-link" href="' + FL.ctx + '/article-detail.html?id=' + encodeURIComponent(item.id) + '"' +
        ' style="flex-direction:column;align-items:flex-start;gap:3px;padding:11px;min-height:auto">' +
        '<strong style="font-size:12.5px">' + FL.escape(item.title) + '</strong>' +
        '<span style="color:var(--muted);font-size:11px">' + FL.escape(item.categoryName || '未分类') +
        ' · ' + FL.escape(FL.fmtDate(item.publishedAt)) + '</span></a>';
    }).join('');

    return '<section class="panel" style="align-self:start">' +
      '<div class="panel-head"><h3>相关文章</h3></div>' +
      '<div class="panel-body">' +
      (items || FL.empty('暂无相关文章', '该分类下还没有其他已发布文章。')) +
      '</div></section>';
  }

  /** 底部：返回知识库 */
  function backLink() {
    return '<a class="secondary-btn" href="' + FL.ctx + '/articles.html">← 返回知识库</a>';
  }

  /** 底部：外部来源链接，仅接受 http(s) 地址 */
  function sourceLink(article) {
    var url = safeLink(article.sourceUrl);
    if (!url) {
      return '<span style="color:var(--muted);font-size:12px">本文由平台编辑整理，暂无可跳转的原文链接</span>';
    }
    return '<a class="secondary-btn" href="' + FL.escape(url) + '" target="_blank" rel="noopener">查看原文 ↗</a>';
  }

  /**
   * 封面地址白名单校验：只接受 http(s)、站内绝对路径或 assets/ 相对路径，
   * 避免把任意字符串拼进内联 CSS。
   */
  function safeCover(url) {
    if (!url) { return ''; }
    var value = $.trim(String(url));
    return /^(https?:\/\/[^"'\s()\\<>]+|\/[^"'\s()\\<>]*|assets\/[^"'\s()\\<>]+)$/i.test(value) ? value : '';
  }

  /** 外链地址校验：非 http(s) 一律不渲染成链接 */
  function safeLink(url) {
    if (!url) { return ''; }
    var value = $.trim(String(url));
    return /^https?:\/\/[^"'\s<>]+$/i.test(value) ? value : '';
  }
}(window.jQuery, window.FL));
