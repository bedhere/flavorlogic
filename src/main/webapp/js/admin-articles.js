/* ============================================================
   管理后台 · 文章管理
   ------------------------------------------------------------
   接口：GET    /api/admin/articles?categoryId=&keyword=&status=&page=&pageSize=
        GET    /api/admin/articles/categories
        POST   /api/admin/articles              新增
        POST   /api/admin/articles/sync         模拟同步 / 重复检测（不写库）
        PUT    /api/admin/articles/{id}         编辑
        DELETE /api/admin/articles/{id}         软删除（status=3）
   说明：来源地址重复、正文指纹重复时后端返回 409，此处把服务端说明
        用 FL.toast 展示并保留弹窗内的用户输入，避免白填一遍。
   ============================================================ */
(function ($, FL) {
  'use strict';

  /** 列表查询条件（翻页时复用） */
  var query = { categoryId: '', keyword: '', status: '', page: 1, pageSize: 10 };

  /** Bootstrap Modal 实例（延迟创建，避免页面初始化时取不到元素） */
  var modal = null;

  FL.start({}, function () {
    FL.layout({
      active: 'admin-articles',
      title: '文章管理',
      subtitle: '维护研发知识库内容：草稿、发布、下架与软删除，并演示外部内容的重复检测',
      crumb: '管理后台 / <strong>文章管理</strong>',
      adminPage: true,
      content: renderShell()
    });
    bindToolbar();
    loadCategories();
    load();
  });

  /* ---------------- 页面外壳 ---------------- */

  /**
   * 渲染「页面外壳」：筛选工具栏（分类 / 状态 / 关键字 / 查询 / 重置 / 新增 / 模拟同步）。
   *
   * 本页原先只渲染了 FL.loading 占位，工具栏各控件在 HTML 与 JS 中都不存在，
   * bindToolbar() 的绑定全部落在空集合上，也就无法从界面新增文章或触发重复检测。
   * 这里补齐缺失的控件，既有的 load / render / 事件逻辑保持不变。
   *
   * 列表数据写入 #articleListRegion（不再整体覆盖 #pageBody），本函数只在容器缺失时补渲染一次，
   * 因此工具栏会一直留在页面上，bindToolbar() 的一次性事件绑定也不会因为元素被替换而失效。
   *
   * @returns {String} 工具栏与列表容器的 HTML
   */
  function renderShell() {
    var html =
      '<div class="toolbar">' +
      '  <div style="display:flex;flex-wrap:wrap;align-items:center;gap:10px">' +
      '    <div class="field" style="min-width:150px">' +
      '      <select id="articleCategoryFilter" aria-label="文章分类"><option value="">全部分类</option></select></div>' +
      '    <div class="field" style="min-width:130px">' +
      '      <select id="articleStatusFilter" aria-label="文章状态">' +
      '        <option value="">全部状态</option>' +
      '        <option value="0">草稿</option>' +
      '        <option value="1">已发布</option>' +
      '        <option value="2">已下架</option>' +
      '        <option value="3">已删除</option>' +
      '      </select></div>' +
      '    <label class="search" for="articleKeyword"><span aria-hidden="true">⌕</span>' +
      '      <input type="search" id="articleKeyword" placeholder="搜索标题或摘要" autocomplete="off" aria-label="搜索标题或摘要" /></label>' +
      '  </div>' +
      '  <div style="display:flex;flex-wrap:wrap;align-items:center;gap:10px">' +
      '    <button class="primary-btn" type="button" id="articleSearchBtn">查询</button>' +
      '    <button class="secondary-btn" type="button" id="articleResetBtn">重置</button>' +
      '    <button class="primary-btn" type="button" id="articleCreateBtn">新增文章</button>' +
      '    <button class="secondary-btn" type="button" id="articleSyncBtn">模拟同步 / 重复检测</button>' +
      '  </div>' +
      '</div>' +
      '<div id="articleListRegion">' + FL.loading('正在加载文章列表…') + '</div>';

    var body = $('#pageBody');
    // 首次渲染时 #pageBody 还不存在，直接把 HTML 交给 FL.layout 作为 content；
    // 之后只在列表容器丢失时补渲染——不要重复覆盖，否则会摧毁 bindToolbar 的一次性事件绑定
    if (!body.length) {
      return html;
    }
    if (!body.find('#articleListRegion').length) {
      body.html(html);
    }
    return html;
  }

  /* ---------------- 分类下拉 ---------------- */

  function loadCategories() {
    FL.get('/api/admin/articles/categories').then(function (list) {
      var items = list || [];
      $('#articleCategory').html('<option value="">未分类</option>' + items.map(function (item) {
        return '<option value="' + FL.escape(item.id) + '">' + FL.escape(item.name) + '</option>';
      }).join(''));
      $('#articleCategoryFilter').html('<option value="">全部分类</option>' + items.map(function (item) {
        return '<option value="' + FL.escape(item.id) + '">' + FL.escape(item.name) + '</option>';
      }).join(''));
    })['catch'](function (err) {
      FL.toast(err.message || '文章分类加载失败', 'error', '分类不可用');
    });
  }

  /* ---------------- 工具栏 ---------------- */

  function bindToolbar() {
    $('#articleSearchBtn').on('click', function () {
      collect();
      query.page = 1;
      load();
    });
    $('#articleResetBtn').on('click', function () {
      $('#articleCategoryFilter').val('');
      $('#articleStatusFilter').val('');
      $('#articleKeyword').val('');
      collect();
      query.page = 1;
      load();
    });
    $('#articleKeyword').on('keydown', function (event) {
      if (event.key === 'Enter') {
        event.preventDefault();
        $('#articleSearchBtn').trigger('click');
      }
    });
    $('#articleCreateBtn').on('click', function () {
      openEditor(null);
    });
    $('#articleSyncBtn').on('click', runSync);
    $('#articleSaveBtn').on('click', save);
  }

  function collect() {
    query.categoryId = filterValue($('#articleCategoryFilter').val());
    query.status = filterValue($('#articleStatusFilter').val());
    query.keyword = $.trim($('#articleKeyword').val() || '');
  }

  /** 下拉值归一化：空字符串按「全部」处理，状态 0（草稿）等合法值必须保留 */
  function filterValue(value) {
    if (value === null || value === undefined || value === '') { return ''; }
    return String(value);
  }

  /* ---------------- 列表 ---------------- */

  function load() {
    // 外壳只在缺失时补渲染（幂等），列表数据写进专用容器，避免覆盖工具栏导致事件绑定失效
    renderShell();
    $('#articleListRegion').html(FL.loading('正在加载文章列表…'));
    FL.get('/api/admin/articles' + FL.qs(query)).then(render)['catch'](function (err) {
      $('#articleListRegion').html(FL.empty('文章列表加载失败', err.message || '请稍后重试。'));
    });
  }

  function render(data) {
    var list = (data && data.list) || [];
    // 缓存本页文章对象：行内按钮只带编号，编辑/发布时取完整字段（含正文）
    rowCache = {};
    $.each(list, function (index, article) {
      rowCache[String(article.id)] = article;
    });
    var rows = list.map(row).join('');
    var body = rows
      ? '<div class="table-wrap"><table><thead><tr>' +
        '<th>标题</th><th>分类</th><th>作者</th><th>状态</th><th>浏览</th><th>发布时间</th><th>操作</th>' +
        '</tr></thead><tbody>' + rows + '</tbody></table></div>' + FL.pager(data, 'goPage')
      : FL.empty('没有匹配的文章', '调整筛选条件，或点击「新增文章」录入第一篇研发知识。');

    $('#articleListRegion').html(
      '<section class="panel">' +
      '  <div class="panel-head"><h3>知识库文章</h3><div class="tag">共 ' + FL.escape((data && data.total) || 0) + ' 篇</div></div>' +
      body +
      '</section>' +
      '<section class="panel" style="margin-top:18px">' +
      '  <div class="panel-head"><h3>同步与重复检测结果</h3><div class="tag">仅演示，不写库</div></div>' +
      '  <div class="panel-body" id="syncResult">' + syncEmpty() + '</div>' +
      '</section>'
    );
  }

  function row(article) {
    var status = FL.ARTICLE_STATUS[Number(article.status)] || { text: '未知', cls: 'muted' };
    var id = FL.escape(article.id);
    return '<tr>' +
      '<td><strong title="' + FL.escape(article.title) + '" style="display:block;max-width:320px;overflow:hidden;' +
      'text-overflow:ellipsis;white-space:nowrap">' + FL.escape(article.title) + '</strong>' +
      (article.sourceName ? '<span style="color:var(--muted);font-size:11px">来源：' + FL.escape(article.sourceName) + '</span>' : '') +
      '</td>' +
      '<td>' + (article.categoryName ? FL.escape(article.categoryName) : '<span style="color:var(--muted)">未分类</span>') + '</td>' +
      '<td>' + (article.authorName ? FL.escape(article.authorName) : '<span style="color:var(--muted)">—</span>') + '</td>' +
      '<td>' + FL.statusTag(status.text, status.cls) + '</td>' +
      '<td>' + FL.escape(article.viewCount === null || article.viewCount === undefined ? 0 : article.viewCount) + '</td>' +
      '<td>' + FL.escape(article.publishedAt ? FL.fmtDate(article.publishedAt) : '未发布') + '</td>' +
      '<td><div class="row-actions">' +
      '<button type="button" class="link-btn" data-action="edit" data-id="' + id + '">编辑</button>' +
      (Number(article.status) === 1
        ? '<button type="button" class="link-btn" data-action="offline" data-id="' + id + '" data-title="' + FL.escape(article.title) + '">下架</button>'
        : '<button type="button" class="link-btn" data-action="publish" data-id="' + id + '" data-title="' + FL.escape(article.title) + '">发布</button>') +
      '<button type="button" class="link-btn danger" data-action="delete" data-id="' + id + '" data-title="' + FL.escape(article.title) + '">删除</button>' +
      '</div></td></tr>';
  }

  /** 行内按钮只带编号，真实数据从当前列表缓存中取，避免在 HTML 里塞正文 */
  var rowCache = {};

  /* ---------------- 行内操作 ---------------- */

  $(document).on('click', '#pageBody [data-action]', function () {
    var $btn = $(this);
    var action = $btn.attr('data-action');
    var id = $btn.attr('data-id');
    var title = $btn.attr('data-title') || '该文章';
    if (action === 'edit') {
      openEditor(rowCache[String(id)] || null);
    } else if (action === 'publish' || action === 'offline') {
      changeStatus($btn, id, title, action === 'publish' ? 1 : 2);
    } else if (action === 'delete') {
      removeArticle($btn, id, title);
    }
  });

  function changeStatus($btn, id, title, status) {
    var publish = status === 1;
    var text = publish
      ? ('发布「' + title + '」？发布后文章会出现在知识库列表中。')
      : ('下架「' + title + '」？下架后普通用户不再看到该文章，数据仍会保留。');
    FL.confirm(text, { title: publish ? '发布文章' : '下架文章', okText: publish ? '确认发布' : '确认下架' })
      .then(function (ok) {
        if (!ok) { return; }
        return guard($btn, function () {
          // 编辑接口要求正文等必填字段，因此提交当前列表中的完整文章对象
          var article = rowCache[String(id)] || {};
          return FL.put('/api/admin/articles/' + id, payloadOf(article, status));
        }, publish ? '文章已发布' : '文章已下架').then(load);
      })['catch'](function (err) {
        FL.toast(err.message || '状态修改失败', 'error', '操作未完成');
      });
  }

  function removeArticle($btn, id, title) {
    FL.confirm('删除「' + title + '」？该操作为软删除（状态改为已删除），文章数据仍保留在库中以便追溯。', {
      title: '删除文章', okText: '确认删除', danger: true
    }).then(function (ok) {
      if (!ok) { return; }
      return guard($btn, function () {
        return FL.del('/api/admin/articles/' + id);
      }, '文章已删除').then(load);
    })['catch'](function (err) {
      FL.toast(err.message || '删除失败', 'error', '操作未完成');
    });
  }

  /* ---------------- 新增 / 编辑弹窗 ---------------- */

  function openEditor(article) {
    $('#articleModalTitle').text(article ? '编辑文章' : '新增文章');
    $('#articleId').val(article ? article.id : '');
    $('#articleTitle').val(article ? (article.title || '') : '');
    $('#articleCategory').val(article && article.categoryId ? String(article.categoryId) : '');
    $('#articleSummary').val(article ? (article.summary || '') : '');
    $('#articleContent').val(article ? (article.content || '') : '');
    $('#articleSourceName').val(article ? (article.sourceName || '') : '');
    $('#articleSourceUrl').val(article ? (article.sourceUrl || '') : '');
    $('#articleCoverUrl').val(article ? (article.coverUrl || '') : '');
    // 编辑已下架/已删除的文章时，状态默认回到草稿，避免误发布
    $('#articleStatus').val(article && Number(article.status) === 1 ? '1' : '0');
    FL.clearFieldErrors($('#articleForm'));
    $('#articleSaveBtn').prop('disabled', false).text('保存');
    showModal();
  }

  function showModal() {
    if (!modal) {
      modal = new window.bootstrap.Modal(document.getElementById('articleModal'));
    }
    modal.show();
  }

  function save() {
    var $form = $('#articleForm');
    if (!FL.validate($form, [
      { name: '#articleTitle', label: '标题', required: true, max: 200 },
      { name: '#articleContent', label: '正文', required: true }
    ])) {
      return;
    }
    var id = $.trim($('#articleId').val() || '');
    var body = {
      title: $.trim($('#articleTitle').val()),
      categoryId: $('#articleCategory').val() ? Number($('#articleCategory').val()) : null,
      summary: $.trim($('#articleSummary').val()),
      content: $('#articleContent').val(),
      coverUrl: $.trim($('#articleCoverUrl').val()),
      sourceName: $.trim($('#articleSourceName').val()),
      sourceUrl: $.trim($('#articleSourceUrl').val()),
      status: Number($('#articleStatus').val())
    };

    // 提交期间禁用按钮，避免重复写入
    var $btn = $('#articleSaveBtn').prop('disabled', true).text('保存中…');
    var request = id ? FL.put('/api/admin/articles/' + id, body) : FL.post('/api/admin/articles', body);
    request.then(function () {
      FL.toast(id ? '文章已保存' : '文章已创建');
      if (modal) { modal.hide(); }
      load();
    })['catch'](function (err) {
      // 409：来源地址或正文指纹重复；400：字段校验不通过
      // 保留弹窗与已填内容，仅提示服务端返回的说明（内含已有文章标题）
      FL.toast(err.message || '保存失败，请检查表单内容', 'error', '保存未完成');
    })['finally'](function () {
      $btn.prop('disabled', false).text('保存');
    });
  }

  /** 组装写接口需要的字段（列表对象里已包含正文与来源信息） */
  function payloadOf(article, status) {
    return {
      title: article.title,
      categoryId: article.categoryId === null || article.categoryId === undefined ? null : Number(article.categoryId),
      summary: article.summary,
      content: article.content,
      coverUrl: article.coverUrl,
      sourceName: article.sourceName,
      sourceUrl: article.sourceUrl,
      status: status === undefined || status === null ? Number(article.status) : status
    };
  }

  /* ---------------- 模拟同步 / 重复检测 ---------------- */

  function runSync() {
    var $btn = $('#articleSyncBtn').prop('disabled', true).text('检测中…');
    $('#syncResult').html(FL.loading('正在执行模拟同步与重复检测…'));
    FL.post('/api/admin/articles/sync', {})['catch'](function (err) {
      FL.toast(err.message || '模拟同步失败', 'error', '同步未完成');
      throw err;
    }).then(function (data) {
      // 同步结果只做展示，不刷新列表（模拟模式不会写库）
      var result = data || {};
      $('#syncResult').html(syncResult(result));
      FL.toast('模拟同步完成：检测 ' + (result.total || 0) + ' 条，命中重复 ' + ((result.duplicated || []).length) + ' 条');
    })['catch'](function () {
      $('#syncResult').html(syncEmpty('本次同步未返回结果，可稍后重试。'));
    })['finally'](function () {
      $btn.prop('disabled', false).text('模拟同步 / 重复检测');
    });
  }

  function syncEmpty(hint) {
    return '<div style="color:var(--muted);font-size:12px;line-height:1.9">' +
      '尚未执行同步。点击右上角「模拟同步 / 重复检测」可按来源地址与正文指纹做一次去重演练：' +
      '第一阶段不抓取外部网络内容，也不会写入数据库。' +
      (hint ? '<br />' + FL.escape(hint) : '') + '</div>';
  }

  function syncResult(result) {
    var duplicated = result.duplicated || [];
    var html = '<div class="stat-row" style="grid-template-columns:repeat(3,minmax(0,1fr))">' +
      miniStat('本次检测条目', result.total) +
      miniStat('实际导入', result.imported) +
      miniStat('命中重复 / 未导入', duplicated.length) +
      '</div>';
    html += '<div class="notice" style="margin-top:16px">' +
      (result.simulated ? '本次为模拟同步（未写库）：' : '本次为真实导入：') +
      FL.escape(result.message || ('共处理 ' + result.total + ' 条，导入 ' + result.imported + ' 条。')) +
      '</div>';
    html += duplicated.length
      ? '<div class="table-wrap" style="margin-top:16px"><table><thead><tr><th>条目标题</th><th>检测结果说明</th></tr></thead><tbody>' +
        duplicated.map(function (item) {
          return '<tr><td><strong>' + FL.escape(item.title || '未命名字段') + '</strong></td>' +
            '<td style="color:var(--muted)">' + FL.escape(item.reason || '判定为重复内容，已拦截') + '</td></tr>';
        }).join('') + '</tbody></table></div>'
      : '<div class="empty" style="padding:26px"><strong>未发现重复条目</strong>本次检测的条目均通过去重校验。</div>';
    return html;
  }

  function miniStat(label, value) {
    return '<div class="stat-card" style="padding:14px 16px">' +
      '<span class="label">' + FL.escape(label) + '</span>' +
      '<span class="value" style="font-size:24px;margin:8px 0 0">' +
      FL.escape(value === null || value === undefined ? 0 : value) + '</span></div>';
  }

  /* ---------------- 通用 ---------------- */

  /**
   * 提交期间禁用按钮，避免重复点击；成功后提示，失败向上抛出由调用方提示。
   *
   * @param {Object} $btn 触发按钮
   * @param {Function} request 返回 Promise 的请求函数
   * @param {String} successText 成功提示文案
   */
  function guard($btn, request, successText) {
    $btn.prop('disabled', true).css('opacity', '.6');
    return request().then(function (result) {
      FL.toast(successText);
      return result;
    })['catch'](function (err) {
      $btn.prop('disabled', false).css('opacity', '');
      throw err;
    });
  }

  /** 分页入口：由 FL.pager 以 onclick 方式调用，必须挂在 window 上 */
  window.goPage = function (page) {
    query.page = page > 0 ? page : 1;
    load();
  };
}(window.jQuery, window.FL));
