/* ============================================================
   配方录入 / 编辑 / 复制
   ------------------------------------------------------------
   三种进入方式（URL 参数）：
     无参数          → 新建配方
     ?id=12          → 编辑已有配方（GET /api/recipes/12 回填）
     ?copyFrom=12    → 以已有配方为模板新建（清空编号与版本号，名称加「（副本）」）
   若同时带 id 与 copyFrom，以 id 为准（编辑优先）。
   ------------------------------------------------------------
   页面由两块面板组成：
     1) 基准配方 —— 配方主体信息（POST / PUT 提交）
     2) 配料明细 —— 基准配料行，含八维风味属性摘要
   ------------------------------------------------------------
   注意：本页**只负责编辑配方**。「发起风味分析」已经拆成独立页面
   analysis-run.html?recipeId=N（js/analysis-run.js），两页共用同一套配料行
   编辑交互与样式类（.item-row / .ingredient-select / .amount-input / .unit-select
   / .attr-hint 等）；修改配料行相关代码时请同步 analysis-run.js，避免两页体验分叉。
   食材选择已抽成共享模块 js/fl-ingredient-picker.js（可搜索下拉）：
   .ingredient-select 是被隐藏的原生 select，仍然是唯一数据源；行内食材状态
   变化后由 syncRow() 调用 FL.ingredientPicker.sync($tr) 回填搜索框显示。
   配方已保存（有配方编号）时，本页底部只渲染一个跳转到分析页的入口。
   ============================================================ */
(function ($, FL) {
  'use strict';

  /** 用量单位候选（演示口径，与后端 recipe_item.unit 一致） */
  var UNITS = ['g', 'ml', '个', '片'];

  /** 八维风味维度：接口字段名 → 中文名，顺序与后端 FlavorDimensions.ALL 一致 */
  var ATTRS = [
    ['sweet', '甜'], ['salty', '咸'], ['sour', '酸'], ['bitter', '苦'],
    ['umami', '鲜'], ['spicy', '辣'], ['numbing', '麻'], ['fatAroma', '脂香']
  ];

  /** 用量区间与后端 ValidationUtil.requireDecimal 的校验区间保持一致 */
  var AMOUNT_MIN = 0.001;
  var AMOUNT_MAX = 999999.999;

  var state = {
    mode: 'create',           // create | edit | copy
    recipeId: null,           // 当前编辑的配方编号（新建保存成功后回填）
    sourceId: null,           // 复制来源编号
    versionNo: null,          // 当前版本号
    sourceName: '',           // 复制来源名称
    sourceVersion: null,      // 复制来源版本号
    metaNote: '',             // 面板头部的补充说明（最近修改 / 已保存）
    ingredients: [],
    ingredientMap: {},
    ingredientOptionsHtml: '',
    saving: false
  };

  FL.start({}, function (user) {
    var idParam = FL.query('id');
    var copyParam = FL.query('copyFrom');
    if (idParam) {
      state.mode = 'edit';
      state.recipeId = idParam;
    } else if (copyParam) {
      state.mode = 'copy';
      state.sourceId = copyParam;
    }

    FL.layout({
      active: state.mode === 'edit' ? 'recipes' : 'recipe-edit',
      title: titleOf(),
      subtitle: subtitleOf(),
      crumb: crumbOf(),
      actions: '<a class="secondary-btn" href="' + FL.ctx + '/recipes.html">返回配方库</a>',
      content: FL.loading('正在加载食材属性…')
    });
    bindEvents();
    bootstrap();
  });

  /* ---------------- 模式相关的文案 ---------------- */

  function titleOf() {
    if (state.mode === 'edit') { return '编辑配方'; }
    if (state.mode === 'copy') { return '复制配方'; }
    return '新建配方与分析';
  }

  function subtitleOf() {
    if (state.mode === 'edit') { return '修改基准配方明细后保存，版本号会自动 +1；风味分析请到分析页发起，历史分析记录保持原样'; }
    if (state.mode === 'copy') { return '以已有配方为模板新建一份配方，保存后会生成新的配方编号与版本'; }
    return '录入基准配方主体信息与配料明细，保存后即可到「发起风味分析」页调整目标方案';
  }

  function crumbOf() {
    if (state.mode === 'edit') { return '研发工作区 / <strong>编辑配方</strong>'; }
    if (state.mode === 'copy') { return '研发工作区 / <strong>复制配方</strong>'; }
    return '研发工作区 / <strong>新建配方</strong>';
  }

  /* ---------------- 首次加载 ---------------- */

  function bootstrap() {
    var tasks = [FL.get('/api/ingredients')];
    if (state.recipeId) {
      tasks.push(FL.get('/api/recipes/' + encodeURIComponent(state.recipeId)));
    } else if (state.sourceId) {
      tasks.push(FL.get('/api/recipes/' + encodeURIComponent(state.sourceId)));
    }
    Promise.all(tasks).then(function (results) {
      applyIngredients(results[0]);
      renderPage(results[1] || null);
    })['catch'](function (err) {
      $('#pageBody').html(FL.empty('页面数据加载失败', err.message || '请稍后重试') + retryBlock());
    });
  }

  function retryBlock() {
    return '<div class="form-actions" style="justify-content:center;margin-top:0">' +
      '<button type="button" class="secondary-btn" id="retryBtn">重新加载</button></div>';
  }

  function applyIngredients(data) {
    state.ingredients = (data && data.list) || [];
    state.ingredientMap = {};
    state.ingredients.forEach(function (ingredient) {
      state.ingredientMap[String(ingredient.id)] = ingredient;
    });
    state.ingredientOptionsHtml = buildIngredientOptions((data && data.categories) || []);
  }

  /* ---------------- 页面骨架 ---------------- */

  function renderPage(recipe) {
    var html = '<div style="display:grid;gap:18px">' +
      basePanel() +
      itemsPanel() +
      '<div id="analysisPanelHost"></div>' +
      '</div>';
    $('#pageBody').html(html);

    if (recipe) { fillRecipeForm(recipe); }
    if (recipe && (recipe.items || []).length) {
      recipe.items.forEach(function (item) { appendItemRow($('#itemBody'), item); });
    } else {
      appendItemRow($('#itemBody'), null);   // 至少保留一行
    }
    updateBaseStats();
    renderAnalysisPanel();
  }

  function panel(title, extra, body, flush) {
    return '<section class="panel">' +
      '<div class="panel-head"><h3>' + FL.escape(title) + '</h3><div>' + (extra || '') + '</div></div>' +
      (flush ? body : '<div class="panel-body">' + body + '</div>') +
      '</section>';
  }

  /** 面板 1：基准配方主体信息 */
  function basePanel() {
    var body =
      '<div class="form-grid" id="recipeForm">' +
      '  <div class="field">' +
      '    <label for="recipeName">配方名称 <span class="req">*</span></label>' +
      '    <input type="text" id="recipeName" maxlength="100" autocomplete="off" placeholder="例如：低糖番茄火锅底料" />' +
      '    <span class="hint">用于配方库检索与结果页标题，建议写清产品定位</span>' +
      '    <span class="error"></span>' +
      '  </div>' +
      '  <div class="field">' +
      '    <label for="productType">产品类型</label>' +
      '    <input type="text" id="productType" maxlength="50" autocomplete="off" placeholder="例如：复合调味酱 / 汤料 / 饮料" />' +
      '    <span class="hint">选填，便于按品类归档</span>' +
      '    <span class="error"></span>' +
      '  </div>' +
      '  <div class="field span-2">' +
      '    <label for="processNote">工艺说明</label>' +
      '    <textarea id="processNote" maxlength="2000" placeholder="例如：香辛料 90℃ 油浸 20 分钟后过滤，冷却至 60℃ 与酱体混合均质"></textarea>' +
      '    <span class="hint">选填，记录火候、时间、投料顺序等无法量化的工艺信息（最多 2000 字）</span>' +
      '    <span class="error"></span>' +
      '  </div>' +
      '  <div class="field span-2">' +
      '    <label for="remark">备注</label>' +
      '    <textarea id="remark" maxlength="500" placeholder="选填，例如：本版为对照样，仅用于内部评审"></textarea>' +
      '    <span class="hint">选填，最多 500 字</span>' +
      '    <span class="error"></span>' +
      '  </div>' +
      '</div>';
    return '<section class="panel">' +
      '<div class="panel-head"><h3>基准配方</h3>' +
      '<div id="recipeMeta" style="color:var(--muted);font-size:12px">' + FL.escape(metaText()) + '</div></div>' +
      '<div class="panel-body">' + body + '</div></section>';
  }

  /** 面板 2：基准配料明细 */
  function itemsPanel() {
    var extra = '<button type="button" class="secondary-btn" id="addItemBtn">+ 添加配料</button>';
    // 启用食材库为空时给出明确引导，避免用户对着空下拉框反复点击
    var emptyHint = state.ingredients.length ? '' :
      '<div class="panel-body" style="border-bottom:1px solid var(--line)">' +
      '<div class="notice">启用中的食材库为空：请联系管理员在「食材与规则」中维护食材及其八维风味属性，' +
      '之后才能录入配料并发起分析。</div></div>';
    var body = emptyHint +
      '<div class="table-wrap"><table>' +
      '<thead><tr>' +
      '<th>食材（含八维风味属性摘要）</th>' +
      '<th style="width:140px">用量</th>' +
      '<th style="width:110px">单位</th>' +
      '<th style="width:90px">操作</th>' +
      '</tr></thead>' +
      '<tbody id="itemBody"></tbody>' +
      '</table></div>' +
      '<div class="panel-body" style="border-top:1px solid var(--line)">' +
      '  <div class="form-actions" style="justify-content:space-between;margin-top:0;gap:12px;flex-wrap:wrap">' +
      '    <span id="baseStats" style="color:var(--muted);font-size:12px"></span>' +
      '    <span class="row-actions">' +
      '      <button type="button" class="primary-btn" id="saveBtn">保存配方</button>' +
      '    </span>' +
      '  </div>' +
      '</div>';
    return panel('配料明细', extra, body, true);
  }

  /* ---------------- 面板 3：发起风味分析的入口 ---------------- */

  /**
   * 分析表单已拆到独立页面 analysis-run.html，这里只渲染一个跳转入口：
   *   · 配方还没有编号（未保存）→ 提示先保存，沿用原来的文案风格；
   *   · 已有配方编号         → 给「发起风味分析」主按钮，带上当前配方编号。
   * 函数名保持不变：saveRecipe 成功分支（尤其是新建成功）会调用它来刷新这块面板。
   */
  function renderAnalysisPanel() {
    var $host = $('#analysisPanelHost');
    if (!$host.length) { return; }
    if (!state.recipeId) {
      $host.html(panel('发起风味分析',
        '<a class="link-btn" href="' + FL.ctx + '/analysis-history.html">分析历史 →</a>',
        '<div class="notice">配方还没有保存，暂时无法发起风味分析：分析任务必须引用一条已保存的基准配方。' +
        '请先在上方「配料明细」中点击「保存配方」，保存成功后这里会自动解锁。</div>'));
      return;
    }

    var body =
      '<div class="notice">配方明细保存后即可发起风味分析：在分析页里可以调整目标方案的用量、替换食材或增删行，' +
      '再选择分析类型与目标区域，系统会对比两套方案的八维风味偏移并给出补偿建议。</div>' +
      '<div class="form-actions" style="justify-content:space-between;margin-top:16px;gap:12px;flex-wrap:wrap">' +
      '  <span id="analysisEntryHint" style="color:var(--muted);font-size:12px;line-height:42px">' +
      FL.escape(analysisEntryHintText()) + '</span>' +
      '  <span class="row-actions">' +
      '    <a class="secondary-btn" href="' + FL.ctx + '/analysis-history.html">查看分析历史</a>' +
      '    <a class="primary-btn" href="' + analysisEntryUrl() + '">发起风味分析</a>' +
      '  </span>' +
      '</div>';
    $host.html(panel('发起风味分析',
      '<a class="link-btn" href="' + FL.ctx + '/analysis-history.html">分析历史 →</a>',
      body));
  }

  function analysisEntryHintText() {
    var text = '基准配方已保存为 V' + (state.versionNo || 1) + '，可以发起分析';
    if (state.metaNote === '已保存') {
      return text + '；刚做过的修改请先点「保存配方」，分析页取到的是保存后的明细。';
    }
    return text + '；分析页会按当前已保存的明细导入目标方案。';
  }

  function analysisEntryUrl() {
    return FL.ctx + '/analysis-run.html?recipeId=' + encodeURIComponent(state.recipeId);
  }

  /* ---------------- 配料行 ---------------- */

  function buildIngredientOptions(categories) {
    var groups = {};
    var order = [];
    state.ingredients.forEach(function (ingredient) {
      var category = ingredient.category || '未分类';
      if (!groups[category]) {
        groups[category] = [];
        order.push(category);
      }
      groups[category].push('<option value="' + FL.escape(ingredient.id) + '"' +
        ' data-name="' + FL.escape(ingredient.name) + '"' +
        ' data-category="' + FL.escape(category) + '"' +
        ' data-unit="' + FL.escape(ingredient.defaultUnit || 'g') + '">' +
        FL.escape(ingredient.name) + '</option>');
    });
    // 分类下拉顺序优先取后端返回的分类列表，保证与管理端一致
    order.sort(function (a, b) {
      var indexA = categories.indexOf(a);
      var indexB = categories.indexOf(b);
      return (indexA < 0 ? 999 : indexA) - (indexB < 0 ? 999 : indexB);
    });
    var html = '<option value="">请选择食材</option>';
    order.forEach(function (category) {
      html += '<optgroup label="' + FL.escape(category) + '">' + groups[category].join('') + '</optgroup>';
    });
    return html;
  }

  function unitOptions() {
    return UNITS.map(function (unit) {
      return '<option value="' + FL.escape(unit) + '">' + FL.escape(unit) + '</option>';
    }).join('');
  }

  function itemRowHtml() {
    return '<tr class="item-row">' +
      '<td>' +
      /* 可搜索下拉：搜索框负责交互，隐藏的 .ingredient-select 负责存值，
         两者由 js/fl-ingredient-picker.js 与 syncRow() 保持同步 */
      '<div class="ing-picker">' +
      '<input type="text" class="table-inline-input ing-search" placeholder="输入食材名筛选" autocomplete="off" spellcheck="false" />' +
      '<button type="button" class="ing-toggle" tabindex="-1" aria-label="展开食材列表">▼</button>' +
      '<select class="ingredient-select" tabindex="-1" aria-hidden="true">' +
      (state.ingredientOptionsHtml || '<option value="">请选择食材</option>') +
      '</select>' +
      '</div>' +
      '<input type="hidden" class="ingredient-name" value="" />' +
      '<div class="attr-hint" style="color:var(--muted);font-size:11px;line-height:1.6;margin-top:5px">未选择食材</div>' +
      '</td>' +
      '<td><input type="number" class="table-inline-input num amount-input" min="' + AMOUNT_MIN + '" max="' + AMOUNT_MAX + '" step="0.1" value="100" /></td>' +
      '<td><select class="table-inline-input unit-select">' + unitOptions() + '</select></td>' +
      '<td class="row-actions"><button type="button" class="link-btn danger remove-btn">移除</button></td>' +
      '</tr>';
  }

  /**
   * 追加一行配料。
   * @param {jQuery} $tbody 目标 tbody
   * @param {Object} item   已有明细（可空，为空时给一行默认值）
   */
  function appendItemRow($tbody, item) {
    var $tr = $(itemRowHtml());
    $tbody.append($tr);
    if (item && item.ingredientId) {
      selectIngredient($tr.find('.ingredient-select'), item.ingredientId, item.ingredientName);
      var amount = (item.amount === null || item.amount === undefined || item.amount === '') ? '100' : item.amount;
      $tr.find('.amount-input').val(amount);
      setRowUnit($tr, item.unit);
    }
    syncRow($tr, false);
    return $tr;
  }

  /** 选中食材；食材已停用或删除时补占位选项，保证历史明细不丢失 */
  function selectIngredient($select, ingredientId, fallbackName) {
    var value = (ingredientId === null || ingredientId === undefined) ? '' : String(ingredientId);
    if (!value) { return false; }
    $select.val(value);
    if ($select.val() !== value) {
      var name = fallbackName || '未知食材';
      $select.append('<option value="' + FL.escape(value) + '"' +
        ' data-name="' + FL.escape(name) + '" data-category="" data-unit="g" data-missing="1">' +
        FL.escape(name) + '（已停用，无属性数据）</option>');
      $select.val(value);
    }
    return $select.val() === value;
  }

  function setRowUnit($tr, unit) {
    var value = unit || 'g';
    var $select = $tr.find('.unit-select');
    var exists = false;
    $select.find('option').each(function () {
      if ($(this).val() === value) { exists = true; }
    });
    if (!exists) {
      $select.append('<option value="' + FL.escape(value) + '">' + FL.escape(value) + '</option>');
    }
    $select.val(value);
  }

  /**
   * 同步一行的食材名称、属性摘要与默认单位。
   * @param {jQuery} $tr            行
   * @param {Boolean} useDefaultUnit 是否把单位重置为食材默认单位（仅在用户换食材时为 true）
   */
  function syncRow($tr, useDefaultUnit) {
    var $select = $tr.find('.ingredient-select');
    var $option = $select.find('option:selected');
    var id = $.trim($select.val() || '');
    var name = id ? ($option.attr('data-name') || '') : '';
    $tr.find('.ingredient-name').val(name);
    $tr.attr('data-ingredient-id', id || '');
    $tr.find('.attr-hint').text(attributeSummary(id));
    if (id && useDefaultUnit) {
      var unit = $option.attr('data-unit') || 'g';
      if (UNITS.indexOf(unit) < 0) { unit = 'g'; }
      setRowUnit($tr, unit);
    }
    $select.data('prevValue', id);
    // 搜索框的显示统一从 select 反推：这样「重复食材被回退」「历史明细回填」
    // 「已停用食材占位」三种情况都不会出现显示与数据不一致
    if (FL.ingredientPicker) { FL.ingredientPicker.sync($tr); }
  }

  /** 八维属性摘要：按固定顺序列出八维相对评分，便于理解分析的计算依据 */
  function attributeSummary(id) {
    if (!id) { return '未选择食材'; }
    var ingredient = state.ingredientMap[id];
    if (!ingredient) { return '该食材已不在启用食材库中，分析时不会参与加权'; }
    var parts = [];
    var usable = false;
    ATTRS.forEach(function (pair) {
      var value = Number(ingredient[pair[0]] || 0);
      if (value > 0) { usable = true; }
      parts.push(pair[1] + ' ' + FL.fmtNumber(value, 0));
    });
    return usable ? parts.join(' · ') : '八维属性均为 0，暂不贡献风味';
  }

  /** 同一张明细表内是否已存在该食材 */
  function isDuplicate($tbody, $tr, id) {
    var duplicated = false;
    $tbody.find('.item-row').each(function () {
      if (this === $tr[0]) { return; }
      if ($.trim($(this).find('.ingredient-select').val() || '') === String(id)) {
        duplicated = true;
      }
    });
    return duplicated;
  }

  /**
   * 校验并收集表格内的配料。
   * @param {jQuery} $tbody 明细表 tbody
   * @return {{items: Array, error: String, $tr: jQuery}} 校验失败时 error 有值，$tr 为出错行
   */
  function collectItems($tbody) {
    var items = [];
    var seen = {};
    var error = null;
    var $errorRow = null;
    $tbody.find('.item-row').each(function () {
      if (error) { return; }
      var $tr = $(this);
      var index = $tr.index() + 1;
      var id = $.trim($tr.find('.ingredient-select').val() || '');
      var name = $.trim($tr.find('.ingredient-name').val() || '');
      var amountText = $.trim($tr.find('.amount-input').val() || '');
      var unit = $.trim($tr.find('.unit-select').val() || 'g');
      if (!id || !name) {
        error = '第 ' + index + ' 项还没有选择食材';
        $errorRow = $tr;
        return;
      }
      if (seen[name]) {
        error = '第 ' + index + ' 项「' + name + '」重复，请合并用量或移除重复行';
        $errorRow = $tr;
        return;
      }
      var amount = Number(amountText);
      if (!amountText || isNaN(amount) || amount < AMOUNT_MIN || amount > AMOUNT_MAX) {
        error = '第 ' + index + ' 项「' + name + '」的用量需在 ' + AMOUNT_MIN + ' ~ ' + AMOUNT_MAX + ' 之间';
        $errorRow = $tr;
        return;
      }
      seen[name] = true;
      items.push({
        ingredientId: Number(id),
        ingredientName: name,
        amount: amount,
        unit: unit || 'g'
      });
    });
    return { items: items, error: error, $tr: $errorRow };
  }

  function focusRow($tr) {
    if (!$tr || !$tr.length) { return; }
    var el = $tr.find('.ing-search').get(0) || $tr.find('input, select').get(0);
    if (el) { el.focus(); }
  }

  /* ---------------- 底部统计 ---------------- */

  function collectStats($tbody) {
    var count = 0;
    var total = 0;
    var missing = 0;
    $tbody.find('.item-row').each(function () {
      var $tr = $(this);
      var id = $.trim($tr.find('.ingredient-select').val() || '');
      if (!id) { return; }
      count++;
      var amount = Number($tr.find('.amount-input').val());
      if (!isNaN(amount) && amount > 0) { total += amount; }
      if (!state.ingredientMap[id]) { missing++; }
    });
    return { count: count, total: total, missing: missing };
  }

  function updateBaseStats() {
    var stats = collectStats($('#itemBody'));
    var text = '配料项数 ' + stats.count + ' 项 · 总用量 ' + FL.fmtNumber(stats.total, 2) +
      '（演示口径：直接累加各配料用量数值，不做单位换算）';
    if (stats.missing > 0) {
      text += ' · 其中 ' + stats.missing + ' 项食材已不在启用食材库中，分析时不会参与加权';
    }
    $('#baseStats').text(text);
  }

  /* ---------------- 回填 ---------------- */

  function fillRecipeForm(recipe) {
    if (state.mode === 'copy') {
      state.sourceName = recipe.name || '';
      state.sourceVersion = recipe.versionNo || 1;
      $('#recipeName').val(copyName(recipe.name));
    } else {
      state.versionNo = recipe.versionNo || 1;
      state.metaNote = '最近修改 ' + FL.fmtDate(recipe.updatedAt);
      $('#recipeName').val(recipe.name || '');
      // 面包屑补上具体配方名，方便判断当前编辑的是哪一份
      $('.crumb').html('研发工作区 / <a href="' + FL.ctx + '/recipes.html">配方库</a> / <strong>' +
        FL.escape(recipe.name || '') + '</strong>');
    }
    $('#productType').val(recipe.productType || '');
    $('#processNote').val(recipe.processNote || '');
    $('#remark').val(recipe.remark || '');
    $('#recipeMeta').text(metaText());
  }

  /** 复制时名称加「（副本）」，并保证不超过后端 100 字上限 */
  function copyName(name) {
    var suffix = '（副本）';
    var base = $.trim(name || '') || '未命名配方';
    if (base.length + suffix.length > 100) {
      base = base.substring(0, 100 - suffix.length);
    }
    return base + suffix;
  }

  function metaText() {
    if (state.recipeId) {
      var text = '当前版本 V' + (state.versionNo || 1);
      if (state.metaNote) { text += ' · ' + state.metaNote; }
      return text;
    }
    if (state.sourceName) {
      return '复制自「' + state.sourceName + '」V' + (state.sourceVersion || 1) + ' · 保存后生成新配方';
    }
    return '尚未保存 · 保存后即可发起风味分析';
  }

  /* ---------------- 保存配方 ---------------- */

  function saveRecipe() {
    if (state.saving) { return; }
    var valid = FL.validate($('#pageBody'), [
      { name: '#recipeName', label: '配方名称', required: true, max: 100 },
      { name: '#productType', label: '产品类型', max: 50 },
      { name: '#processNote', label: '工艺说明', max: 2000 },
      { name: '#remark', label: '备注', max: 500 }
    ]);
    if (!valid) { return; }

    var base = collectItems($('#itemBody'));
    if (base.error) {
      FL.toast(base.error, 'error', '配料明细不完整');
      focusRow(base.$tr);
      return;
    }
    if (!base.items.length) {
      FL.toast('配方至少需要一项配料', 'error', '无法保存');
      return;
    }

    var payload = {
      name: $.trim($('#recipeName').val() || ''),
      productType: $.trim($('#productType').val() || '') || null,
      processNote: $.trim($('#processNote').val() || '') || null,
      remark: $.trim($('#remark').val() || '') || null,
      items: base.items
    };
    var editing = !!state.recipeId;
    var $btn = $('#saveBtn');
    state.saving = true;
    $btn.prop('disabled', true).text('保存中…').css('opacity', '.6');

    var request = editing
      ? FL.put('/api/recipes/' + encodeURIComponent(state.recipeId), payload)
      : FL.post('/api/recipes', payload);

    request.then(function (saved) {
      state.saving = false;
      $btn.prop('disabled', false).text('保存配方').css('opacity', '');
      var savedId = (saved && saved.id) ? saved.id : state.recipeId;
      state.versionNo = (saved && saved.versionNo) ? saved.versionNo : (state.versionNo || 1);
      state.metaNote = '已保存';
      state.recipeId = String(savedId);
      $('#recipeMeta').text(metaText());

      // 统一的保存反馈：屏幕居中弹出「保存成功」，1.3 秒后自动消失并返回配方库。
      // 新建与编辑都回配方库——新建时能在列表首行立刻看到刚保存的配方与版本号，
      // 需要继续分析时从配方库点「发起分析」即可（编辑与分析已拆成两个入口）。
      var message = (editing ? '配方已保存为 V' : '配方已创建为 V') + state.versionNo + '，正在返回配方库…';
      FL.success(message, { title: '保存成功', duration: 1300, redirect: '/recipes.html' });
    })['catch'](function (err) {
      state.saving = false;
      $btn.prop('disabled', false).text('保存配方').css('opacity', '');
      FL.toast(err.message || '保存失败，请稍后重试', 'error', '保存未完成');
    });
  }

  /* ---------------- 提交分析 ---------------- */
  /* 分析表单已整体迁移到独立页面：见 analysis-run.html / js/analysis-run.js，
     原 submitAnalysis、splitList 已从本文件移除，避免两处逻辑分叉。 */

  /* ---------------- 事件绑定（委托到 #pageBody，面板重建后依然生效） ---------------- */

  function bindEvents() {
    var $body = $('#pageBody');

    $body.on('click', '#retryBtn', function () { bootstrap(); });

    // 新增配料行
    $body.on('click', '#addItemBtn', function () {
      appendItemRow($('#itemBody'), null);
      updateBaseStats();
    });

    // 移除配料行：基准明细至少保留一行
    $body.on('click', '.remove-btn', function () {
      var $tr = $(this).closest('.item-row');
      var $tbody = $tr.closest('tbody');
      if ($tbody.find('.item-row').length <= 1) {
        FL.toast('配料明细至少保留一项，请先添加替代配料', 'warn', '无法移除');
        return;
      }
      $tr.remove();
      updateBaseStats();
    });

    // 切换食材：重复食材立即阻止，正常切换时同步名称、属性摘要与默认单位
    $body.on('change', '.ingredient-select', function () {
      var $select = $(this);
      var $tr = $select.closest('.item-row');
      var $tbody = $tr.closest('tbody');
      var id = $.trim($select.val() || '');
      var name = id ? ($select.find('option:selected').attr('data-name') || '') : '';
      if (id && isDuplicate($tbody, $tr, id)) {
        $select.val($select.data('prevValue') || '');
        syncRow($tr, false);
        updateBaseStats();
        FL.toast('「' + name + '」已经在本表中，请调整用量或先移除重复行', 'warn', '食材重复');
        return;
      }
      syncRow($tr, true);
      updateBaseStats();
    });

    // 用量变化即时刷新底部统计
    $body.on('input change', '.amount-input', function () {
      updateBaseStats();
    });

    $body.on('click', '#saveBtn', saveRecipe);
  }
}(window.jQuery, window.FL));
