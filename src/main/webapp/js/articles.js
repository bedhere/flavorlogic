/* ============================================================
   研发知识库：分类 / 关键字筛选 + 文章卡片网格 + 分页
   ------------------------------------------------------------
   接口：GET /api/categories（分类下拉）
        GET /api/articles?categoryId=&keyword=&page=&pageSize=
   三态：加载中（FL.loading）、空结果（FL.empty）、失败（FL.empty + 重试）
   ============================================================ */
(function ($, FL) {
  'use strict';

  /** 每页文章数：与三列卡片网格对齐（3 × 3） */
  var PAGE_SIZE = 9;

  /** app.css 只给前三张卡片配了封面，这里按序轮播三张已有素材，保证第 4 张起不出现空白图区 */
  var COVERS = [
    'assets/knowledge-lab.jpg',
    'assets/hero-ingredients.jpg',
    'assets/knowledge-ingredients.jpg'
  ];

  /** 当前筛选条件：分类编号、关键字、页码 */
  var state = { categoryId: '', keyword: '', page: 1 };

  FL.startPublic({}, function () {
    FL.layout({
      active: 'articles',
      title: '研发知识库',
      subtitle: '风味科学、原料特性与健康化改造的研发支撑文章，可按分类与关键字快速检索',
      crumb: '研发工作区 / <strong>知识库</strong>',
      actions: '<a class="secondary-btn" href="' + FL.ctx + '/dashboard.html">返回工作台</a>',
      content: FL.loading('正在加载研发知识…')
    });
    loadCategories().then(load);
  });

  /** 先取分类，再渲染工具栏；分类失败时降级为「全部分类」，不阻塞文章列表 */
  function loadCategories() {
    return FL.get('/api/categories').then(function (categories) {
      renderShell(categories || []);
    })['catch'](function () {
      renderShell([]);
      FL.toast('文章分类加载失败，可先用关键字检索', 'warn', '部分内容未加载');
    });
  }

  /** 渲染工具栏与结果区域，并绑定查询 / 重置 / 回车 / 卡片跳转事件 */
  function renderShell(categories) {
    var options = '<option value="">全部分类</option>' + categories.map(function (category) {
      return '<option value="' + FL.escape(category.id) + '">' + FL.escape(category.name) + '</option>';
    }).join('');

    var html =
      '<div class="toolbar">' +
      '  <div style="display:flex;flex-wrap:wrap;align-items:flex-end;gap:10px">' +
      '    <div class="control" style="margin:0;min-width:190px">' +
      '      <label for="categoryFilter">文章分类</label>' +
      '      <select id="categoryFilter"' + (categories.length ? '' : ' disabled') + '>' + options + '</select>' +
      '    </div>' +
      '    <label class="search" for="keyword"><span aria-hidden="true">⌕</span>' +
      '      <input type="search" id="keyword" placeholder="搜索文章标题或摘要" autocomplete="off" /></label>' +
      '    <button class="primary-btn" type="button" id="searchBtn">查询</button>' +
      '    <button class="secondary-btn" type="button" id="resetBtn">重置</button>' +
      '  </div>' +
      '  <div id="resultHint" style="color:var(--muted);font-size:12px"></div>' +
      '</div>' +
      '<div id="listRegion">' + FL.loading('正在加载文章列表…') + '</div>' +
      '<div id="pagerRegion"></div>';
    $('#pageBody').html(html);

    $('#searchBtn').on('click', function () {
      state.categoryId = $('#categoryFilter').val() || '';
      state.keyword = $.trim($('#keyword').val());
      state.page = 1;
      load();
    });
    $('#keyword').on('keydown', function (event) {
      if (event.key === 'Enter') {
        event.preventDefault();
        $('#searchBtn').trigger('click');
      }
    });
    $('#resetBtn').on('click', function () {
      $('#categoryFilter').val('');
      $('#keyword').val('');
      state = { categoryId: '', keyword: '', page: 1 };
      load();
    });
    // 整张卡片可点击（含键盘回车 / 空格），跳转文章详情
    $('#listRegion').on('click', '.article', function () {
      openArticle($(this).attr('data-id'));
    });
    $('#listRegion').on('keydown', '.article', function (event) {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        openArticle($(this).attr('data-id'));
      }
    });
  }

  /** 打开文章详情页 */
  function openArticle(id) {
    if (!id) { return; }
    location.href = FL.ctx + '/article-detail.html?id=' + encodeURIComponent(id);
  }

  /** 按当前筛选条件拉取文章列表 */
  function load() {
    $('#listRegion').html(FL.loading('正在加载文章列表…'));
    FL.get('/api/articles' + FL.qs({
      categoryId: state.categoryId,
      keyword: state.keyword,
      page: state.page,
      pageSize: PAGE_SIZE
    })).then(render)['catch'](renderError);
  }

  /** 渲染卡片网格与分页器；无数据时给出空状态 */
  function render(page) {
    var list = (page && page.list) || [];
    var total = (page && page.total) || 0;
    $('#resultHint').text(state.keyword || state.categoryId
      ? '当前条件下共 ' + total + ' 篇文章'
      : '共 ' + total + ' 篇文章');

    if (!list.length) {
      $('#listRegion').html(FL.empty('没有找到相关文章', '换个分类或关键字试试，也可以点「重置」查看全部文章。'));
      $('#pagerRegion').empty();
      return;
    }

    var cards = list.map(function (article, index) {
      return '<article class="article" role="link" tabindex="0" data-id="' + FL.escape(article.id) + '"' +
        ' style="cursor:pointer" aria-label="阅读文章：' + FL.escape(article.title) + '">' +
        '<div class="article-media" style="background-image:url(\'' + COVERS[index % COVERS.length] + '\')"></div>' +
        '<div class="article-body">' +
        '<span class="tag">' + FL.escape(article.categoryName || '未分类') + '</span>' +
        '<h3>' + FL.escape(article.title) + '</h3>' +
        '<p>' + FL.escape(article.summary || '作者暂未填写摘要，点击卡片查看正文。') + '</p>' +
        '<div class="article-meta">' +
        '<span>' + FL.escape(article.authorName || '平台编辑') + '</span>' +
        '<span>' + FL.escape(FL.fmtDate(article.publishedAt || article.createdAt)) +
        ' · 阅读 ' + FL.escape(article.viewCount === null || article.viewCount === undefined ? 0 : article.viewCount) + '</span>' +
        '</div></div></article>';
    }).join('');

    $('#listRegion').html('<div class="knowledge-grid">' + cards + '</div>');
    $('#pagerRegion').html(FL.pager(page, 'goPage'));
  }

  /** 列表加载失败：给出可重试的失败态 */
  function renderError(err) {
    $('#resultHint').text('');
    $('#pagerRegion').empty();
    $('#listRegion').html(
      FL.empty('知识库加载失败', (err && err.message) || '请稍后重试。') +
      '<div style="padding-bottom:40px;text-align:center">' +
      '<button class="secondary-btn" type="button" id="retryBtn">重新加载</button></div>');
    $('#retryBtn').on('click', load);
  }

  /** 分页入口：FL.pager 通过内联 onclick 调用 */
  window.goPage = function (page) {
    if (!page || page < 1 || page === state.page) { return; }
    state.page = page;
    window.scrollTo(0, 0);
    load();
  };
}(window.jQuery, window.FL));
