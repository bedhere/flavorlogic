/* ============================================================
   配方库：搜索、查看风味画像、编辑、复制为新配方、删除基准配方
   ------------------------------------------------------------
   数据来源：
     GET /api/recipes?keyword=&page=&pageSize=  —— 配方分页
     GET /api/recipes/profiles                  —— 全部配方的风味画像（只算不存）
   列表区域 #listHost 内统一渲染「加载中 / 空 / 失败 / 表格 + 分页」四种状态，
   工具栏与面板外壳只渲染一次，避免搜索时输入框失焦。
   ------------------------------------------------------------
   关于「查看画像」：画像由后端用与正式分析同一个 FlavorEngine 计算，
   但**不创建分析任务、不写分析历史**。分析历史只应记录「有对比」的分析，
   否则会被大量无信息量的记录挤占——那个模块承载的是可追溯性。
   ============================================================ */
(function ($, FL) {
  'use strict';

  /** 八维固定顺序：甜 / 咸 / 酸 / 苦 / 鲜 / 辣 / 麻 / 脂香（与后端 FlavorDimensions.ALL 一致） */
  var DIMENSIONS = [
    { key: 'sweet', label: '甜' },
    { key: 'salty', label: '咸' },
    { key: 'sour', label: '酸' },
    { key: 'bitter', label: '苦' },
    { key: 'umami', label: '鲜' },
    { key: 'spicy', label: '辣' },
    { key: 'numbing', label: '麻' },
    { key: 'fat_aroma', label: '脂香' }
  ];

  /** 与设计系统一致的图表配色（与结果页保持一致） */
  var PROFILE_COLOR = '#157a56';

  /** 列表行内风味标签最多显示几个维度 */
  var TAG_LIMIT = 3;

  /** 列表状态：关键词、页码、每页条数、风味画像（配方编号 → 画像） */
  var state = { keyword: '', page: 1, pageSize: 10, profiles: {} };

  /** 请求序号：快速连续搜索时只采纳最后一次请求的结果 */
  var requestSeq = 0;

  FL.start({}, function (user) {
    FL.layout({
      active: 'recipes',
      title: '配方库',
      subtitle: '管理你的基准配方：搜索、编辑、复制或删除，配方明细是风味分析的输入',
      crumb: '研发工作区 / <strong>配方库</strong>',
      actions: '<a class="secondary-btn" href="' + FL.ctx + '/dashboard.html">返回工作台</a>' +
        '<a class="primary-btn" href="' + FL.ctx + '/recipe-edit.html">新建配方</a>',
      content: FL.loading('正在加载配方列表…')
    });
    bindEvents();
    load();
  });

  /* ---------------- 事件绑定 ---------------- */

  function bindEvents() {
    var $body = $('#pageBody');
    // 搜索：回车或点击「搜索」按钮触发
    $body.on('click', '#searchBtn', function () { doSearch(); });
    $body.on('keydown', '#recipeKeyword', function (event) {
      if (event.key === 'Enter') {
        event.preventDefault();
        doSearch();
      }
    });
    $body.on('click', '#clearSearchBtn', function () {
      $('#recipeKeyword').val('');
      doSearch();
    });
    // 删除：二次确认后软删除，按钮进入禁用态防止重复提交
    $body.on('click', '.del-btn', function () {
      var $btn = $(this);
      removeRecipe($btn.attr('data-id'), $btn.attr('data-name'), $btn);
    });
    $body.on('click', '#retryBtn', function () { load(); });
    // 查看风味画像：纯展示，不产生分析任务与历史记录
    $body.on('click', '.profile-btn', function () {
      openProfile($(this).attr('data-id'));
    });
  }

  function doSearch() {
    state.keyword = $.trim($('#recipeKeyword').val() || '');
    state.page = 1;
    load();
  }

  /* ---------------- 数据加载 ---------------- */

  function load() {
    var seq = ++requestSeq;
    if ($('#listHost').length) {
      $('#listHost').html(FL.loading('正在加载配方列表…'));
    } else {
      $('#pageBody').html(FL.loading('正在加载配方列表…'));
    }
    // 画像与列表并行取。画像单独失败时不影响列表展示（标签位置显示「—」），
    // 因此这里把它的异常吞掉并降级成空集合。
    var listRequest = FL.get('/api/recipes' + FL.qs({
      keyword: state.keyword, page: state.page, pageSize: state.pageSize
    }));
    var profileRequest = FL.get('/api/recipes/profiles')['catch'](function () {
      return { profiles: [] };
    });
    Promise.all([listRequest, profileRequest])
      .then(function (results) {
        if (seq !== requestSeq) { return; }
        var page = results[0];
        state.profiles = indexProfiles(results[1]);
        // 删除最后一页的最后一条后，页码可能越界，自动回退一页
        if (!(page.list || []).length && page.page > 1 && page.total > 0) {
          state.page = page.page - 1;
          load();
          return;
        }
        if (!$('#listHost').length) { renderShell(); }
        renderList(page);
      })['catch'](function (err) {
        if (seq !== requestSeq) { return; }
        showError(err.message);
      });
  }

  /**
   * 把画像列表转成「配方编号 → 画像」的索引，供行渲染与弹窗按编号取用。
   *
   * <p>后端直接把 List 放进 {@code data} 里，所以 {@code data} 本身就是数组；
   * 这里同时兼容 {@code [ ... ]} 与 {@code { profiles: [ ... ] }} 两种形态，
   * 将来后端若改成包一层对象，前端不必跟着改。</p>
   *
   * @param {*} data 接口返回的 data
   * @return {Object} 配方编号（字符串）→ 画像
   */
  function indexProfiles(data) {
    var list = Array.isArray(data) ? data : ((data && data.profiles) || []);
    var map = {};
    list.forEach(function (profile) {
      if (profile && profile.recipeId !== null && profile.recipeId !== undefined) {
        map[String(profile.recipeId)] = profile;
      }
    });
    return map;
  }

  /** 首次进入时渲染工具栏与面板外壳，之后只刷新 #listHost */
  function renderShell() {
    var html =
      '<div class="toolbar">' +
      '  <div class="search">' +
      '    <input type="search" id="recipeKeyword" autocomplete="off" placeholder="按配方名称搜索，回车即可" value="' + FL.escape(state.keyword) + '" />' +
      '  </div>' +
      '  <div class="row-actions">' +
      '    <button type="button" class="secondary-btn hidden" id="clearSearchBtn">清空搜索</button>' +
      '    <button type="button" class="secondary-btn" id="searchBtn">搜索</button>' +
      '    <a class="primary-btn" href="' + FL.ctx + '/recipe-edit.html">新建配方</a>' +
      '  </div>' +
      '</div>' +
      '<section class="panel">' +
      '  <div class="panel-head"><h3>配方列表</h3><div id="listSummary" style="color:var(--muted);font-size:12px"></div></div>' +
      '  <div id="listHost"></div>' +
      '</section>';
    $('#pageBody').html(html);
  }

  /* ---------------- 列表渲染 ---------------- */

  function renderList(page) {
    var list = page.list || [];
    $('#clearSearchBtn').toggleClass('hidden', !state.keyword);
    $('#listSummary').text(summaryText(page));

    var rows = list.map(rowHtml).join('');
    var html;
    if (!rows) {
      html = state.keyword
        ? FL.empty('没有匹配的配方', '换个关键词试试，或点「清空搜索」查看全部配方。')
        : FL.empty('还没有配方', '先录入一份基准配方，系统才能计算风味偏移。');
    } else {
      html = '<div class="table-wrap"><table>' +
        '<thead><tr>' +
        '<th>配方名称</th><th>配料数</th><th>风味画像（点击查看）</th><th>版本号</th>' +
        '<th>最近修改时间</th><th>数据完整性</th><th>操作</th>' +
        '</tr></thead><tbody>' + rows + '</tbody></table></div>' +
        '<div class="panel-body">' + FL.pager(page, 'goPage') + '</div>';
    }
    $('#listHost').html(html);
  }

  function summaryText(page) {
    var text = '共 ' + (page.total || 0) + ' 份配方';
    if (state.keyword) { text += ' · 关键词「' + state.keyword + '」'; }
    return text;
  }

  function rowHtml(recipe) {
    var itemCount = (recipe.itemCount === null || recipe.itemCount === undefined)
      ? '—' : recipe.itemCount + ' 项';
    var versionNo = (recipe.versionNo === null || recipe.versionNo === undefined) ? 1 : recipe.versionNo;
    // 数据完整性由后端标记：有食材被停用/删除时无法参与风味加权
    var integrity = recipe.hasMissingAttribute
      ? FL.statusTag('有食材缺属性', 'warn')
      : FL.statusTag('属性完整', 'muted');
    var id = encodeURIComponent(recipe.id);
    return '<tr>' +
      '<td><strong>' + FL.escape(recipe.name) + '</strong><br />' +
      '<span style="color:var(--muted)">' + FL.escape(recipe.productType || '未填写产品类型') + '</span></td>' +
      '<td>' + FL.escape(itemCount) + '</td>' +
      '<td>' + profileCellHtml(recipe) + '</td>' +
      '<td><span class="tag">V' + FL.escape(versionNo) + '</span></td>' +
      '<td>' + FL.escape(FL.fmtDate(recipe.updatedAt)) + '</td>' +
      '<td>' + integrity + '</td>' +
      '<td class="row-actions">' +
      '<a class="link-btn" href="' + FL.ctx + '/recipe-edit.html?id=' + id + '">编辑配料</a>' +
      '<a class="link-btn" href="' + FL.ctx + '/recipe-generate.html?recipeId=' + id + '">生成配方</a>' +
      '<a class="link-btn" href="' + FL.ctx + '/analysis-run.html?recipeId=' + id + '">发起分析</a>' +
      '<a class="link-btn" href="' + FL.ctx + '/recipe-edit.html?copyFrom=' + id + '">复制为新配方</a>' +
      '<button type="button" class="link-btn danger del-btn" data-id="' + FL.escape(recipe.id) + '" ' +
      'data-name="' + FL.escape(recipe.name) + '">删除</button>' +
      '</td></tr>';
  }

  /* ---------------- 风味画像 ---------------- */

  /**
   * 渲染「风味画像」单元格：显示最强的前几个维度，点击弹出完整画像。
   *
   * <p>为什么不用迷你雷达图：八维压进几十像素的方形画布后，各轴只有二十来像素，
   * 形状差异基本看不出来；而「甜 54 · 酸 45 · 鲜 32」这种标签一眼就能读懂，
   * 需要看形状时再点开完整雷达图，信息密度更合理。</p>
   *
   * @param {Object} recipe 配方
   * @return {String} 单元格 HTML
   */
  function profileCellHtml(recipe) {
    var profile = state.profiles[String(recipe.id)];
    if (!profile) {
      return '<span style="color:var(--muted)">—</span>';
    }
    var tags = topDimensions(profile.scores, TAG_LIMIT);
    if (!tags.length) {
      return '<span style="color:var(--muted)">八维均为 0</span>';
    }
    var text = tags.map(function (item) {
      return item.label + ' ' + FL.fmtNumber(item.value, 1);
    }).join(' · ');
    var warn = profile.hasMissingAttribute ? ' ' + FL.statusTag('缺属性', 'warn') : '';
    return '<button type="button" class="link-btn profile-btn" data-id="' + FL.escape(recipe.id) + '" ' +
      'title="点击查看完整八维画像与雷达图">' + FL.escape(text) + '</button>' + warn;
  }

  /**
   * 取得分最高的前几个维度（0 分不列入）。
   *
   * @param {Object} scores 维度编码 → 得分
   * @param {Number} limit  最多返回几个
   * @return {Array} [{ label, value }]
   */
  function topDimensions(scores, limit) {
    var source = scores || {};
    return DIMENSIONS.map(function (item) {
      return { label: item.label, value: toNumber(source[item.key]) };
    }).filter(function (item) {
      return item.value > 0;
    }).sort(function (a, b) {
      return b.value - a.value;
    }).slice(0, limit || TAG_LIMIT);
  }

  /**
   * 数字兜底：接口给的是字符串或 null 时统一转成数字。
   *
   * @param {*} value 原值
   * @return {Number} 数值，非法时为 0
   */
  function toNumber(value) {
    var num = Number(value);
    return isNaN(num) ? 0 : num;
  }

  /**
   * 弹出配方画像：八维打分卡 + 雷达图 + 数据完整度提示。
   *
   * <p><b>纯展示</b>——不请求分析接口，因此不会产生分析任务与历史记录。</p>
   *
   * @param {String} recipeId 配方编号
   */
  function openProfile(recipeId) {
    var profile = state.profiles[String(recipeId)];
    if (!profile) {
      FL.toast('这份配方暂时算不出画像（可能所有配料都缺少风味属性数据）', 'warn', '无法查看');
      return;
    }
    var modalId = 'flProfileModal';
    $('#' + modalId).remove();

    var scores = profile.scores || {};
    var values = DIMENSIONS.map(function (item) { return toNumber(scores[item.key]); });
    var cards = DIMENSIONS.map(function (item, index) {
      var value = values[index];
      var cls = value >= 20 ? ' is-strong' : (value <= 1 ? ' is-faint' : '');
      return '<div class="score-card' + cls + '">' +
        '<span class="score-label">' + FL.escape(item.label) + '</span>' +
        '<span class="score-value">' + value.toFixed(1) + '</span>' +
        '</div>';
    }).join('');
    var total = values.reduce(function (sum, value) { return sum + value; }, 0);
    var average = values.length ? total / values.length : 0;
    var tags = topDimensions(scores, TAG_LIMIT);
    var summary = tags.length
      ? tags.map(function (item) { return item.label + ' ' + FL.fmtNumber(item.value, 1); }).join('、')
      : '八维得分均为 0';

    var completeness = toNumber(profile.completeness);
    var notice = completeness >= 100 ? '' :
      '<div class="notice" style="margin-bottom:14px">数据完整度 <strong>' +
      FL.fmtNumber(completeness, 0) + '%</strong>：部分配料缺少风味属性数据、未参与加权计算，' +
      '画像整体偏高还是偏低无法判断，请先补齐这些食材的属性。</div>';

    var html =
      '<div class="modal fade" id="' + modalId + '" tabindex="-1">' +
      '  <div class="modal-dialog modal-dialog-centered modal-lg">' +
      '    <div class="modal-content" style="border-radius:12px;border:1px solid var(--line)">' +
      '      <div class="modal-header" style="border-bottom:1px solid var(--line)">' +
      '        <h5 class="modal-title" style="font-size:15px">' +
      FL.escape(profile.recipeName || '配方画像') + '　<small style="color:var(--muted)">V' +
      FL.escape(profile.versionNo || 1) + '</small></h5>' +
      '        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="关闭"></button>' +
      '      </div>' +
      '      <div class="modal-body">' +
      '        <div class="notice" style="margin-bottom:14px">这是<strong>该配方自身</strong>的八维画像，' +
      '不与任何基准比较。要分析「改了会怎样」，请用下方的「发起改造分析」。</div>' +
      notice +
      '        <div class="score-grid">' + cards + '</div>' +
      '        <div class="score-summary">风味特征：<strong>' + FL.escape(summary) + '</strong>' +
      '　·　平均强度 <strong>' + FL.fmtNumber(average, 1) + '</strong> / 100</div>' +
      '        <div class="radar-wrap"><canvas id="profileRadar"></canvas></div>' +
      '      </div>' +
      '      <div class="modal-footer" style="border-top:1px solid var(--line)">' +
      '        <a class="secondary-btn" href="' + FL.ctx + '/recipe-edit.html?id=' +
      encodeURIComponent(recipeId) + '">编辑配料</a>' +
      '        <a class="primary-btn" href="' + FL.ctx + '/analysis-run.html?recipeId=' +
      encodeURIComponent(recipeId) + '">发起改造分析</a>' +
      '      </div>' +
      '    </div>' +
      '  </div>' +
      '</div>';

    var $el = $(html).appendTo('body');
    var modal = new window.bootstrap.Modal($el[0]);
    // 雷达必须在弹窗显示后再画：隐藏状态下 canvas 尺寸为 0，画出来会是空白
    $el.on('shown.bs.modal', function () {
      var canvas = document.getElementById('profileRadar');
      if (!canvas || !FL.radar) { return; }
      FL.radar(canvas, {
        labels: DIMENSIONS.map(function (item) { return item.label; }),
        series: [{
          name: '配方画像',
          color: PROFILE_COLOR,
          values: values
        }],
        height: 320
      });
    });
    $el.on('hidden.bs.modal', function () { $el.remove(); });
    modal.show();
  }

  function showError(message) {
    var html = FL.empty('配方列表加载失败', message || '请稍后重试') +
      '<div class="form-actions" style="justify-content:center;margin-top:0">' +
      '<button type="button" class="secondary-btn" id="retryBtn">重新加载</button></div>';
    if ($('#listHost').length) {
      $('#listHost').html(html);
    } else {
      $('#pageBody').html(html);
    }
  }

  /* ---------------- 删除 ---------------- */

  function removeRecipe(id, name, $btn) {
    if (!id) { return; }
    FL.confirm('删除后「' + name + '」不再出现在配方库与新建分析的候选中，历史分析记录仍可回看。确定删除吗？',
      { title: '删除配方', okText: '删除', danger: true }).then(function (ok) {
      if (!ok) { return; }
      $btn.prop('disabled', true).text('删除中…').css('opacity', '.55');
      FL.del('/api/recipes/' + encodeURIComponent(id)).then(function () {
        FL.toast('配方「' + name + '」已删除', 'success', '操作完成');
        // 本页只剩这一条时回退一页，避免停留在空页
        if (state.page > 1 && $('#listHost tbody tr').length <= 1) {
          state.page = state.page - 1;
        }
        load();
      })['catch'](function (err) {
        $btn.prop('disabled', false).text('删除').css('opacity', '');
        FL.toast(err.message || '删除失败，请稍后重试', 'error', '操作未完成');
      });
    });
  }

  /* ---------------- 分页入口（FL.pager 通过内联 onclick 调用，必须挂在 window 上） ---------------- */

  window.goPage = function (page) {
    var target = Number(page);
    if (!target || target < 1 || target === state.page) { return; }
    state.page = target;
    load();
  };
}(window.jQuery, window.FL));
