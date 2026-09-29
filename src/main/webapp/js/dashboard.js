/* ============================================================
   研发工作台：汇总配方、分析任务、知识推荐与（管理员）平台统计
   ============================================================ */
(function ($, FL) {
  'use strict';

  FL.start({}, function (user) {
    FL.layout({
      active: 'dashboard',
      title: '研发工作台',
      subtitle: '汇总你的配方、分析任务与研发知识，从这里开始一次配方推演',
      crumb: '研发工作区 / <strong>工作台</strong>',
      actions: '<a class="secondary-btn" href="' + FL.ctx + '/analysis-history.html">分析历史</a>' +
        '<a class="primary-btn" href="' + FL.ctx + '/analysis-run.html">发起风味分析</a>',
      content: FL.loading('正在加载工作台数据…')
    });
    load();
  });

  function load() {
    FL.get('/api/dashboard').then(render)['catch'](function (err) {
      $('#pageBody').html(FL.empty('工作台数据加载失败', err.message));
    });
  }

  function render(data) {
    var html = '';
    html += statCards(data);
    html += '<div class="app-grid" style="margin-top:18px">';
    html += '<div style="display:grid;gap:18px">' + recentTasks(data) + recentRecipes(data) + '</div>';
    html += '<div style="display:grid;gap:18px">' + quickActions() + latestArticles(data) + '</div>';
    html += '</div>';
    if (data.isAdmin && data.platform) {
      html += '<div class="app-grid" style="margin-top:18px">' + platformPanel(data.platform) + operationLogs(data.operationLogs) + '</div>';
    }
    html += '<div class="notice" style="margin-top:18px">本平台的风味数值为课设演示用相对评分，分析结果用于打样前的方向性参考，' +
      '不等同于实验室检测或感官评价结论；最终配方仍需试产与食品安全审核。</div>';
    $('#pageBody').html(html);
  }

  function statCards(data) {
    return '<div class="stat-row">' +
      card('我的配方', data.recipeCount, '可继续编辑并发起分析') +
      card('分析任务', data.taskCount, '累计创建的风味分析任务') +
      card('已完成分析', data.completedCount, '可直接回看结果与建议') +
      card('最近分析', data.lastAnalysisAt ? FL.fmtDate(data.lastAnalysisAt) : '暂无', '最近一次分析完成时间', true) +
      '</div>';
  }

  function card(label, value, hint, small) {
    return '<div class="stat-card' + (small ? '' : ' accent') + '">' +
      '<span class="label">' + FL.escape(label) + '</span>' +
      '<span class="value"' + (small ? ' style="font-size:17px;margin:14px 0 8px"' : '') + '>' + FL.escape(value === null || value === undefined ? 0 : value) + '</span>' +
      '<span class="delta">' + FL.escape(hint) + '</span>' +
      '</div>';
  }

  function recentTasks(data) {
    var rows = (data.recentTasks || []).map(function (task) {
      var status = FL.TASK_STATUS[task.status] || { text: task.status, cls: 'muted' };
      return '<tr>' +
        '<td><strong>' + FL.escape(task.taskName) + '</strong><br /><span style="color:var(--muted)">' +
        FL.escape(task.recipeName || '—') + '</span></td>' +
        '<td>' + FL.escape(FL.goalText(task.goalType)) + '</td>' +
        '<td>' + FL.statusTag(status.text, status.cls) + '</td>' +
        '<td>' + FL.escape(FL.fmtDate(task.createdAt)) + '</td>' +
        '<td class="row-actions">' +
        (task.status === 'COMPLETED'
          ? '<a class="link-btn" href="' + FL.ctx + '/analysis-result.html?taskId=' + task.id + '">查看结果</a>'
          : '<span style="color:var(--muted)">—</span>') +
        '</td></tr>';
    }).join('');
    var body = rows
      ? '<div class="table-wrap"><table><thead><tr><th>任务 / 基准配方</th><th>分析类型</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table></div>'
      : FL.empty('还没有分析任务', '从「新建配方与分析」开始你的第一次风味推演。');
    return panel('最近分析任务', '<a class="link-btn" href="' + FL.ctx + '/analysis-history.html">全部记录 →</a>', body, true);
  }

  function recentRecipes(data) {
    var rows = (data.recentRecipes || []).map(function (recipe) {
      return '<tr>' +
        '<td><strong>' + FL.escape(recipe.name) + '</strong><br /><span style="color:var(--muted)">' +
        FL.escape(recipe.productType || '未填写产品类型') + '</span></td>' +
        '<td>' + FL.escape(recipe.itemCount === null || recipe.itemCount === undefined ? '—' : recipe.itemCount + ' 项配料') + '</td>' +
        '<td>' + FL.escape(FL.fmtDate(recipe.updatedAt)) + '</td>' +
        '<td class="row-actions">' +
        '<a class="link-btn" href="' + FL.ctx + '/recipe-edit.html?id=' + recipe.id + '">编辑配料</a>' +
        '<a class="link-btn" href="' + FL.ctx + '/analysis-run.html?recipeId=' + recipe.id + '">发起分析</a>' +
        '</td></tr>';
    }).join('');
    var body = rows
      ? '<div class="table-wrap"><table><thead><tr><th>配方名称</th><th>配料</th><th>最近修改</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table></div>'
      : FL.empty('还没有配方', '先录入一份基准配方，系统才能计算风味偏移。');
    return panel('我的配方', '<a class="link-btn" href="' + FL.ctx + '/recipes.html">配方库 →</a>', body, true);
  }

  function quickActions() {
    var items = [
      { href: '/recipe-edit.html', title: '新建配方', desc: '录入或编辑基准配方的配料明细与工艺参数', icon: '＋' },
      { href: '/analysis-run.html', title: '发起风味分析', desc: '导入一份配方作为基准，调整目标方案并推演风味变化', icon: '◑' },
      { href: '/recipes.html', title: '配方库', desc: '搜索、编辑、复制已有配方', icon: '☰' },
      { href: '/analysis-history.html', title: '分析历史', desc: '按类型与状态回看历史推演结果', icon: '◔' },
      { href: '/articles.html', title: '研发知识库', desc: '风味科学、原料知识与健康化改造文章', icon: '❖' }
    ];
    var html = items.map(function (item) {
      return '<a class="side-link" href="' + FL.ctx + item.href + '" style="flex-direction:column;align-items:flex-start;gap:4px;padding:12px;min-height:auto">' +
        '<strong>' + item.icon + ' ' + FL.escape(item.title) + '</strong>' +
        '<span style="color:var(--muted);font-size:11.5px">' + FL.escape(item.desc) + '</span></a>';
    }).join('');
    return panel('快捷入口', '', '<div class="side-nav" style="gap:8px">' + html + '</div>');
  }

  function latestArticles(data) {
    var items = (data.latestArticles || []).map(function (article) {
      return '<a class="side-link" href="' + FL.ctx + '/article-detail.html?id=' + article.id + '" style="flex-direction:column;align-items:flex-start;gap:3px;padding:11px;min-height:auto">' +
        '<strong style="font-size:12.5px">' + FL.escape(article.title) + '</strong>' +
        '<span style="color:var(--muted);font-size:11px">' + FL.escape(article.categoryName || '未分类') + ' · ' + FL.escape(FL.fmtDate(article.publishedAt || article.createdAt)) + '</span></a>';
    }).join('');
    return panel('最新研发知识', '<a class="link-btn" href="' + FL.ctx + '/articles.html">知识库 →</a>',
      items || FL.empty('暂无文章', '管理员可在后台发布研发知识文章。'));
  }

  function platformPanel(platform) {
    return panel('平台概览（管理员）', '', '<div class="panel-body">' +
      '<div class="stat-row" style="grid-template-columns:repeat(2,minmax(0,1fr))">' +
      miniStat('注册用户', platform.userCount) +
      miniStat('全部分析任务', platform.taskCountAll) +
      miniStat('食材数据', platform.ingredientCountAll) +
      miniStat('知识文章', platform.articleCountAll) +
      '</div>' +
      '<div class="form-actions" style="justify-content:flex-start;margin-top:16px">' +
      '<a class="secondary-btn" href="' + FL.ctx + '/admin/users.html">用户管理</a>' +
      '<a class="secondary-btn" href="' + FL.ctx + '/admin/ingredients.html">食材与规则</a>' +
      '<a class="secondary-btn" href="' + FL.ctx + '/admin/articles.html">文章管理</a>' +
      '<a class="secondary-btn" href="' + FL.ctx + '/admin/tasks.html">分析任务</a>' +
      '</div></div>');
  }

  function miniStat(label, value) {
    return '<div class="stat-card" style="padding:14px 16px"><span class="label">' + FL.escape(label) + '</span>' +
      '<span class="value" style="font-size:24px;margin:8px 0 0">' + FL.escape(value === null || value === undefined ? 0 : value) + '</span></div>';
  }

  function operationLogs(logs) {
    var list = (logs || []).map(function (line) {
      return '<li style="display:block;padding:7px 0;border-bottom:1px dashed var(--line);color:var(--muted);font-size:11.5px">' + FL.escape(line) + '</li>';
    }).join('');
    return panel('最近操作日志', '', '<div class="panel-body">' +
      (list ? '<ul class="snapshot-list">' + list + '</ul>' : FL.empty('暂无操作记录', '管理员的食材、文章、用户改动会记录在这里。')) +
      '</div>');
  }

  function panel(title, extra, body, flush) {
    return '<section class="panel">' +
      '<div class="panel-head"><h3>' + FL.escape(title) + '</h3><div>' + (extra || '') + '</div></div>' +
      (flush ? body : '<div class="panel-body">' + body + '</div>') +
      '</section>';
  }
}(window.jQuery, window.FL));
