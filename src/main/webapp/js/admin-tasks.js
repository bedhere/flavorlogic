/* ============================================================
   管理后台 · 分析任务查看
   ------------------------------------------------------------
   接口：GET /api/admin/tasks?keyword=&goalType=&status=&page=&pageSize=
         → {page:{list,total,page,pageSize,totalPages}, totalAll}
   说明：管理端任务接口为只读，管理员只能查看结果，不能修改。
   ============================================================ */
(function ($, FL) {
  'use strict';

  /** 列表查询条件（翻页时复用） */
  var query = { keyword: '', goalType: '', status: '', page: 1, pageSize: 10 };

  FL.start({}, function () {
    FL.layout({
      active: 'admin-tasks',
      title: '分析任务',
      subtitle: '查看全平台的风味分析任务与置信度，用于了解规则引擎的实际使用情况',
      crumb: '管理后台 / <strong>分析任务</strong>',
      adminPage: true,
      content: renderShell()
    });
    bindToolbar();
    load();
  });

  /* ---------------- 页面外壳 ---------------- */

  /**
   * 渲染「页面外壳」：筛选工具栏（关键字 / 分析类型 / 状态 / 查询 / 重置）。
   *
   * 本页原先只渲染了 FL.loading 占位，工具栏各控件在 HTML 与 JS 中都不存在，
   * bindToolbar() 的绑定全部落在空集合上，筛选栏与查询按钮不会出现。
   * 这里补齐缺失的控件，既有的 load / render / 事件逻辑保持不变。
   *
   * 下拉选项复用 FL.GOALS 与 FL.TASK_STATUS，取值与 collect() 读取的
   * goalType / status 完全一致（空值表示「全部」）。
   *
   * 列表数据写入 #taskListRegion（不再整体覆盖 #pageBody），本函数只在容器缺失时补渲染一次，
   * 因此工具栏会一直留在页面上，bindToolbar() 的一次性事件绑定也不会因为元素被替换而失效。
   *
   * @returns {String} 工具栏与列表容器的 HTML
   */
  function renderShell() {
    var goalOptions = '<option value="">全部类型</option>' + FL.GOALS.map(function (goal) {
      return '<option value="' + FL.escape(goal.value) + '">' + FL.escape(goal.text) + '</option>';
    }).join('');

    var statusOptions = '<option value="">全部状态</option>' + Object.keys(FL.TASK_STATUS).map(function (key) {
      return '<option value="' + FL.escape(key) + '">' + FL.escape(FL.TASK_STATUS[key].text) + '</option>';
    }).join('');

    var html =
      '<div class="toolbar">' +
      '  <label class="search" for="taskKeyword"><span aria-hidden="true">⌕</span>' +
      '    <input type="search" id="taskKeyword" placeholder="搜索任务名或配方名" autocomplete="off" aria-label="搜索任务名或配方名" /></label>' +
      '  <div style="display:flex;flex-wrap:wrap;align-items:center;gap:10px">' +
      '    <div class="field" style="min-width:150px">' +
      '      <select id="taskGoal" aria-label="分析类型">' + goalOptions + '</select></div>' +
      '    <div class="field" style="min-width:130px">' +
      '      <select id="taskStatus" aria-label="任务状态">' + statusOptions + '</select></div>' +
      '    <button class="primary-btn" type="button" id="taskSearchBtn">查询</button>' +
      '    <button class="secondary-btn" type="button" id="taskResetBtn">重置</button>' +
      '  </div>' +
      '</div>' +
      '<div id="taskListRegion">' + FL.loading('正在加载分析任务…') + '</div>';

    var body = $('#pageBody');
    // 首次渲染时 #pageBody 还不存在，直接把 HTML 交给 FL.layout 作为 content；
    // 之后只在列表容器丢失时补渲染——不要重复覆盖，否则会摧毁 bindToolbar 的一次性事件绑定
    if (!body.length) {
      return html;
    }
    if (!body.find('#taskListRegion').length) {
      body.html(html);
    }
    return html;
  }

  /* ---------------- 工具栏 ---------------- */

  function bindToolbar() {
    $('#taskSearchBtn').on('click', function () {
      collect();
      query.page = 1;
      load();
    });
    $('#taskResetBtn').on('click', function () {
      $('#taskKeyword').val('');
      $('#taskGoal').val('');
      $('#taskStatus').val('');
      collect();
      query.page = 1;
      load();
    });
    $('#taskKeyword').on('keydown', function (event) {
      if (event.key === 'Enter') {
        event.preventDefault();
        $('#taskSearchBtn').trigger('click');
      }
    });
  }

  function collect() {
    query.keyword = $.trim($('#taskKeyword').val() || '');
    query.goalType = filterValue($('#taskGoal').val());
    query.status = filterValue($('#taskStatus').val());
  }

  /** 下拉值归一化：空字符串按「全部」处理，其余原样传给 FL.qs */
  function filterValue(value) {
    if (value === null || value === undefined || value === '') { return ''; }
    return String(value);
  }

  /* ---------------- 列表 ---------------- */

  function load() {
    // 外壳只在缺失时补渲染（幂等），列表数据写进专用容器，避免覆盖工具栏导致事件绑定失效
    renderShell();
    $('#taskListRegion').html(FL.loading('正在加载分析任务…'));
    FL.get('/api/admin/tasks' + FL.qs(query)).then(render)['catch'](function (err) {
      $('#taskListRegion').html(FL.empty('分析任务加载失败', err.message || '请稍后重试。'));
    });
  }

  function render(data) {
    var page = (data && data.page) || {};
    var list = page.list || [];
    var html = '<div class="stat-row" style="grid-template-columns:repeat(2,minmax(0,1fr))">' +
      '<div class="stat-card accent">' +
      '  <span class="label">平台分析任务总数</span>' +
      '  <span class="value">' + FL.escape(total(data)) + '</span>' +
      '  <span class="delta">含全部用户的待处理、分析中、已完成与失败任务</span>' +
      '</div>' +
      '<div class="stat-card">' +
      '  <span class="label">当前筛选结果</span>' +
      '  <span class="value">' + FL.escape(page.total || 0) + '</span>' +
      '  <span class="delta">按关键字、分析类型与状态筛选后的任务数</span>' +
      '</div>' +
      '</div>';

    html += '<div class="notice" style="margin:18px 0">管理员仅可查看，不能修改用户的分析结果；' +
      '任务数据由用户在前台发起分析时生成，只有状态为「已完成」的任务才能进入结果页回看。</div>';

    var rows = list.map(row).join('');
    var body = rows
      ? '<div class="table-wrap"><table><thead><tr>' +
        '<th>任务名</th><th>创建用户</th><th>基准配方</th><th>分析类型</th><th>目标区域</th>' +
        '<th>状态</th><th>置信度</th><th>创建时间</th><th>完成时间</th><th>操作</th>' +
        '</tr></thead><tbody>' + rows + '</tbody></table></div>' + FL.pager(page, 'goPage')
      : FL.empty('没有匹配的分析任务', '换个关键字或清空筛选条件再试。');

    html += '<section class="panel"><div class="panel-head"><h3>任务列表</h3>' +
      '<div class="tag">每页 ' + FL.escape(query.pageSize) + ' 条</div></div>' + body + '</section>';

    $('#taskListRegion').html(html);
  }

  function total(data) {
    if (data && data.totalAll !== null && data.totalAll !== undefined) { return data.totalAll; }
    return ((data && data.page) || {}).total || 0;
  }

  function row(task) {
    var status = FL.TASK_STATUS[task.status] || { text: task.status || '未知', cls: 'muted' };
    return '<tr>' +
      '<td><strong>' + FL.escape(task.taskName || '未命名任务') + '</strong>' +
      (task.summary ? '<br /><span style="color:var(--muted);font-size:11px">' + FL.escape(task.summary) + '</span>' : '') +
      '</td>' +
      '<td>' + (task.username ? FL.escape(task.username) : '<span style="color:var(--muted)">—</span>') + '</td>' +
      '<td>' + (task.recipeName ? FL.escape(task.recipeName) : '<span style="color:var(--muted)">—</span>') + '</td>' +
      '<td>' + FL.escape(FL.goalText(task.goalType)) + '</td>' +
      '<td>' + (task.targetRegion ? FL.escape(task.targetRegion) : '<span style="color:var(--muted)">未指定</span>') + '</td>' +
      '<td>' + FL.statusTag(status.text, status.cls) + '</td>' +
      '<td>' + FL.escape(confidence(task.confidence)) + '</td>' +
      '<td>' + FL.escape(FL.fmtDate(task.createdAt)) + '</td>' +
      '<td>' + FL.escape(FL.fmtDate(task.finishedAt)) + '</td>' +
      '<td class="row-actions">' +
      (task.status === 'COMPLETED'
        ? '<a class="link-btn" href="../analysis-result.html?taskId=' + FL.escape(task.id) + '">查看结果</a>'
        : '<span style="color:var(--muted)">—</span>') +
      '</td></tr>';
  }

  /** 置信度统一按两位小数展示，无结果时显示占位符 */
  function confidence(value) {
    if (value === null || value === undefined || value === '') { return '—'; }
    return FL.fmtNumber(value, 2);
  }

  /** 分页入口：由 FL.pager 以 onclick 方式调用，必须挂在 window 上 */
  window.goPage = function (page) {
    query.page = page > 0 ? page : 1;
    load();
  };
}(window.jQuery, window.FL));
