/* ============================================================
   发起风味分析（独立页面）
   ------------------------------------------------------------
   入口（URL 参数）：
     ?recipeId=12           → 以配方 #12 为基准，自动导入到目标方案表格
     ?id=12                 → 同上（兼容配方库 / 工作台里沿用 ?id= 的老链接）
     ?fromTask=34           → 从历史任务 #34 的**目标方案快照**作为本次起点
     ?recipeId=12&fromTask=34 → 基准配方取 #12，目标方案取任务 #34 的快照
     ?fromTask 也兼容 ?taskId=
   ------------------------------------------------------------
   页面由三块面板组成：
     1) 基准配方   —— 选择基准配方（一键导入到目标方案）+「重新导入」
     2) 分析设置   —— 分析类型、目标区域、成本/健康/过敏原约束、调整说明
     3) 目标方案   —— 与基准配方的配料明细并列对比的调整结果，提交 POST /api/analysis
   ------------------------------------------------------------
   说明：本文件的配料行编辑器（buildIngredientOptions / appendItemRow /
   selectIngredient / setRowUnit / syncRow / attributeSummary / collectItems 等）
   源自 recipe-edit.js，两页共用同一套交互与样式类（.item-row / .ingredient-select /
   .amount-input / .unit-select / .attr-hint / .table-inline-input 等），
   改动其一时请同步另一处，避免两页的行编辑体验分叉。
   食材选择已抽成共享模块 js/fl-ingredient-picker.js（可搜索下拉）：
   .ingredient-select 是被隐藏的原生 select，仍然是唯一数据源；行内食材状态
   变化后由 syncRow() 调用 FL.ingredientPicker.sync($tr) 回填搜索框显示。
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

  /** 基准配方下拉的拉取条数：课设规模下一次拉全，避免用户还要翻页找配方 */
  var RECIPE_PAGE_SIZE = 50;

  var state = {
    recipeId: null,          // 当前基准配方编号
    fromTaskId: null,        // 「基于此配方再分析」的来源任务编号
    fromTask: null,          // 来源任务详情（目标方案起点）
    recipes: [],             // 当前用户的配方列表（下拉候选）
    ingredients: [],
    ingredientMap: {},
    ingredientOptionsHtml: '',
    regions: [],
    regionMap: {},
    submitting: false
  };

  FL.start({}, function () {
    // 兼容 ?recipeId= 与 ?id=；fromTask 兼容 ?taskId=
    var recipeParam = FL.query('recipeId') || FL.query('id');
    var taskParam = FL.query('fromTask') || FL.query('taskId');
    if (recipeParam) { state.recipeId = String(recipeParam); }
    if (taskParam) { state.fromTaskId = String(taskParam); }

    FL.layout({
      active: 'analysis',
      title: '发起风味分析',
      subtitle: '选一份基准配方一键导入目标方案，调整用量或替换食材后提交，系统会计算八维风味偏移并给出补偿建议',
      crumb: '研发工作区 / <strong>风味分析</strong>',
      actions: '<a class="secondary-btn" href="' + FL.ctx + '/recipes.html">返回配方库</a>',
      content: FL.loading('正在加载食材属性、区域画像与配方列表…')
    });
    bindEvents();
    init();
  });

  /* ---------------- 首次加载 ---------------- */

  function init() {
    var tasks = [
      FL.get('/api/ingredients'),
      FL.get('/api/regions'),
      FL.get('/api/recipes' + FL.qs({ page: 1, pageSize: RECIPE_PAGE_SIZE }))
    ];
    if (state.fromTaskId) {
      tasks.push(FL.get('/api/analysis/' + encodeURIComponent(state.fromTaskId)));
    }
    Promise.all(tasks).then(function (results) {
      applyIngredients(results[0]);
      applyRegions(results[1]);
      state.recipes = (results[2] && results[2].list) || [];
      state.fromTask = results[3] || null;
      if (!state.recipeId) {
        // 未指定基准配方时默认选中最近修改的一份；其余情况交给后续校验（可能已被删除）
        state.recipeId = state.recipes.length ? String(state.recipes[0].id) : null;
      }
      renderPage();
      if (state.fromTask) {
        if (state.fromTask.recipeId) { state.recipeId = String(state.fromTask.recipeId); }
        applyTaskAsStart(state.fromTask);
        return null;
      }
      // 基准配方已经选好 → 一键导入（载入该配方明细到目标方案表格）
      if (state.recipeId) {
        return importBaseline({ confirm: false, silent: true });
      }
      return null;
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

  function applyRegions(list) {
    state.regions = list || [];
    state.regionMap = {};
    state.regions.forEach(function (region) {
      state.regionMap[String(region.id)] = region;
    });
  }

  /* ---------------- 页面骨架 ---------------- */

  function renderPage() {
    var html = '<div style="display:grid;gap:18px">' +
      basePanel() +
      analysisPanel() +
      '</div>';
    $('#pageBody').html(html);
    applyRegionOptions();
    updateRegionHint();
    appendTargetRow(null);   // 至少保留一行，导入失败时页面也是可用的
    updateTargetStats();
  }

  /** 统一的卡片外壳（与配方编辑页的面板写法保持一致） */
  function panel(title, extra, body, flush) {
    return '<section class="panel">' +
      '<div class="panel-head"><h3>' + FL.escape(title) + '</h3><div>' + (extra || '') + '</div></div>' +
      (flush ? body : '<div class="panel-body">' + body + '</div>') +
      '</section>';
  }

  /* ---------------- 面板 1：基准配方（一键导入） ---------------- */

  function basePanel() {
    // 一份配方都没有：给明确引导，避免用户对着空下拉框反复点击
    if (!state.recipes.length) {
      return panel('基准配方',
        '<a class="link-btn" href="' + FL.ctx + '/recipes.html">配方库 →</a>',
        FL.empty('还没有配方', '先创建一份基准配方，再回来发起分析。') +
        '<div class="form-actions" style="justify-content:center;margin-top:0">' +
        '<a class="primary-btn" href="' + FL.ctx + '/recipe-edit.html">去新建配方</a></div>');
    }

    var options = state.recipes.map(function (recipe) {
      var count = (recipe.itemCount === null || recipe.itemCount === undefined) ? 0 : recipe.itemCount;
      var label = (recipe.name || '未命名配方') + '（' + count + ' 项配料）';
      return '<option value="' + FL.escape(recipe.id) + '">' + FL.escape(label) + '</option>';
    }).join('');

    var body =
      '<div class="notice">先选一份基准配方：选中即把该配方的配料明细<strong>一键导入</strong>到下方「目标方案」，' +
      '再按分析目标调整用量、替换食材或增删行。基准配方本身不会被修改，分析结果页会同时展示两套方案。</div>' +
      '<div class="form-grid" style="margin-top:16px">' +
      '  <div class="field span-2">' +
      '    <label for="baseRecipe">基准配方 <span class="req">*</span></label>' +
      '    <select id="baseRecipe"><option value="">请选择配方</option>' + options + '</select>' +
      '    <span class="hint" id="baseRecipeHint">' + FL.escape(baseRecipeHintText()) + '</span>' +
      '    <span class="error"></span>' +
      '  </div>' +
      '</div>' +
      '<div class="form-actions" style="justify-content:flex-start;margin-top:14px">' +
      '  <button type="button" class="secondary-btn" id="reimportBtn">重新导入</button>' +
      '  <a class="link-btn" id="editBaseLink" href="' + FL.ctx + '/recipe-edit.html">去编辑基准配方</a>' +
      '</div>';
    return panel('基准配方',
      '<a class="link-btn" href="' + FL.ctx + '/recipes.html">配方库 →</a>',
      body);
  }

  function baseRecipeHintText() {
    if (!state.recipeId) {
      return '还没有选择基准配方：请在上方下拉框中选择一份，配料明细会自动导入目标方案。';
    }
    var recipe = findRecipe(state.recipeId);
    if (!recipe) {
      return '原基准配方（编号 ' + state.recipeId + '）已不在配方库中，请重新选择一份基准配方。';
    }
    var text = '当前基准配方：' + recipe.name;
    if (recipe.versionNo) { text += ' · V' + recipe.versionNo; }
    text += ' · 每次保存配方后，重新导入即可取到最新明细';
    return text;
  }

  function findRecipe(id) {
    var value = String(id || '');
    for (var i = 0; i < state.recipes.length; i++) {
      if (String(state.recipes[i].id) === value) { return state.recipes[i]; }
    }
    return null;
  }

  /** 把下拉框、提示文案与「去编辑基准配方」链接同步到当前 state.recipeId */
  function syncBaseControls() {
    var $select = $('#baseRecipe');
    if ($select.length && state.recipeId) {
      $select.val(String(state.recipeId));
    }
    $('#baseRecipeHint').text(baseRecipeHintText());
    var $link = $('#editBaseLink');
    if ($link.length) {
      $link.attr('href', FL.ctx + '/recipe-edit.html' + (state.recipeId ? '?id=' + encodeURIComponent(state.recipeId) : ''));
    }
  }

  /**
   * 一键导入：把基准配方的配料明细载入目标方案表格。
   * @param {Object} options {confirm: 是否先二次确认, silent: 成功时是否静默（首次自动导入用）}
   */
  function importBaseline(options) {
    var opts = options || {};
    var recipeId = state.recipeId;
    if (!recipeId) {
      FL.toast('请先选择一份基准配方', 'warn', '无法导入');
      return Promise.resolve(false);
    }
    // 重新导入都会覆盖已经做过的调整，先确认再动手
    var confirmed = opts.confirm
      ? FL.confirm('目标方案会按当前基准配方的配料明细重新生成，已经做过的调整将被覆盖。确定重新导入吗？',
        { title: '重新导入基准配方', okText: '重新导入' })
      : Promise.resolve(true);
    return confirmed.then(function (ok) {
      if (!ok) { return false; }
      return FL.get('/api/recipes/' + encodeURIComponent(recipeId)).then(function (recipe) {
        fillTargetFromRecipe(recipe);
        syncBaseControls();
        if (!opts.silent) {
          FL.toast('已按「' + (recipe.name || '基准配方') + '」重新生成目标方案', 'success', '导入完成');
        }
        return true;
      })['catch'](function (err) {
        FL.toast(err.message || '基准配方载入失败，请稍后重试', 'error', '导入未完成');
        return false;
      });
    });
  }

  /** 目标方案按基准配方的配料明细重新生成（一行对一行） */
  function fillTargetFromRecipe(recipe) {
    var $targetBody = $('#targetBody');
    if (!$targetBody.length) { return; }
    var items = (recipe && recipe.items) || [];
    $targetBody.empty();
    items.forEach(function (item) {
      appendTargetRow({
        ingredientId: item.ingredientId,
        ingredientName: item.ingredientName,
        amount: item.amount,
        unit: item.unit
      });
    });
    if (!$targetBody.find('.item-row').length) { appendTargetRow(null); }
    updateTargetStats();
  }

  /**
   * 「基于此配方再分析」：把原分析任务的**目标方案快照**作为本次分析的起点。
   *
   * 关键点：目标配料取自任务的 targetSnapshot（上次提交的目标方案），而不是基准配方，
   * 因此上次做的用量调整与食材替换都会被保留；同时回填分析类型、目标区域与
   * 成本 / 健康 / 过敏原约束，便于直接继续调整后再次分析。
   *
   * @param {Object} task 原分析任务详情（GET /api/analysis/{id}）
   */
  function applyTaskAsStart(task) {
    var items = (task && task.targetSnapshot && task.targetSnapshot.items) || [];
    var $targetBody = $('#targetBody');
    if (!$targetBody.length) { return; }

    if (items.length) {
      $targetBody.empty();
      items.forEach(function (item) {
        appendTargetRow({
          ingredientId: item.ingredientId,
          ingredientName: item.ingredientName,
          amount: item.amount,
          unit: item.unit
        });
      });
      updateTargetStats();
    }

    // 回填分析设置
    if (task.goalType) { $('#goalType').val(task.goalType); }
    var constraints = task.constraints || {};
    if (constraints.costLimit !== null && constraints.costLimit !== undefined) {
      $('#costLimit').val(constraints.costLimit);
    }
    if (constraints.healthGoal) { $('#healthGoal').val(constraints.healthGoal); }
    if (constraints.allergens && constraints.allergens.length) {
      $('#allergens').val(constraints.allergens.join('、'));
    }
    if (constraints.note) { $('#adjustNote').val(constraints.note); }
    // 表单控件的值不能用 HTML 转义（否则 & 会显示成 &amp;）
    if (task.taskName) { $('#taskName').val(task.taskName + '（再分析）'); }

    if (task.regionProfileId) {
      $('#regionProfileId').val(String(task.regionProfileId));
      // 区域画像里没有这条记录时补一个占位选项，保证历史任务的区域不被静默丢弃
      if ($('#regionProfileId').val() !== String(task.regionProfileId)) {
        var region = state.regionMap[String(task.regionProfileId)];
        var label = (region && (region.regionName || region.regionCode)) ||
          ('区域 ' + task.regionProfileId + '（已停用）');
        $('#regionProfileId').append('<option value="' + FL.escape(task.regionProfileId) + '">' +
          FL.escape(label) + '</option>');
        $('#regionProfileId').val(String(task.regionProfileId));
      }
      updateRegionHint();
    }

    // 顶部提示本次目标方案的起点来自哪条历史任务
    $('#targetStartNotice').html(
      '<div class="notice" style="margin-top:12px;border-left-color:var(--blue)">' +
      '已载入任务 <strong>#' + FL.escape(String(task.id || '')) + '</strong>「' +
      FL.escape(task.taskName || '') + '」的<strong>目标方案</strong>作为本次起点（不是基准配方），' +
      '可直接继续调整用量或替换食材后再次分析；点「重新导入」可回到基准配方。</div>');
    syncBaseControls();
  }

  /* ---------------- 面板 2：分析设置 + 目标方案 ---------------- */

  function analysisPanel() {
    var body =
      '<div class="notice">目标方案默认与基准配方一致。调整用量、替换食材或删除行都算作一次「调整」，' +
      '提交后系统会按所选分析类型加载风味规则，对比两套方案的八维偏移并给出补偿建议。</div>' +
      '<div id="targetStartNotice"></div>' +
      '<div class="form-grid" style="margin-top:16px">' +
      '  <div class="field">' +
      '    <label for="taskName">任务名称</label>' +
      '    <input type="text" id="taskName" maxlength="100" autocomplete="off" placeholder="留空时按「配方名 · 分析类型」自动生成" />' +
      '    <span class="hint">便于在分析历史中检索</span>' +
      '    <span class="error"></span>' +
      '  </div>' +
      '  <div class="field">' +
      '    <label for="goalType">分析类型 <span class="req">*</span></label>' +
      '    <select id="goalType">' + goalOptions() + '</select>' +
      '    <span class="hint">决定规则引擎加载哪一组风味规则</span>' +
      '    <span class="error"></span>' +
      '  </div>' +
      '  <div class="field">' +
      '    <label for="regionProfileId">目标区域</label>' +
      '    <select id="regionProfileId"><option value="">不指定区域</option></select>' +
      '    <span class="hint" id="regionHint"></span>' +
      '    <span class="error"></span>' +
      '  </div>' +
      '  <div class="field">' +
      '    <label for="costLimit">成本上限（元/份）</label>' +
      '    <input type="number" id="costLimit" min="0" step="0.01" placeholder="留空表示不限制" />' +
      '    <span class="hint">演示口径，用于在建议中标注超限风险</span>' +
      '    <span class="error"></span>' +
      '  </div>' +
      '  <div class="field">' +
      '    <label for="healthGoal">健康化目标</label>' +
      '    <select id="healthGoal">' +
      '      <option value="NONE">不限制</option>' +
      '      <option value="LOW_SUGAR">减糖</option>' +
      '      <option value="LOW_FAT">减脂</option>' +
      '      <option value="LOW_SODIUM">减钠</option>' +
      '    </select>' +
      '    <span class="hint">与「控糖调整 / 控脂调整」分析类型配合使用</span>' +
      '    <span class="error"></span>' +
      '  </div>' +
      '  <div class="field">' +
      '    <label for="allergens">过敏原 / 禁用原料</label>' +
      '    <input type="text" id="allergens" maxlength="200" autocomplete="off" placeholder="多个用逗号分隔，例如：花生, 芝麻" />' +
      '    <span class="hint">与建议中的原料名称做包含匹配，命中的替换方案会被过滤</span>' +
      '    <span class="error"></span>' +
      '  </div>' +
      '  <div class="field span-2">' +
      '    <label for="adjustNote">调整说明</label>' +
      '    <textarea id="adjustNote" maxlength="500" placeholder="例如：用赤藓糖醇替换部分白砂糖，希望保留焦糖香气"></textarea>' +
      '    <span class="hint">会随任务一起保存，结果页与历史记录中都会展示</span>' +
      '    <span class="error"></span>' +
      '  </div>' +
      '</div>' +
      '<div class="table-wrap" style="margin-top:18px"><table>' +
      '<thead><tr>' +
      '<th>目标食材（含八维风味属性摘要）</th>' +
      '<th style="width:140px">用量</th>' +
      '<th style="width:110px">单位</th>' +
      '<th style="width:90px">操作</th>' +
      '</tr></thead>' +
      '<tbody id="targetBody"></tbody>' +
      '</table></div>' +
      '<div id="submitErrorHost"></div>' +
      '<div class="form-actions" style="justify-content:space-between;margin-top:16px;gap:12px;flex-wrap:wrap">' +
      '  <span class="row-actions">' +
      '    <button type="button" class="secondary-btn" id="addTargetBtn">+ 添加目标配料</button>' +
      '  </span>' +
      '  <span class="row-actions">' +
      '    <span id="targetStats" style="color:var(--muted);font-size:12px;line-height:42px"></span>' +
      '    <button type="button" class="primary-btn" id="submitBtn">提交分析</button>' +
      '  </span>' +
      '</div>' +
      '<div class="notice" style="margin-top:16px">本页与结果页中的风味数值均为课设演示用相对评分（0-100），' +
      '用于打样前的方向性参考，不等同于实验室检测或感官评价结论。</div>';
    return panel('分析设置与目标方案',
      '<a class="link-btn" href="' + FL.ctx + '/analysis-history.html">分析历史 →</a>',
      body);
  }

  function goalOptions() {
    return FL.GOALS.map(function (goal) {
      return '<option value="' + FL.escape(goal.value) + '">' + FL.escape(goal.text) + '</option>';
    }).join('');
  }

  function applyRegionOptions() {
    var $select = $('#regionProfileId');
    if (!$select.length) { return; }
    state.regions.forEach(function (region) {
      var label = region.regionName || region.regionCode || ('区域 ' + region.id);
      $select.append('<option value="' + FL.escape(region.id) + '">' + FL.escape(label) + '</option>');
    });
  }

  function updateRegionHint() {
    var id = $.trim($('#regionProfileId').val() || '');
    var region = id ? state.regionMap[id] : null;
    $('#regionHint').text(region
      ? (region.description || '按「' + (region.regionName || '该区域') + '」的维度偏好权重加权计算。')
      : '不指定区域时按全国基准（权重 1.000）计算，不叠加区域偏好。');
  }

  /* ---------------- 目标方案配料行 ---------------- */

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
   * 追加一行目标配料。
   * @param {Object} item 已有明细（可空，为空时给一行默认值）
   */
  function appendTargetRow(item) {
    var $tbody = $('#targetBody');
    if (!$tbody.length) { return $(); }
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
   * @param {jQuery} $tr             行
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

  /** 目标方案表格内是否已存在该食材 */
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
   * 校验并收集目标方案的配料。
   * @return {{items: Array, error: String, $tr: jQuery}} 校验失败时 error 有值，$tr 为出错行
   */
  function collectTargetItems() {
    var items = [];
    var seen = {};
    var error = null;
    var $errorRow = null;
    $('#targetBody').find('.item-row').each(function () {
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

  function updateTargetStats() {
    var stats = collectStats($('#targetBody'));
    var text = '目标方案 ' + stats.count + ' 项 · 总用量 ' + FL.fmtNumber(stats.total, 2);
    if (stats.missing > 0) {
      text += ' · 其中 ' + stats.missing + ' 项食材已不在启用食材库中，分析时不会参与加权';
    }
    $('#targetStats').text(text);
  }

  /* ---------------- 提交分析 ---------------- */

  function submitAnalysis() {
    if (state.submitting) { return; }
    if (!state.recipeId) {
      FL.toast('请先在上方选择一份基准配方，再提交分析', 'warn', '无法分析');
      return;
    }
    var valid = FL.validate($('#pageBody'), [
      { name: '#taskName', label: '任务名称', max: 100 },
      { name: '#costLimit', label: '成本上限', pattern: /^\d+(\.\d{1,2})?$/, message: '成本上限只能填写非负数字，最多两位小数' },
      { name: '#allergens', label: '过敏原 / 禁用原料', max: 200 },
      { name: '#adjustNote', label: '调整说明', max: 500 }
    ]);
    if (!valid) { return; }

    var target = collectTargetItems();
    if (target.error) {
      FL.toast(target.error, 'error', '目标方案不完整');
      focusRow(target.$tr);
      return;
    }
    if (!target.items.length) {
      FL.toast('目标方案至少需要一项配料', 'error', '无法提交');
      return;
    }

    var regionId = $.trim($('#regionProfileId').val() || '');
    var costText = $.trim($('#costLimit').val() || '');
    var note = $.trim($('#adjustNote').val() || '');
    var payload = {
      taskName: $.trim($('#taskName').val() || '') || null,
      recipeId: Number(state.recipeId),
      goalType: $('#goalType').val() || 'GENERAL',
      regionProfileId: regionId ? Number(regionId) : null,
      targetItems: target.items,
      constraints: {
        costLimit: costText === '' ? null : Number(costText),
        healthGoal: $('#healthGoal').val() || 'NONE',
        allergens: splitList($('#allergens').val()),
        // 后端把「本次调整说明」与「约束补充说明」分开存储，这里用同一段文本填充两处，
        // 保证结果页、历史记录、约束 JSON 三处都能看到用户的调整意图
        note: note || null
      },
      remark: note || null
    };

    var $btn = $('#submitBtn');
    state.submitting = true;
    $btn.prop('disabled', true).text('分析中…').css('opacity', '.6');
    FL.post('/api/analysis', payload).then(function (task) {
      FL.toast('分析完成，正在打开结果页…', 'success', '提交成功');
      location.href = FL.ctx + '/analysis-result.html?taskId=' + encodeURIComponent(task && task.id ? task.id : '');
    })['catch'](function (err) {
      state.submitting = false;
      $btn.prop('disabled', false).text('提交分析').css('opacity', '');
      showSubmitError(err.message || '分析失败，请稍后重试');
    });
  }

  /**
   * 提交被驳回时就地提示，并把提示滚动到视野内。
   *
   * <p>为什么除了右下角 toast 还要就地提示：toast 距离提交按钮很远，而用户点完按钮后
   * 视线仍停在按钮附近，很容易整场都没注意到（实测出现过连续点击 8 次的情况）。
   * 驳回类错误必须落在「刚刚点击的那个位置」旁边，否则等于没有反馈。</p>
   *
   * @param {String} message 错误信息
   */
  function showSubmitError(message) {
    FL.toast(message, 'error', '提交未完成');
    var $host = $('#submitErrorHost');
    if (!$host.length) { return; }
    $host.html('<div class="notice" style="margin-bottom:14px;border-left-color:#c2503a;color:#8f3a28">' +
      FL.escape(message) + '</div>');
    if ($host[0].scrollIntoView) {
      $host[0].scrollIntoView({ block: 'center', behavior: 'smooth' });
    }
  }

  /** 过敏原等多值输入：支持中英文逗号、顿号、分号与空格分隔 */
  function splitList(text) {
    return String(text || '')
      .split(/[,，、;；\s]+/)
      .map(function (part) { return $.trim(part); })
      .filter(function (part) { return part.length > 0; });
  }

  /* ---------------- 事件绑定（委托到 #pageBody，面板重建后依然生效） ---------------- */

  function bindEvents() {
    var $body = $('#pageBody');

    $body.on('click', '#retryBtn', function () { init(); });

    // 一键导入：选中基准配方即把该配方的配料明细载入目标方案
    $body.on('change', '#baseRecipe', function () {
      var value = $.trim($(this).val() || '');
      if (!value) {
        state.recipeId = null;
        syncBaseControls();
        return;
      }
      var previous = state.recipeId;
      state.recipeId = value;
      syncBaseControls();
      // 已经调整过目标方案时先确认，避免误切基准配方丢失调整
      var hasTarget = $('#targetBody .item-row').length > 0;
      if (!previous || !hasTarget) {
        importBaseline({ confirm: false, silent: false });
        return;
      }
      FL.confirm('切换基准配方会按新配方的配料明细重新生成目标方案，已经做过的调整将被覆盖。确定切换吗？',
        { title: '切换基准配方', okText: '切换并导入' }).then(function (ok) {
        if (ok) {
          importBaseline({ confirm: false, silent: false });
          return;
        }
        state.recipeId = previous;   // 取消切换：把下拉框退回原来的基准配方
        syncBaseControls();
      });
    });

    // 重新导入：等价于原来的「从基准配方重置」
    $body.on('click', '#reimportBtn', function () {
      importBaseline({ confirm: true, silent: false });
    });

    $body.on('click', '#addTargetBtn', function () {
      appendTargetRow(null);
      updateTargetStats();
    });

    // 移除目标配料行：至少保留一行
    $body.on('click', '#targetBody .remove-btn', function () {
      var $tr = $(this).closest('.item-row');
      var $tbody = $tr.closest('tbody');
      if ($tbody.find('.item-row').length <= 1) {
        FL.toast('目标方案至少保留一项配料', 'warn', '无法移除');
        return;
      }
      $tr.remove();
      updateTargetStats();
    });

    // 切换食材：重复食材立即阻止，正常切换时同步名称、属性摘要与默认单位
    $body.on('change', '#targetBody .ingredient-select', function () {
      var $select = $(this);
      var $tr = $select.closest('.item-row');
      var $tbody = $tr.closest('tbody');
      var id = $.trim($select.val() || '');
      var name = id ? ($select.find('option:selected').attr('data-name') || '') : '';
      if (id && isDuplicate($tbody, $tr, id)) {
        $select.val($select.data('prevValue') || '');
        syncRow($tr, false);
        updateTargetStats();
        FL.toast('「' + name + '」已经在本表中，请调整用量或先移除重复行', 'warn', '食材重复');
        return;
      }
      syncRow($tr, true);
      updateTargetStats();
    });

    // 用量变化即时刷新底部统计
    $body.on('input change', '#targetBody .amount-input', function () {
      updateTargetStats();
    });

    $body.on('change', '#regionProfileId', updateRegionHint);
    $body.on('click', '#submitBtn', submitAnalysis);

    // 用户一改目标方案，就把上一次的驳回提示清掉，避免过期提示一直挂在页面上
    $body.on('input change click', '#targetBody', function () {
      $('#submitErrorHost').empty();
    });
  }
}(window.jQuery, window.FL));
