/* ============================================================
   配方生成：给出目标，直接拿到一份可执行的新配方
   ------------------------------------------------------------
   数据来源：
     GET  /api/recipes?page=1&pageSize=50   —— 基准配方候选
     GET  /api/regions                      —— 目标区域候选
     GET  /api/recipes/{id}/compose?…       —— 合成（只算不存）
     POST /api/recipes                      —— 「采用为新配方」时落库
   ------------------------------------------------------------
   本页是产品的核心入口：它的交付物是「一张配料表」，不是一份分析报告。
   八维数据与调整依据都只是支撑这份配方的依据，因此排在配方之后。
   ============================================================ */
(function ($, FL) {
  'use strict';

  var state = {
    recipes: [],
    regions: [],
    preselect: '',     // 从 URL 带入的基准配方编号
    source: null,      // 本次合成用的基准配方详情
    result: null,      // 合成结果
    generating: false
  };

  FL.start({}, function () {
    FL.layout({
      active: 'generate',
      title: '配方生成',
      subtitle: '选定基准配方与研发目标，系统直接生成一份可执行的新配方（含完整配料表与用量）',
      crumb: '研发工作区 / <strong>配方生成</strong>',
      actions: '<a class="secondary-btn" href="' + FL.ctx + '/recipes.html">返回配方库</a>',
      content: FL.loading('正在加载配方与区域…')
    });
    // 支持从配方库行内「生成配方」带 ?recipeId= 进来时预选该配方
    state.preselect = FL.query('recipeId') || '';
    bindEvents();
    init();
  });

  /* ---------------- 初始化 ---------------- */

  function init() {
    Promise.all([
      FL.get('/api/recipes?page=1&pageSize=50'),
      FL.get('/api/regions')['catch'](function () { return []; })
    ]).then(function (results) {
      state.recipes = (results[0] && results[0].list) || [];
      state.regions = results[1] || [];
      renderSettings();
    })['catch'](function (err) {
      $('#pageBody').html(FL.empty('加载失败', err.message || '请稍后重试') +
        '<div class="form-actions" style="justify-content:center;margin-top:0">' +
        '<button type="button" class="secondary-btn" id="retryBtn">重新加载</button></div>');
    });
  }

  function bindEvents() {
    var $body = $('#pageBody');
    $body.on('click', '#retryBtn', function () { init(); });
    $body.on('click', '#generateBtn', generate);
    $body.on('click', '#adoptBtn', openAdoptDialog);
    $body.on('click', '#adoptConfirmBtn', submitAdopt);
    // 一改设置就把上一次的生成结果收起，避免误以为结果属于新设置
    $body.on('change', '#baseRecipe, #goalType, #regionProfileId', function () {
      if (state.result) { state.result = null; renderSettings(); }
    });
  }

  /* ---------------- 面板 1：生成设置 ---------------- */

  function renderSettings() {
    var tips = FL.empty('还没有可用的基准配方', '先到「配方库」录入一份配方，再回到这里生成新配方。');
    var body =
      '<div class="notice">给系统三样东西：<strong>一份基准配方、一个研发目标、若干约束</strong>，' +
      '它就会直接产出一份符合目标的新配方。八维数据与调整依据只是支撑这份配方的依据。</div>' +
      '<div class="form-grid" style="margin-top:16px">' +
      '  <div class="field">' +
      '    <label for="baseRecipe">基准配方 <span class="req">*</span></label>' +
      '    <select id="baseRecipe">' + recipeOptions() + '</select>' +
      '    <span class="hint">要以哪一份配方为起点改造</span>' +
      '  </div>' +
      '  <div class="field">' +
      '    <label for="goalType">研发目标 <span class="req">*</span></label>' +
      '    <select id="goalType">' + goalOptions() + '</select>' +
      '    <span class="hint">决定目标向量：哪些维度要保持、哪些要定向调整</span>' +
      '  </div>' +
      '  <div class="field">' +
      '    <label for="regionProfileId">目标区域</label>' +
      '    <select id="regionProfileId"><option value="">不指定区域</option></select>' +
      '    <span class="hint">选择后八维目标值按该区域权重折算</span>' +
      '  </div>' +
      '  <div class="field">' +
      '    <label for="allergens">过敏原 / 禁用原料</label>' +
      '    <input type="text" id="allergens" maxlength="200" autocomplete="off" placeholder="多个用逗号分隔，例如：大豆, 芝麻" />' +
      '    <span class="hint">命中的候选会被排除在配方之外</span>' +
      '  </div>' +
      '</div>' +
      '<div id="generateErrorHost"></div>' +
      '<div class="form-actions" style="margin-top:16px">' +
      '  <button type="button" class="primary-btn" id="generateBtn">生成配方</button>' +
      '</div>';
    $('#pageBody').html('<div style="display:grid;gap:18px">' +
      panel('生成设置', '<span class="tag">目标 → 配方</span>', state.recipes.length ? body : tips) +
      '<div id="resultHost"></div>' +
      '</div>');
    applyRegionOptions();
    if (state.preselect) {
      var $select = $('#baseRecipe');
      // 只在候选中确实存在时才选中，避免被手工改过的 URL 带出无效值
      if ($select.find('option[value="' + state.preselect + '"]').length) {
        $select.val(state.preselect);
      }
    }
  }

  function recipeOptions() {
    if (!state.recipes.length) { return '<option value="">（暂无配方）</option>'; }
    return state.recipes.map(function (recipe) {
      return '<option value="' + FL.escape(recipe.id) + '">' + FL.escape(recipe.name) + '</option>';
    }).join('');
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

  /* ---------------- 生成 ---------------- */

  function generate() {
    if (state.generating) { return; }
    var recipeId = $('#baseRecipe').val();
    var goalType = $('#goalType').val();
    if (!recipeId) { showGenerateError('请先选择一份基准配方'); return; }
    if (!goalType) { showGenerateError('请先选择研发目标'); return; }

    clearGenerateError();
    state.generating = true;
    var $btn = $('#generateBtn').prop('disabled', true).text('生成中…').css('opacity', '.6');
    $('#resultHost').html(FL.loading('正在合成配方…'));

    var query = FL.qs({
      goalType: goalType,
      regionProfileId: $('#regionProfileId').val() || null,
      allergens: $.trim($('#allergens').val() || '') || null
    });
    // 基准配方详情与合成结果一起取：采用为新配方时需要原配方的主体信息
    Promise.all([
      FL.get('/api/recipes/' + encodeURIComponent(recipeId)),
      FL.get('/api/recipes/' + encodeURIComponent(recipeId) + '/compose' + query)
    ]).then(function (results) {
      state.generating = false;
      $btn.prop('disabled', false).text('生成配方').css('opacity', '');
      state.source = results[0];
      state.result = results[1];
      renderResult();
    })['catch'](function (err) {
      state.generating = false;
      $btn.prop('disabled', false).text('生成配方').css('opacity', '');
      $('#resultHost').empty();
      showGenerateError(err.message || '配方生成失败，请稍后重试');
    });
  }

  /**
   * 驳回类错误就地提示：右下角 toast 离按钮太远，实测用户会整场都没注意到。
   */
  function showGenerateError(message) {
    FL.toast(message, 'error', '生成未完成');
    var $host = $('#generateErrorHost');
    if (!$host.length) { return; }
    $host.html('<div class="notice" style="margin-top:14px;border-left-color:#c2503a;color:#8f3a28">' +
      FL.escape(message) + '</div>');
    if ($host[0].scrollIntoView) { $host[0].scrollIntoView({ block: 'center', behavior: 'smooth' }); }
  }

  function clearGenerateError() {
    $('#generateErrorHost').empty();
  }

  /* ---------------- 结果：新配方 + 依据 + 达标 ---------------- */

  function renderResult() {
    var data = state.result;
    if (!data) { return; }
    $('#resultHost').html('<div style="display:grid;gap:18px">' +
      recipePanel(data) + adjustmentPanel(data) + checkPanel(data) + warningPanel(data) +
      '</div>');
  }

  function recipePanel(data) {
    var rows = (data.items || []).map(function (item) {
      var note = '';
      if (item.added) {
        note = FL.statusTag('新增', '') + ' ' + FL.escape(item.knowledgeCode || '');
      } else if (item.changed) {
        note = FL.statusTag('由 ' + FL.fmtNumber(item.baseAmount, 3) + ' 调整', 'warn');
      }
      return '<tr>' +
        '<td><strong>' + FL.escape(item.ingredientName) + '</strong>' +
        (item.category ? '<br /><span style="color:var(--muted)">' + FL.escape(item.category) + '</span>' : '') +
        '</td>' +
        '<td>' + FL.escape(FL.fmtNumber(item.amount, 3)) + '</td>' +
        '<td>' + FL.escape(item.unit || '') + '</td>' +
        '<td>' + FL.escape(FL.fmtNumber(item.share, 2)) + '%</td>' +
        '<td>' + note + '</td>' +
        '</tr>';
    }).join('');

    var head = '<button type="button" class="primary-btn" id="adoptBtn">采用为新配方</button>';
    var body =
      '<div class="notice" style="margin-bottom:14px">这是依据「' +
      FL.escape(goalTextOf(data.goalType)) + '」目标为你的基准配方生成的新配方，' +
      '<strong>可直接用于打样</strong>。每一处用量都注明了依据的知识卡。</div>' +
      '<div class="table-wrap"><table>' +
      '<thead><tr><th>食材</th><th style="width:110px">用量</th><th style="width:80px">单位</th>' +
      '<th style="width:90px">占比</th><th style="width:190px">说明</th></tr></thead>' +
      '<tbody>' + rows + '</tbody></table></div>';
    return panel('生成的新配方', head, body);
  }

  function adjustmentPanel(data) {
    var list = data.adjustments || [];
    if (!list.length) {
      return panel('调整依据', '', '<div class="notice">本次没有产生任何调整，输出配方与基准一致。原因见下方提示。</div>');
    }
    var cards = list.map(function (item) {
      var code = item.knowledgeCode
        ? '<span class="tag">' + FL.escape(item.knowledgeCode) + '</span> ' + FL.escape(item.knowledgeTitle || '')
        : FL.statusTag('目标模板', 'muted');
      return '<div class="notice" style="margin-bottom:12px">' +
        '<div style="margin-bottom:6px">' + code + '</div>' +
        '<div><strong>' + FL.escape(item.candidate || '') + '</strong> · ' +
        FL.escape(actionText(item.actionType)) +
        (item.amount !== null && item.amount !== undefined
          ? ' · 用量 <strong>' + FL.escape(FL.fmtNumber(item.amount, 3)) + ' ' + FL.escape(item.unit || '') + '</strong>' : '') +
        (item.referenceRange ? '　<span style="color:var(--muted)">' + FL.escape(item.referenceRange) + '</span>' : '') +
        '</div>' +
        '<div style="margin-top:6px;color:var(--muted)">' + FL.escape(item.reason || '') + '</div>' +
        (item.risk ? '<div style="margin-top:4px;color:var(--muted)">风险：' + FL.escape(item.risk) + '</div>' : '') +
        (item.evidenceSource ? '<div style="margin-top:4px;color:var(--muted)">依据：' + FL.escape(item.evidenceSource) + '</div>' : '') +
        '</div>';
    }).join('');
    return panel('调整依据', '<span class="tag">共 ' + list.length + ' 处</span>' +
      '<span style="color:var(--muted);font-size:12px;margin-left:8px">每处都能追溯到具体知识卡</span>', cards);
  }

  function checkPanel(data) {
    var rows = (data.checks || []).map(function (item) {
      var state = item.intent === 'ALLOW'
        ? FL.statusTag('放行', 'muted')
        : (item.satisfied ? FL.statusTag('达标', '') : FL.statusTag('未达标', 'warn'));
      var gap = (item.gap === null || item.gap === undefined) ? '—'
        : (item.gap > 0 ? '+' : '') + FL.fmtNumber(item.gap, 2);
      return '<tr><td><strong>' + FL.escape(item.label) + '</strong></td>' +
        '<td>' + FL.escape(intentText(item.intent)) + '</td>' +
        '<td>' + FL.escape(FL.fmtNumber(item.baseline, 2)) + '</td>' +
        '<td>' + FL.escape(FL.fmtNumber(item.target, 2)) + '</td>' +
        '<td>' + FL.escape(FL.fmtNumber(item.predicted, 2)) + '</td>' +
        '<td>' + FL.escape(gap) + '</td>' +
        '<td>' + state + '</td></tr>';
    }).join('');
    return panel('调整后的预测', '<span class="tag">依据</span>',
      '<div class="notice" style="margin-bottom:14px">把生成的新配方重新代入风味引擎后的预测结果。' +
      '它是判断"改到位了没有"的依据，不是本页的主要产出。</div>' +
      '<div class="table-wrap"><table>' +
      '<thead><tr><th>维度</th><th>意图</th><th>基准</th><th>目标</th><th>预测</th><th>偏差</th><th>判定</th></tr></thead>' +
      '<tbody>' + rows + '</tbody></table></div>');
  }

  function warningPanel(data) {
    var list = data.warnings || [];
    if (!list.length) { return ''; }
    return panel('需要你知道的事', '<span class="tag">' + list.length + ' 条</span>',
      list.map(function (text) {
        return '<div class="notice" style="margin-bottom:10px">' + FL.escape(text) + '</div>';
      }).join(''));
  }

  /* ---------------- 采用为新配方 ---------------- */

  function openAdoptDialog() {
    if (!state.result) { return; }
    var defaultName = (state.source && state.source.name ? state.source.name : '配方') +
      ' · ' + goalTextOf(state.result.goalType) + '版';
    var modalId = 'adoptModal';
    $('#' + modalId).remove();
    var html =
      '<div class="modal fade" id="' + modalId + '" tabindex="-1">' +
      '  <div class="modal-dialog modal-dialog-centered">' +
      '    <div class="modal-content" style="border-radius:12px;border:1px solid var(--line)">' +
      '      <div class="modal-header" style="border-bottom:1px solid var(--line)">' +
      '        <h5 class="modal-title" style="font-size:15px">采用为新配方</h5>' +
      '        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="关闭"></button>' +
      '      </div>' +
      '      <div class="modal-body">' +
      '        <div class="notice" style="margin-bottom:14px">会按当前生成结果新建一份配方存入配方库。' +
      '原基准配方不受影响。</div>' +
      '        <div class="field">' +
      '          <label for="adoptName">新配方名称</label>' +
      '          <input type="text" id="adoptName" maxlength="100" value="' + FL.escape(defaultName) + '" />' +
      '          <span class="error"></span>' +
      '        </div>' +
      '        <div id="adoptErrorHost"></div>' +
      '      </div>' +
      '      <div class="modal-footer" style="border-top:1px solid var(--line)">' +
      '        <button type="button" class="secondary-btn" data-bs-dismiss="modal">取消</button>' +
      '        <button type="button" class="primary-btn" id="adoptConfirmBtn">保存到配方库</button>' +
      '      </div>' +
      '    </div>' +
      '  </div>' +
      '</div>';
    var $el = $(html).appendTo('body');
    var modal = new window.bootstrap.Modal($el[0]);
    $el.on('hidden.bs.modal', function () { $el.remove(); });
    modal.show();
  }

  function submitAdopt() {
    if (!state.result || !state.source) { return; }
    var name = $.trim($('#adoptName').val() || '');
    if (!name) {
      $('#adoptErrorHost').html('<div class="notice" style="border-left-color:#c2503a;color:#8f3a28">请填写配方名称</div>');
      return;
    }
    var $btn = $('#adoptConfirmBtn');
    $btn.prop('disabled', true).text('保存中…').css('opacity', '.6');
    var payload = {
      name: name,
      productType: state.source.productType || null,
      processNote: state.source.processNote || null,
      remark: '由「配方生成」产生；基准配方：' + (state.source.name || '') +
        '；研发目标：' + goalTextOf(state.result.goalType),
      items: (state.result.items || []).map(function (item) {
        return {
          ingredientId: item.ingredientId,
          ingredientName: item.ingredientName,
          amount: item.amount,
          unit: item.unit
        };
      })
    };
    FL.post('/api/recipes', payload).then(function () {
      $btn.prop('disabled', false).text('保存到配方库').css('opacity', '');
      $('#adoptModal').modal('hide');
      FL.success('已存入配方库，可在配方库中继续编辑或发起分析', {
        title: '已采用为新配方', redirect: '/recipes.html'
      });
    })['catch'](function (err) {
      $btn.prop('disabled', false).text('保存到配方库').css('opacity', '');
      $('#adoptErrorHost').html('<div class="notice" style="border-left-color:#c2503a;color:#8f3a28">' +
        FL.escape(err.message || '保存失败，请稍后重试') + '</div>');
    });
  }

  /* ---------------- 文案 ---------------- */

  function goalTextOf(value) {
    var found = null;
    (FL.GOALS || []).forEach(function (goal) {
      if (goal.value === value) { found = goal.text; }
    });
    return found || value || '';
  }

  function intentText(intent) {
    if (intent === 'KEEP') { return '保持'; }
    if (intent === 'CHANGE') { return '定向调整'; }
    if (intent === 'ALLOW') { return '放行'; }
    return intent || '';
  }

  /**
   * 知识卡的动作方向转中文。
   *
   * <p>注意 {@code REPLACE} 在合成中按「补入该维度的替代来源」处理，
   * 因此这里说"补入"而不是"替换" —— 说"替换"会让用户以为系统把某样原料换掉了，
   * 而实际上"减掉什么"是由目标模板的成分层动作负责的。</p>
   */
  function actionText(action) {
    if (action === 'ADD') { return '补入'; }
    if (action === 'REPLACE') { return '补入替代来源'; }
    if (action === 'REDUCE') { return '减量'; }
    if (action === 'NOTICE') { return '提示'; }
    return action || '';
  }

  /** 统一的面板外壳 */
  function panel(title, aside, body) {
    return '<section class="panel">' +
      '<div class="panel-head"><h3>' + title + '</h3>' + (aside || '') + '</div>' +
      '<div class="panel-body">' + body + '</div>' +
      '</section>';
  }
}(window.jQuery, window.FL));
