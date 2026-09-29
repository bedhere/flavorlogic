/* ============================================================
   管理后台 · 食材与规则
   ------------------------------------------------------------
   接口：GET    /api/admin/ingredients?keyword=&category=&status=&page=&pageSize=
        GET    /api/admin/ingredients/categories
        POST   /api/admin/ingredients            新增
        PUT    /api/admin/ingredients/{id}       编辑
        DELETE /api/admin/ingredients/{id}       停用（status=0，不做物理删除）
        GET    /api/rules                        启用中的风味补偿规则（只读）
   说明：八维属性为 0-100 的相对评分，前端做区间校验，服务端二次校验；
        规则面板仅用于说明「建议为什么产生」，第一阶段不支持后台编辑。
   ============================================================ */
(function ($, FL) {
  'use strict';

  /** 八个风味维度（顺序与前台雷达图一致，fat_aroma 在 JSON 中为驼峰 fatAroma） */
  var DIMENSIONS = [
    { key: 'sweet', label: '甜' },
    { key: 'salty', label: '咸' },
    { key: 'sour', label: '酸' },
    { key: 'bitter', label: '苦' },
    { key: 'umami', label: '鲜' },
    { key: 'spicy', label: '辣' },
    { key: 'numbing', label: '麻' },
    { key: 'fatAroma', label: '脂香' }
  ];

  /** 分类接口不可用时的兜底选项（仅为下拉可用，不写入任何数据） */
  var FALLBACK_CATEGORIES = ['甜味料', '咸味料', '酸味料', '鲜味料', '香辛料', '油脂', '乳制品', '淀粉类', '果蔬类', '其他'];

  /** 食材列表查询条件 */
  var query = { keyword: '', category: '', status: '', page: 1, pageSize: 10 };

  /** Bootstrap Modal 实例（延迟创建） */
  var modal = null;

  FL.start({}, function () {
    FL.layout({
      active: 'admin-ingredients',
      title: '食材与规则',
      subtitle: '维护食材的八维风味属性，并查看规则引擎使用的风味补偿规则',
      crumb: '管理后台 / <strong>食材与规则</strong>',
      adminPage: true,
      content: renderShell()
    });
    bindEvents();
    renderModalDimensions();
    loadCategories();
    loadIngredients();
    loadRules();
  });

  /* ---------------- 页面外壳 ---------------- */

  /**
   * 渲染「页面外壳」：筛选工具栏 + 食材面板 + 规则面板的容器。
   *
   * 本页原先只渲染了 FL.loading 占位，工具栏与 #ingredientPanel / #rulePanel
   * 两个容器在 HTML 与 JS 中都不存在，导致 bindEvents() 的绑定落在空集合上、
   * loadIngredients()/loadRules() 的 .html() 写入无效，整页空白。
   * 这里把原本缺失的容器补齐，既有的 load* / render* 逻辑保持不变。
   *
   * 注意：#ingredientPanel 与 #rulePanel 会被 renderIngredients()/renderRules()
   * 整体覆盖，因此两者保持空容器，不带 panel-body 包装。
   *
   * @returns {String} 工具栏与两个面板容器的 HTML
   */
  function renderShell() {
    var html =
      '<div class="toolbar">' +
      '  <label class="search" for="ingredientKeyword"><span aria-hidden="true">⌕</span>' +
      '    <input type="search" id="ingredientKeyword" placeholder="搜索食材名称" autocomplete="off" aria-label="搜索食材名称" /></label>' +
      '  <div style="display:flex;flex-wrap:wrap;align-items:center;gap:10px">' +
      '    <div class="field" style="min-width:150px">' +
      '      <select id="ingredientCategoryFilter" aria-label="食材分类"><option value="">全部分类</option></select></div>' +
      '    <div class="field" style="min-width:130px">' +
      '      <select id="ingredientStatusFilter" aria-label="食材状态">' +
      '        <option value="">全部状态</option>' +
      '        <option value="1">启用</option>' +
      '        <option value="0">已停用</option>' +
      '      </select></div>' +
      '    <button class="primary-btn" type="button" id="ingredientSearchBtn">查询</button>' +
      '    <button class="secondary-btn" type="button" id="ingredientResetBtn">重置</button>' +
      '    <button class="primary-btn" type="button" id="ingredientCreateBtn">新增食材</button>' +
      '  </div>' +
      '</div>' +
      '<section class="panel">' +
      '  <div class="panel-head"><h3>食材风味属性</h3><div class="tag">加载中…</div></div>' +
      '  <div id="ingredientPanel">' + FL.loading('正在加载食材数据…') + '</div>' +
      '</section>' +
      '<section class="panel" style="margin-top:18px">' +
      '  <div class="panel-head"><h3>风味补偿规则（只读）</h3><div class="tag">初始化数据</div></div>' +
      '  <div id="rulePanel">' + FL.loading('正在加载风味补偿规则…') + '</div>' +
      '</section>';

    var body = $('#pageBody');
    // 首次由 FL.start 直接取 HTML 字符串交给 layout；容器已存在时只做幂等刷新
    if (body.length) { body.html(html); }
    return html;
  }

  /* ---------------- 分类下拉 ---------------- */

  function loadCategories() {
    FL.get('/api/admin/ingredients/categories').then(function (list) {
      var names = (list && list.length) ? list : FALLBACK_CATEGORIES;
      fillCategories(names);
    })['catch'](function (err) {
      fillCategories(FALLBACK_CATEGORIES);
      FL.toast(err.message || '食材分类加载失败，已使用示例分类', 'error', '分类不可用');
    });
  }

  function fillCategories(names) {
    $('#ingredientCategoryList').html(names.map(function (name) {
      return '<option value="' + FL.escape(name) + '"></option>';
    }).join(''));
    $('#ingredientCategoryFilter').html('<option value="">全部分类</option>' + names.map(function (name) {
      return '<option value="' + FL.escape(name) + '">' + FL.escape(name) + '</option>';
    }).join(''));
  }

  /* ---------------- 事件绑定 ---------------- */

  function bindEvents() {
    $('#ingredientSearchBtn').on('click', function () {
      collect();
      query.page = 1;
      loadIngredients();
    });
    $('#ingredientResetBtn').on('click', function () {
      $('#ingredientKeyword').val('');
      $('#ingredientCategoryFilter').val('');
      $('#ingredientStatusFilter').val('');
      collect();
      query.page = 1;
      loadIngredients();
    });
    $('#ingredientKeyword').on('keydown', function (event) {
      if (event.key === 'Enter') {
        event.preventDefault();
        $('#ingredientSearchBtn').trigger('click');
      }
    });
    $('#ingredientCreateBtn').on('click', function () {
      openEditor(null);
    });
    $('#ingredientSaveBtn').on('click', save);
    $('#ruleReloadBtn').on('click', loadRules);

    $(document).on('click', '#ingredientPanel [data-action]', function () {
      var $btn = $(this);
      var action = $btn.attr('data-action');
      if (action === 'edit') {
        openEditor(ingredientCache[String($btn.attr('data-id'))] || null);
      } else if (action === 'disable') {
        disableIngredient($btn, $btn.attr('data-id'), $btn.attr('data-name') || '该食材');
      }
    });
  }

  function collect() {
    query.keyword = $.trim($('#ingredientKeyword').val() || '');
    query.category = filterValue($('#ingredientCategoryFilter').val());
    query.status = filterValue($('#ingredientStatusFilter').val());
  }

  /** 下拉值归一化：空字符串按「全部」处理，状态 0（已停用）等合法值必须保留 */
  function filterValue(value) {
    if (value === null || value === undefined || value === '') { return ''; }
    return String(value);
  }

  /** 行内按钮只带编号，完整对象从当前列表缓存中取 */
  var ingredientCache = {};

  /* ---------------- 面板一：食材列表 ---------------- */

  function loadIngredients() {
    $('#ingredientPanel').html(FL.loading('正在加载食材数据…'));
    FL.get('/api/admin/ingredients' + FL.qs(query)).then(renderIngredients)['catch'](function (err) {
      $('#ingredientPanel').html(FL.empty('食材数据加载失败', err.message || '请稍后重试。'));
    });
  }

  function renderIngredients(data) {
    var list = (data && data.list) || [];
    ingredientCache = {};
    $.each(list, function (index, item) {
      ingredientCache[String(item.id)] = item;
    });

    var rows = list.map(function (item) {
      var enabled = Number(item.status) === 1;
      return '<tr>' +
        '<td><strong>' + FL.escape(item.name) + '</strong></td>' +
        '<td>' + (item.category ? FL.escape(item.category) : '<span style="color:var(--muted)">未分类</span>') + '</td>' +
        '<td>' + FL.escape(item.defaultUnit || 'g') + '</td>' +
        '<td style="color:var(--muted);font-size:11px">' + FL.escape(dimensionSummary(item)) + '</td>' +
        '<td>' + FL.statusTag(enabled ? '启用' : '已停用', enabled ? '' : 'muted') + '</td>' +
        '<td>' + FL.escape(FL.fmtDate(item.updatedAt)) + '</td>' +
        '<td><div class="row-actions">' +
        '<button type="button" class="link-btn" data-action="edit" data-id="' + FL.escape(item.id) + '">编辑</button>' +
        (enabled
          ? '<button type="button" class="link-btn danger" data-action="disable" data-id="' + FL.escape(item.id) +
            '" data-name="' + FL.escape(item.name) + '">停用</button>'
          : '<span style="color:var(--muted)">已停用</span>') +
        '</div></td></tr>';
    }).join('');

    var body = rows
      ? '<div class="table-wrap"><table id="ingredientTable"><thead><tr>' +
        '<th>名称</th><th>分类</th><th>默认单位</th><th>八维属性摘要</th><th>状态</th><th>更新时间</th><th>操作</th>' +
        '</tr></thead><tbody>' + rows + '</tbody></table></div>' + FL.pager(data, 'goPage')
      : FL.empty('没有匹配的食材', '换个关键字，或点击「新增食材」录入一条属性数据。');

    $('#ingredientPanel').html(
      '<div class="panel-head"><h3>食材风味属性</h3>' +
      '<div class="tag">共 ' + FL.escape((data && data.total) || 0) + ' 条食材（含已停用）</div></div>' +
      body +
      '<div class="panel-body" style="border-top:1px solid var(--line);color:var(--muted);font-size:11.5px;line-height:1.8">' +
      '八维属性为课设演示用的相对评分（0-100），停用食材后历史分析结果仍按快照保留，不受本次调整影响。' +
      '</div>'
    );
  }

  /** 属性摘要：只展示非零维度，全为 0 时给出明确提示 */
  function dimensionSummary(item) {
    var parts = [];
    $.each(DIMENSIONS, function (index, dimension) {
      var value = Number(item[dimension.key] || 0);
      if (value > 0) {
        parts.push(dimension.label + FL.fmtNumber(value, 0));
      }
    });
    return parts.length ? parts.join(' ') : '八维均为 0，未录入风味强度';
  }

  function disableIngredient($btn, id, name) {
    FL.confirm('停用「' + name + '」？停用后该食材不再出现在前台可选原料中（状态改为已停用，数据保留可随时改回启用）。', {
      title: '停用食材', okText: '确认停用', danger: true
    }).then(function (ok) {
      if (!ok) { return; }
      return guard($btn, function () {
        return FL.del('/api/admin/ingredients/' + id);
      }, '食材已停用').then(loadIngredients);
    })['catch'](function (err) {
      FL.toast(err.message || '停用失败', 'error', '操作未完成');
    });
  }

  /* ---------------- 面板二：风味补偿规则（只读） ---------------- */

  function loadRules() {
    $('#rulePanel').html(FL.loading('正在加载风味补偿规则…'));
    FL.get('/api/rules').then(renderRules)['catch'](function (err) {
      $('#rulePanel').html(
        '<div class="panel-head"><h3>风味补偿规则（只读）</h3>' +
      '<div><button type="button" class="secondary-btn" id="ruleReloadBtn">重新加载</button>' +
      '<span class="tag" style="margin-left:10px">规则由初始化数据提供，第一阶段仅在后台查看</span></div></div>' +
      FL.empty('规则数据加载失败', (err.message || '接口暂时不可用') + '。规则由初始化数据提供，可由管理员稍后重试。')
      );
    });
  }

  function renderRules(list) {
    var rows = (list || []).map(function (rule) {
      var goal = !rule.goalType || rule.goalType === 'GENERAL' ? '通用' : FL.goalText(rule.goalType);
      return '<tr>' +
        '<td><strong>' + FL.escape(rule.ruleName || '未命名规则') + '</strong>' +
        (rule.explanation ? '<br /><span style="color:var(--muted);font-size:11px">' + FL.escape(rule.explanation) + '</span>' : '') +
        '</td>' +
        '<td style="color:var(--muted);font-size:11px">' + FL.escape(rule.ruleCode || '—') + '</td>' +
        '<td>' + FL.statusTag(goal, 'info') + '</td>' +
        '<td>' + FL.escape(rule.priority === null || rule.priority === undefined ? '—' : rule.priority) + '</td>' +
        '<td>' + code(rule.conditionJson) + '</td>' +
        '<td>' + actionSummary(rule.actionJson) + '</td>' +
        '</tr>';
    }).join('');

    var body = rows
      ? '<div class="table-wrap"><table><thead><tr>' +
        '<th>规则名称 / 说明</th><th>规则编码</th><th>适用目标</th><th>优先级</th><th>触发条件</th><th>建议动作</th>' +
        '</tr></thead><tbody>' + rows + '</tbody></table></div>'
      : FL.empty('暂无启用的补偿规则', '规则由初始化数据提供，未启用任何规则时分析结果不会给出补偿建议。');

    $('#rulePanel').html(
      '<div class="panel-head"><h3>风味补偿规则（只读）</h3>' +
      '<div class="tag">规则由初始化数据提供，第一阶段仅在后台查看</div></div>' +
      body +
      '<div class="panel-body" style="border-top:1px solid var(--line);color:var(--muted);font-size:11.5px;line-height:1.8">' +
      '每条分析建议都能溯源到这里：规则引擎按条件（conditionJson）命中后，执行建议动作（actionJson）并生成解释文案。' +
      '本阶段不提供规则的新增与编辑入口，避免演示环境中的规则被误改。' +
      '</div>'
    );
  }

  /** 条件 JSON 原样小字展示（只读，不支持编辑） */
  function code(text) {
    if (!text) { return '<span style="color:var(--muted)">—</span>'; }
    return '<code style="display:inline-block;max-width:280px;padding:3px 6px;border-radius:5px;' +
      'background:var(--surface-2);color:var(--muted);font-size:11px;word-break:break-all">' + FL.escape(text) + '</code>';
  }

  /** 建议动作摘要：把 actionJson 里的关键字段拼成一行中文说明 */
  function actionSummary(text) {
    if (!text) { return '<span style="color:var(--muted)">—</span>'; }
    var action;
    try {
      action = JSON.parse(text);
    } catch (ignore) {
      return code(text);
    }
    var parts = [];
    if (action.dimension) { parts.push('维度 ' + dimensionText(action.dimension)); }
    if (action.changePercent !== undefined && action.changePercent !== null) {
      parts.push('用量 ' + (Number(action.changePercent) > 0 ? '+' : '') + action.changePercent + '%');
    }
    if (action.direction) { parts.push('方向 ' + directionText(action.direction)); }
    if (action.ingredientName) { parts.push('参考食材 ' + action.ingredientName); }
    if (action.targetIngredient) { parts.push('目标食材 ' + action.targetIngredient); }
    if (!parts.length) {
      parts.push(Object.keys(action).map(function (key) {
        return key + '=' + action[key];
      }).join('，'));
    }
    return '<span style="font-size:11.5px">' + FL.escape(parts.join(' · ')) + '</span><br />' + code(text);
  }

  function dimensionText(dimension) {
    for (var i = 0; i < DIMENSIONS.length; i++) {
      if (DIMENSIONS[i].key === dimension) { return DIMENSIONS[i].label; }
    }
    return dimension;
  }

  function directionText(direction) {
    if (direction === 'UP' || direction === 'INCREASE') { return '上调'; }
    if (direction === 'DOWN' || direction === 'DECREASE') { return '下调'; }
    return direction;
  }

  /* ---------------- 新增 / 编辑弹窗 ---------------- */

  /** 动态渲染八个维度输入框，避免在 HTML 中重复 8 段结构 */
  function renderModalDimensions() {
    $('#ingredientDimensions').html(DIMENSIONS.map(function (dimension) {
      return '<div class="field">' +
        '<label for="ingredient_' + dimension.key + '">' + FL.escape(dimension.label) + '</label>' +
        '<input type="number" id="ingredient_' + dimension.key + '" min="0" max="100" step="1" value="0" />' +
        '<div class="error"></div>' +
        '</div>';
    }).join(''));
  }

  function openEditor(item) {
    $('#ingredientModalTitle').text(item ? '编辑食材' : '新增食材');
    $('#ingredientId').val(item ? item.id : '');
    $('#ingredientName').val(item ? (item.name || '') : '');
    $('#ingredientCategory').val(item ? (item.category || '') : '');
    $('#ingredientUnit').val(item ? (item.defaultUnit || '') : '');
    $('#ingredientDataSource').val(item ? (item.dataSource || '') : '');
    $('#ingredientDescription').val(item ? (item.description || '') : '');
    $('#ingredientStatus').val(item && Number(item.status) === 0 ? '0' : '1');
    $.each(DIMENSIONS, function (index, dimension) {
      var value = item ? item[dimension.key] : null;
      $('#ingredient_' + dimension.key).val(value === null || value === undefined || value === '' ? 0 : Number(value));
    });
    FL.clearFieldErrors($('#ingredientForm'));
    $('#ingredientSaveBtn').prop('disabled', false).text('保存');
    if (!modal) {
      modal = new window.bootstrap.Modal(document.getElementById('ingredientModal'));
    }
    modal.show();
  }

  function save() {
    var $form = $('#ingredientForm');
    if (!FL.validate($form, [
      { name: '#ingredientName', label: '食材名称', required: true, max: 100 }
    ])) {
      return;
    }
    var body = {
      name: $.trim($('#ingredientName').val()),
      category: $.trim($('#ingredientCategory').val()),
      defaultUnit: $.trim($('#ingredientUnit').val()),
      description: $.trim($('#ingredientDescription').val()),
      dataSource: $.trim($('#ingredientDataSource').val()),
      status: Number($('#ingredientStatus').val())
    };

    // 八维必须落在 0-100（与后端校验保持一致）
    var valid = true;
    $.each(DIMENSIONS, function (index, dimension) {
      var $input = $('#ingredient_' + dimension.key);
      var raw = $.trim($input.val());
      var value = raw === '' ? 0 : Number(raw);
      if (raw === '' || isNaN(value) || value < 0 || value > 100) {
        FL.setFieldError($input, dimension.label + '必须是 0-100 之间的数字');
        valid = false;
        return;
      }
      body[dimension.key] = value;
    });
    if (!valid) {
      FL.toast('八维属性必须是 0-100 之间的数字', 'error', '请检查表单');
      return;
    }

    var id = $.trim($('#ingredientId').val() || '');
    // 提交期间禁用按钮，避免重复写入
    var $btn = $('#ingredientSaveBtn').prop('disabled', true).text('保存中…');
    var request = id ? FL.put('/api/admin/ingredients/' + id, body) : FL.post('/api/admin/ingredients', body);
    request.then(function () {
      FL.toast(id ? '食材已保存' : '食材已创建');
      if (modal) { modal.hide(); }
      loadIngredients();
    })['catch'](function (err) {
      // 名称重复等业务校验由服务端给出说明，保留弹窗内容便于修改后重试
      FL.toast(err.message || '保存失败，请检查表单内容', 'error', '保存未完成');
    })['finally'](function () {
      $btn.prop('disabled', false).text('保存');
    });
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
    loadIngredients();
  };
}(window.jQuery, window.FL));
