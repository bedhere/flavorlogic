/* ============================================================
   分析历史页：关键字 / 分析类型 / 状态筛选 + 分页 + 查看结果 + 删除
   ------------------------------------------------------------
   数据来源：GET /api/analysis/history、DELETE /api/analysis/{id}
   类型与状态文案统一复用 FL.goalText 与 FL.TASK_STATUS，避免各处硬编码。
   ============================================================ */
(function ($, FL) {
  'use strict';

  /** 列表查询条件（关键字、类型、状态、分页） */
  var state = { keyword: '', goalType: '', status: '', page: 1, pageSize: 10 };
  /** 最近一次分页结果，删除后用于判断是否需要回退页码 */
  var lastPage = null;

  FL.start({}, function () {
    // 支持从其他页面带筛选条件跳转：analysis-history.html?keyword=&goalType=&status=&page=
    state.keyword = FL.query('keyword') || '';
    state.goalType = FL.query('goalType') || '';
    state.status = FL.query('status') || '';
    var page = Number(FL.query('page'));
    state.page = isNaN(page) || page < 1 ? 1 : Math.floor(page);

    FL.layout({
      active: 'history',
      title: '分析历史',
      subtitle: '按任务名、分析类型与状态回看历史推演结果，可继续查看建议或删除记录',
      crumb: '研发工作区 / <strong>分析历史</strong>',
      actions: '<a class="secondary-btn" href="' + FL.ctx + '/recipes.html">配方库</a>' +
        '<a class="primary-btn" href="' + FL.ctx + '/analysis-run.html">发起风味分析</a>',
      content: toolbar() + '<div id="historyBody">' + FL.loading('正在加载分析记录…') + '</div>'
    });

    bindEvents();
    load();
  });

  /* ---------------- 工具栏 ---------------- */

  function toolbar() {
    var goalOptions = '<option value="">全部类型</option>' + FL.GOALS.map(function (goal) {
      return '<option value="' + FL.escape(goal.value) + '"' +
        (state.goalType === goal.value ? ' selected' : '') + '>' + FL.escape(goal.text) + '</option>';
    }).join('');

    var statusOptions = '<option value="">全部状态</option>' + Object.keys(FL.TASK_STATUS).map(function (key) {
      return '<option value="' + FL.escape(key) + '"' +
        (state.status === key ? ' selected' : '') + '>' + FL.escape(FL.TASK_STATUS[key].text) + '</option>';
    }).join('');

    return '<div class="toolbar">' +
      '<label class="search"><span>⌕</span>' +
      '<input type="search" id="keyword" value="' + FL.escape(state.keyword) + '"' +
      ' placeholder="搜索任务名或配方名" autocomplete="off" /></label>' +
      '<div style="display:flex;flex-wrap:wrap;align-items:center;gap:10px">' +
      '<div class="field" style="min-width:150px">' +
      '<select id="goalType" aria-label="分析类型">' + goalOptions + '</select></div>' +
      '<div class="field" style="min-width:140px">' +
      '<select id="status" aria-label="状态">' + statusOptions + '</select></div>' +
      '<button class="primary-btn" type="button" id="searchBtn">查询</button>' +
      '<button class="secondary-btn" type="button" id="resetBtn">重置</button>' +
      '</div>' +
      '</div>';
  }

  function bindEvents() {
    $('#searchBtn').on('click', function () { search(1); });

    $('#resetBtn').on('click', function () {
      state.keyword = '';
      state.goalType = '';
      state.status = '';
      $('#keyword').val('');
      $('#goalType').val('');
      $('#status').val('');
      search(1);
    });

    // 回车即查询
    $('#keyword').on('keydown', function (event) {
      if (event.key === 'Enter') {
        event.preventDefault();
        search(1);
      }
    });

    // 下拉变更后直接重新查询，减少一次点击
    $('#goalType, #status').on('change', function () { search(1); });

    $('#historyBody').on('click', '[data-action="delete"]', onDelete);
  }

  /** 以当前工具栏值发起查询（页码重置为指定值） */
  function search(page) {
    state.keyword = $.trim($('#keyword').val() || '');
    state.goalType = $.trim($('#goalType').val() || '');
    state.status = $.trim($('#status').val() || '');
    state.page = page;
    load();
  }

  /* ---------------- 数据加载 ---------------- */

  function load() {
    $('#historyBody').html(FL.loading('正在加载分析记录…'));
    var params = {
      keyword: state.keyword,
      goalType: state.goalType,
      status: state.status,
      page: state.page,
      pageSize: state.pageSize
    };
    FL.get('/api/analysis/history' + FL.qs(params)).then(renderList)['catch'](function (err) {
      $('#historyBody').html(FL.empty('分析历史加载失败', err.message || '请稍后重试。'));
    });
  }

  function renderList(page) {
    var data = page || {};
    var list = data.list || [];
    lastPage = data;
    state.page = data.page || state.page;

    if (!list.length) {
      $('#historyBody').html(FL.empty(
        hasFilter() ? '没有符合条件的分析记录' : '还没有分析记录',
        hasFilter() ? '换个关键字或筛选条件再试一次。' : '从「新建配方与分析」开始你的第一次风味推演。'
      ));
      return;
    }

    var html = '<div class="table-wrap"><table><thead><tr>' +
      '<th>任务 / 基准配方</th><th>分析类型</th><th>状态</th><th>结果摘要</th>' +
      '<th>置信度</th><th>创建时间</th><th>操作</th>' +
      '</tr></thead><tbody>' + list.map(row).join('') + '</tbody></table></div>';
    html += FL.pager(data, 'goPage');
    $('#historyBody').html(html);
  }

  function row(item) {
    var status = FL.TASK_STATUS[item.status] || { text: item.status || '未知', cls: 'muted' };
    var actions = item.status === 'COMPLETED'
      ? '<a class="link-btn" href="' + FL.ctx + '/analysis-result.html?taskId=' +
        encodeURIComponent(item.id) + '">查看结果</a>'
      : '<span style="color:var(--muted)">—</span>';
    actions += '<button class="link-btn danger" type="button" data-action="delete" data-id="' +
      FL.escape(item.id) + '" data-name="' + FL.escape(item.taskName || '未命名任务') + '">删除</button>';

    return '<tr>' +
      '<td><strong>' + FL.escape(item.taskName || '未命名任务') + '</strong><br />' +
      '<span style="color:var(--muted)">' + FL.escape(item.recipeName || '—') + '</span></td>' +
      '<td>' + FL.escape(FL.goalText(item.goalType)) + '</td>' +
      '<td>' + FL.statusTag(status.text, status.cls) + '</td>' +
      '<td>' + FL.escape(summaryOf(item)) + '</td>' +
      '<td>' + FL.escape(confidenceOf(item)) + '</td>' +
      '<td>' + FL.escape(FL.fmtDate(item.createdAt)) + '</td>' +
      '<td class="row-actions">' + actions + '</td>' +
      '</tr>';
  }

  /* ---------------- 删除 ---------------- */

  function onDelete() {
    var $btn = $(this);
    if ($btn.prop('disabled')) { return; }
    var id = $btn.attr('data-id');
    if (!id) { return; }
    var name = $btn.attr('data-name') || '该分析记录';

    $btn.prop('disabled', true);
    FL.confirm('删除后该任务的配方快照与分析结果会一并移除，且不可恢复。确认删除「' + name + '」？', {
      title: '删除分析记录',
      okText: '删除',
      danger: true
    }).then(function (confirmed) {
      if (!confirmed) {
        $btn.prop('disabled', false);
        return null;
      }
      return FL.del('/api/analysis/' + encodeURIComponent(id)).then(function () {
        FL.toast('分析记录已删除', 'ok', '删除成功');
        // 删除当页最后一条时回退一页，避免停留在空页
        var size = lastPage && lastPage.list ? lastPage.list.length : 1;
        if (size <= 1 && state.page > 1) { state.page = state.page - 1; }
        load();
      })['catch'](function (err) {
        $btn.prop('disabled', false);
        FL.toast(err.message || '删除失败，请稍后重试。', 'error', '删除未完成');
      });
    });
  }

  /* ---------------- 分页入口（FL.pager 以行内 onclick 调用） ---------------- */

  window.goPage = function (page) {
    var target = Number(page);
    state.page = isNaN(target) || target < 1 ? 1 : Math.floor(target);
    load();
  };

  /* ---------------- 展示辅助 ---------------- */

  function hasFilter() {
    return !!(state.keyword || state.goalType || state.status);
  }

  /** 结果摘要：无结果或已完成但无摘要时显示 — */
  function summaryOf(item) {
    if (!item.summary) { return '—'; }
    return clip(item.summary, 42);
  }

  /** 置信度：无结果数据时显示 — */
  function confidenceOf(item) {
    var value = item.confidence;
    if (value === null || value === undefined || value === '') { return '—'; }
    var num = Number(value);
    return isNaN(num) ? '—' : num.toFixed(2) + '%';
  }

  /** 截断长文本（先截断再转义，避免破坏 HTML 实体） */
  function clip(text, max) {
    var value = String(text === null || text === undefined ? '' : text).replace(/\s+/g, ' ').trim();
    if (!value) { return ''; }
    return value.length > max ? value.substring(0, max) + '…' : value;
  }
}(window.jQuery, window.FL));
