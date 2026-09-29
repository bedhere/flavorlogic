/* ============================================================
   分析结果页：八维雷达、偏移明细、补偿建议、快照对比与试产反馈
   ------------------------------------------------------------
   数据来源：GET /api/analysis/{id}（?taskId= 传入任务编号）
   说明：八维维度编码 sweet/salty/sour/bitter/umami/spicy/numbing/fat_aroma
        由后端 FlavorDimensions 固定，前端展示顺序不可调整。
   ============================================================ */
(function ($, FL) {
  'use strict';

  /** 八维固定顺序：甜 / 咸 / 酸 / 苦 / 鲜 / 辣 / 麻 / 脂香 */
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

  /** 偏移状态 → 标签样式（DOWN/STABLE/UP 用默认或 muted，OUT_OF_RANGE 用 warn） */
  var DELTA_STATUS = {
    DOWN: { text: '下降', cls: '' },
    STABLE: { text: '稳定', cls: 'muted' },
    UP: { text: '上升', cls: '' },
    OUT_OF_RANGE: { text: '超出建议范围', cls: 'warn' }
  };

  /** 与设计系统一致的图表配色 */
  var BASELINE_COLOR = '#157a56';
  var TARGET_COLOR = '#527ee9';

  /**
   * 基准值低于该阈值时，相对变化比例会被放大而无参考意义。
   * 0-100 量表上，低于 5 基本等于「尝不出来」：例如基准 1.58 → 目标 10.95 会算出 +593%，
   * 但语义只是「从几乎不甜变成有点甜」。这类维度只在表格里给出差值，比例显示为「基准极低」。
   */
  var LOW_BASELINE = 5;

  var taskId = FL.query('taskId');
  /** 当前任务详情，供雷达重绘与反馈提交复用 */
  var task = null;
  /** 雷达图显示模式：profile = 只画目标方案轮廓（默认，最易读）；compare = 叠加基准对比 */
  var radarMode = 'profile';
  /** 雷达图绘制参数，窗口缩放时按新宽度重绘 */
  var radarOptions = null;
  var resizeTimer = null;

  FL.start({}, function () {
    FL.layout({
      active: 'history',
      title: '分析结果',
      subtitle: '对比基准配方与目标方案的八维风味偏移，查看补偿建议与试产反馈',
      crumb: '研发工作区 / 分析历史 / <strong>分析结果</strong>',
      actions: '<a class="secondary-btn" href="' + FL.ctx + '/analysis-history.html">返回分析历史</a>' +
        '<a class="primary-btn" href="' + FL.ctx + '/analysis-run.html">发起风味分析</a>',
      content: FL.loading('正在加载分析结果…')
    });

    if (!taskId) {
      $('#pageBody').html(FL.empty('缺少任务编号', '请从分析历史进入。'));
      return;
    }
    bindEvents();
    load();
  });

  /* ---------------- 数据加载 ---------------- */

  function load() {
    FL.get('/api/analysis/' + encodeURIComponent(taskId)).then(function (data) {
      task = data || {};
      render();
    })['catch'](function (err) {
      $('#pageBody').html(FL.empty('分析结果加载失败', err.message || '请稍后重试，或返回分析历史重新进入。'));
    });
  }

  function render() {
    var blocks = [
      hero(task),
      notices(task),
      gauges(task),
      scorePanel(task),
      radarPanel(task),
      diffPanel(task),
      deltaPanel(task),
      suggestionPanel(task),
      snapshotPanel(task),
      explainPanel(task),
      feedbackPanel(task)
    ].filter(function (block) { return !!block; });
    $('#pageBody').html(blocks.join(''));
    // DOM 已插入再进行绘制，保证 canvas 容器有可用宽度
    bindRadarToggle();
    drawRadar();
    drawDiffBars();
  }

  /* ---------------- A. 顶部信息区 ---------------- */

  function hero(data) {
    var status = FL.TASK_STATUS[data.status] || { text: data.status || '未知', cls: 'muted' };
    var tags = FL.statusTag(status.text, status.cls) +
      '<span class="tag">' + FL.escape(FL.goalText(data.goalType)) + '</span>';
    if (data.recipeName) {
      tags += '<span class="tag">基准配方：' + FL.escape(data.recipeName) + '</span>';
    }
    if (data.targetRegion) {
      tags += '<span class="tag">目标区域：' + FL.escape(data.targetRegion) + '</span>';
    }
    tags += '<span class="tag">创建于 ' + FL.escape(FL.fmtDate(data.createdAt)) + '</span>';
    if (data.finishedAt) {
      tags += '<span class="tag">完成于 ' + FL.escape(FL.fmtDate(data.finishedAt)) + '</span>';
    }

    var actions = '<button class="secondary-btn" type="button" id="printBtn">打印结果</button>' +
      '<a class="secondary-btn" href="' + FL.ctx + '/analysis-history.html">返回历史</a>';
    if (data.recipeId) {
      // 带上 fromTask：分析页会载入该任务的「目标方案快照」作为新分析起点，
      // 这样上次做的用量调整与食材替换不会丢失
      var fromTask = data.id ? '&fromTask=' + encodeURIComponent(data.id) : '';
      actions += '<a class="primary-btn" href="' + FL.ctx + '/analysis-run.html?recipeId=' +
        encodeURIComponent(data.recipeId) + fromTask + '">基于此配方再分析</a>';
    }

    return '<div class="result-hero">' +
      '<div>' +
      '<h2>' + FL.escape(data.taskName || '未命名分析任务') + '</h2>' +
      '<div style="display:flex;flex-wrap:wrap;align-items:center;gap:8px">' + tags + '</div>' +
      '<p class="summary" style="margin-top:10px">' + FL.escape(summaryText(data)) + '</p>' +
      '</div>' +
      '<div style="display:flex;flex-wrap:wrap;gap:9px" class="no-print">' + actions + '</div>' +
      '</div>';
  }

  /** 摘要文案：优先结果摘要，无结果时退化为任务状态说明 */
  function summaryText(data) {
    var result = data.result;
    if (result && result.summary) { return result.summary; }
    if (data.summary) { return data.summary; }
    if (data.status === 'FAILED') { return '本次分析执行失败，未生成风味结果。'; }
    if (data.status === 'RUNNING') { return '分析正在执行中，完成后可查看八维偏移与补偿建议。'; }
    if (data.status === 'PENDING') { return '分析任务已创建，等待执行。'; }
    if (data.status === 'COMPLETED') { return '分析已完成，但未生成结果摘要。'; }
    return '暂无摘要信息。';
  }

  /** 失败原因与降级提示 */
  function notices(data) {
    if (data.status === 'FAILED') {
      return '<div class="notice" style="margin-top:18px">分析失败原因：' +
        FL.escape(data.errorMessage || '任务执行过程中发生异常，未生成风味结果。') +
        '　可返回分析历史重新发起分析。</div>';
    }
    if (data.status === 'COMPLETED' && !data.result) {
      return '<div class="notice" style="margin-top:18px">该任务已标记为完成，但未查询到分析结果数据。' +
        '可能是结果记录缺失，建议返回分析历史重新发起一次分析。</div>';
    }
    if (data.status === 'PENDING' || data.status === 'RUNNING') {
      return '<div class="notice" style="margin-top:18px">分析任务尚未完成，本页暂时没有结果数据，' +
        '请稍后从分析历史重新进入查看。</div>';
    }
    return '';
  }

  /* ---------------- B. 指标卡 ---------------- */

  function gauges(data) {
    var result = data.result || null;
    var baseline = result && result.baseline ? result.baseline : null;
    var target = result && result.target ? result.target : null;

    var confidence = result && result.confidence !== null && result.confidence !== undefined
      ? result.confidence : data.confidence;
    var completeness = result ? result.dataCompleteness : null;

    var baselineCount = baseline ? baseline.itemCount : null;
    var targetCount = target ? target.itemCount : null;
    var count = targetCount !== null && targetCount !== undefined ? targetCount : baselineCount;

    var countHint = (baselineCount === null || baselineCount === undefined) &&
      (targetCount === null || targetCount === undefined)
      ? '分析完成后可查看参与计算的配料数'
      : '基准 ' + numberText(baselineCount) + ' 项 / 目标 ' + numberText(targetCount) + ' 项';

    return '<div class="gauge-row" style="margin-top:18px">' +
      gauge('结果置信度', percentText(confidence), confidence, '由食材属性完整度与规则命中情况综合得出') +
      gauge('数据完整度', percentText(completeness), completeness, '具备八维风味属性的食材用量占比') +
      '<div class="gauge">' +
      '<span class="label">参与计算配料数</span>' +
      '<span class="value">' + (count === null || count === undefined ? '—' : FL.escape(count) + ' 项') + '</span>' +
      '<span class="label" style="display:block;margin-top:10px">' + FL.escape(countHint) + '</span>' +
      '</div>' +
      '</div>';
  }

  function gauge(label, valueText, percent, hint) {
    var num = toNumber(percent);
    var width = num === null ? 0 : Math.max(0, Math.min(num, 100));
    return '<div class="gauge">' +
      '<span class="label">' + FL.escape(label) + '</span>' +
      '<span class="value">' + FL.escape(valueText) + '</span>' +
      '<div class="track"><div class="bar" style="--value:' + width.toFixed(1) + '%"></div></div>' +
      '<span class="label" style="display:block;margin-top:8px">' + FL.escape(hint) + '</span>' +
      '</div>';
  }

  /* ---------------- B2. 味觉打分（目标方案自身） ---------------- */

  /**
   * 目标方案自身的八维得分。
   *
   * 与偏移明细的分工：偏移明细回答「相对基准改了多少」，本面板回答
   * 「这份配方本身是什么味道画像」——雷达图单条轮廓也更适合展示后者。
   *
   * @param {Object} data 任务详情
   * @return {String} 面板 HTML
   */
  function scorePanel(data) {
    var result = data.result;
    if (!result || !result.target) {
      return '';
    }
    var scores = result.target.scores || {};
    var items = DIMENSIONS.map(function (item) {
      return { label: item.label, value: scoreOf(scores, item.key) };
    });
    var total = items.reduce(function (sum, item) { return sum + item.value; }, 0);
    var average = items.length ? total / items.length : 0;

    // 风味特征：得分最高的前三维度（为 0 的不列）
    var top = items.slice().sort(function (a, b) { return b.value - a.value; })
      .slice(0, 3)
      .filter(function (item) { return item.value > 0; });
    var profile = top.length
      ? top.map(function (item) { return item.label + ' ' + item.value.toFixed(1); }).join('、')
      : '八维得分均为 0（请检查配料是否都具备风味属性数据）';

    var cards = items.map(function (item) {
      var cls = item.value >= 20 ? ' is-strong' : (item.value <= 1 ? ' is-faint' : '');
      return '<div class="score-card' + cls + '">' +
        '<span class="score-label">' + FL.escape(item.label) + '</span>' +
        '<span class="score-value">' + item.value.toFixed(1) + '</span>' +
        '</div>';
    }).join('');

    var body =
      '<div class="notice" style="margin-bottom:14px">本面板是<strong>目标方案自身</strong>的八维得分，' +
      '不涉及与基准的比较，用于回答「这份配方是什么味道画像」；' +
      '「改了多少」请看下方的绝对差值图与偏移明细。</div>' +
      '<div class="score-grid">' + cards + '</div>' +
      '<div class="score-summary">风味特征：<strong>' + FL.escape(profile) + '</strong>' +
      '　·　平均强度 <strong>' + average.toFixed(1) + '</strong> / 100' +
      '　·　八维合计 ' + total.toFixed(1) + ' / 800</div>';
    return panel('味觉打分（目标方案）', '<span class="tag">0-100 相对评分</span>', body);
  }

  /* ---------------- C. 八维雷达图 ---------------- */

  function radarPanel(data) {
    var result = data.result;
    if (!result || !result.baseline || !result.target) {
      return panel('八维风味雷达', '', FL.empty('暂无雷达数据', '分析完成后即可查看基准配方与目标方案的八维对比。'));
    }
    var body =
      '<div class="radar-toolbar">' +
      '  <button type="button" class="seg-btn active" data-radar-mode="profile">目标方案轮廓</button>' +
      '  <button type="button" class="seg-btn" data-radar-mode="compare">叠加基准对比</button>' +
      '</div>' +
      '<div class="radar-wrap"><canvas id="radar"></canvas></div>' +
      '<div class="radar-legend" id="radarLegend"></div>';
    return panel('八维风味雷达', '<span class="tag">相对评分 0-100</span>', body);
  }

  /** 切换雷达显示模式（不重绘整页，只重画 canvas 与图例） */
  function bindRadarToggle() {
    var $body = $('#pageBody');
    $body.off('click.radarMode').on('click.radarMode', '[data-radar-mode]', function () {
      var mode = $(this).attr('data-radar-mode');
      if (mode === radarMode) { return; }
      radarMode = mode;
      $body.find('[data-radar-mode]').removeClass('active');
      $(this).addClass('active');
      drawRadar();
    });
  }

  /** 图例随模式变化，避免"两条线"默认就叠在一起造成视觉混乱 */
  function updateRadarLegend() {
    var $legend = $('#radarLegend');
    if (!$legend.length) { return; }
    if (radarMode === 'compare') {
      $legend.html(
        '<span><i style="background:transparent;border-top:2px dashed ' + BASELINE_COLOR + ';height:0"></i>基准配方（虚线，不填充）</span>' +
        '<span><i class="blue"></i>目标方案</span>' +
        '<span style="color:var(--muted)">两线越接近说明调整越小；绝对变化量请看下方差值图</span>');
    } else {
      $legend.html('<span><i class="blue"></i>目标方案八维轮廓（单一配方，不与基准叠加）</span>');
    }
  }

  /** 在 DOM 就绪后绘制雷达图（数据缺失时不绘制，避免抛异常） */
  function drawRadar() {
    var canvas = document.getElementById('radar');
    if (!canvas) { return; }
    var result = task && task.result ? task.result : null;
    if (!result || !result.baseline || !result.target) { return; }

    var baselineScores = result.baseline.scores || {};
    var targetScores = result.target.scores || {};
    var targetSeries = {
      name: '目标方案',
      color: TARGET_COLOR,
      values: DIMENSIONS.map(function (item) { return scoreOf(targetScores, item.key); })
    };
    var series;
    if (radarMode === 'compare') {
      series = [
        {
          name: '基准配方',
          color: BASELINE_COLOR,
          values: DIMENSIONS.map(function (item) { return scoreOf(baselineScores, item.key); }),
          dashed: true,      // 基准用虚线且不填充，避免两条半透明多边形糊在一起
          fill: false
        },
        targetSeries
      ];
    } else {
      series = [targetSeries];
    }
    radarOptions = {
      labels: DIMENSIONS.map(function (item) { return item.label; }),
      series: series,
      height: 330
    };
    FL.radar(canvas, radarOptions);
    updateRadarLegend();
  }

  /* ---------------- C2. 绝对差值图（补雷达图的盲区） ---------------- */

  /**
   * 雷达图八维共用一根轴（上限由最大值决定），基准值很低的维度即使变化比例很大，
   * 在图上也只是半径上的一小截；本面板按「绝对差值」归一化，直接回答
   * 「哪一维真正动得多」，与偏移明细表互为印证。
   */
  function diffPanel(data) {
    var deltas = (data.result && data.result.deltas) || [];
    if (!deltas.length) {
      return '';
    }
    var body =
      '<div class="notice" style="margin-bottom:14px">雷达图八维共用同一根轴，基准值很低的维度（例如甜 1.58）' +
      '即使变化比例很大，在图上也不明显；本图按<strong>绝对差值</strong>归一化，用于判断哪一维真正动得多。' +
      '数值为「目标值 − 基准值」，向右为上升、向左为下降。</div>' +
      '<div id="diffChart"></div>' +
      '<div class="diff-legend" style="margin-top:12px">' +
      '  <span><i style="background:var(--coral)"></i>上升（目标 &gt; 基准）</span>' +
      '  <span><i style="background:var(--blue)"></i>下降（目标 &lt; 基准）</span>' +
      '</div>';
    return panel('绝对差值（八维）', '<span class="tag">按绝对差值缩放</span>', body);
  }

  /** DOM 就绪后绘制差值条 */
  function drawDiffBars() {
    var host = document.getElementById('diffChart');
    if (!host || !FL.diffBars) { return; }
    var deltas = (task && task.result && task.result.deltas) || [];
    if (!deltas.length) { return; }
    var items = deltas.slice().sort(function (a, b) {
      return orderOf(a.dimension) - orderOf(b.dimension);
    }).map(function (item) {
      var baseline = toNumber(item.baseline);
      var percent = toNumber(item.changePercent);
      var lowBaseline = baseline !== null && Math.abs(baseline) < LOW_BASELINE;
      var tip = (item.dimensionLabel || labelOf(item.dimension)) +
        '：基准 ' + FL.fmtNumber(baseline, 2) + ' → 目标 ' + FL.fmtNumber(item.target, 2) +
        '，差值 ' + FL.fmtNumber(item.diff, 2) +
        (lowBaseline
          ? '（基准值低于 ' + LOW_BASELINE + '，比例 ' + (percent === null ? '—' : percent.toFixed(1) + '%') + ' 会被放大，请以差值判断）'
          : (percent === null ? '' : '，比例 ' + percent.toFixed(1) + '%'));
      return {
        label: item.dimensionLabel || labelOf(item.dimension),
        diff: toNumber(item.diff) || 0,
        tip: tip,
        lowBaseline: lowBaseline
      };
    });
    FL.diffBars(host, { items: items });
  }

  /* ---------------- D. 偏移明细 ---------------- */

  function deltaPanel(data) {
    var result = data.result;
    var deltas = result && result.deltas ? result.deltas.slice() : [];
    if (!deltas.length) {
      return panel('风味偏移明细', '', FL.empty('暂无偏移明细', '本次分析没有生成八维维度偏移数据。'));
    }
    // 按八维固定顺序展示，避免后端返回顺序变化影响阅读
    deltas.sort(function (a, b) { return orderOf(a.dimension) - orderOf(b.dimension); });

    var rows = deltas.map(function (item) {
      var percent = toNumber(item.changePercent);
      var baseline = toNumber(item.baseline);
      // 基准值过低时比例会被放大（如 1.58 → 10.95 算成 +593%），此时只给差值
      var lowBaseline = baseline !== null && Math.abs(baseline) < LOW_BASELINE;
      var percentCls = lowBaseline || percent === null || percent === 0 ? '' : (percent > 0 ? 'diff-up' : 'diff-down');
      var percentText = percent === null ? '—' : (percent > 0 ? '+' : '') + percent.toFixed(1) + '%';
      var diff = toNumber(item.diff);
      var diffText = diff === null ? '—' : (diff > 0 ? '+' : '') + diff.toFixed(2);
      var status = DELTA_STATUS[item.status] || { text: item.status || '—', cls: 'muted' };
      var label = item.dimensionLabel || labelOf(item.dimension);

      var percentCell;
      if (lowBaseline) {
        var tip = '基准值 ' + FL.fmtNumber(baseline, 2) + ' 低于 ' + LOW_BASELINE +
          '（0-100 量表上属于几乎感知不到），相对变化会被放大：原始计算值为 ' + percentText +
          '。请以差值 ' + diffText + ' 为准。';
        percentCell = '<td title="' + FL.escape(tip) + '"><span class="tag">基准极低 · 看差值</span></td>';
      } else {
        percentCell = '<td class="' + percentCls + '">' + FL.escape(percentText) + '</td>';
      }

      return '<tr>' +
        '<td><strong>' + FL.escape(label) + '</strong>' +
        (item.note ? '<br /><span style="color:var(--muted);font-size:11px">' + FL.escape(item.note) + '</span>' : '') +
        '</td>' +
        '<td>' + FL.escape(FL.fmtNumber(item.baseline, 2)) + '</td>' +
        '<td>' + FL.escape(FL.fmtNumber(item.target, 2)) + '</td>' +
        '<td>' + FL.escape(diffText) + '</td>' +
        percentCell +
        '<td>' + FL.statusTag(item.statusText || status.text, status.cls) + '</td>' +
        '</tr>';
    }).join('');

    var lowCount = deltas.filter(function (item) {
      var baseline = toNumber(item.baseline);
      return baseline !== null && Math.abs(baseline) < LOW_BASELINE;
    }).length;
    var note = '<div class="notice" style="margin:16px">判读说明：变化比例 =（目标值 − 基准值）/ max(|基准值|, 1) × 100%。' +
      '当基准值低于 ' + LOW_BASELINE + ' 时，分母过小会把比例放大到几百甚至上千 %（例如基准 1.58 → 目标 10.95 会算成 +593%，' +
      '实际只是「从几乎不甜变成有点甜」），这类维度在表中标为「基准极低 · 看差值」，请以<strong>差值</strong>列为准' +
      (lowCount > 0 ? '（本次有 ' + lowCount + ' 个维度属于这种情况）。' : '。') + '</div>';

    var body = note + '<div class="table-wrap"><table><thead><tr>' +
      '<th>维度</th><th>基准值</th><th>目标值</th><th>差值</th><th>变化比例</th><th>状态</th>' +
      '</tr></thead><tbody>' + rows + '</tbody></table></div>';
    return panel('风味偏移明细', '<span class="tag">比例为正=上升，为负=下降</span>', body, true);
  }

  /* ---------------- E. 补偿建议 ---------------- */

  function suggestionPanel(data) {
    var list = data.result && data.result.suggestions ? data.result.suggestions : [];
    if (!list.length) {
      return panel('补偿建议', '', FL.empty('本次未触发补偿规则', '说明调整未进入规则库中的风险区间。'));
    }

    var html = list.map(function (item, index) {
      // 有知识卡时用卡片标题作小标题：信息量比「建议增加鲜」大得多
      var title = item.knowledgeTitle ||
        ((item.actionText || '') + (item.dimensionLabel || labelOf(item.dimension) || ''));

      // 生成原因与注意事项是可解释性的核心，始终展示
      var lines = '<p>生成原因：' + FL.escape(item.reason || '—') + '</p>' +
        '<p>注意事项：' + FL.escape(item.risk || '—') + '</p>';

      var meta = [];
      if (item.target) { meta.push('调整对象：' + FL.escape(item.target)); }
      if (item.direction) { meta.push('建议方向：' + FL.escape(item.direction)); }
      if (item.referenceRange) { meta.push('参考幅度：' + FL.escape(item.referenceRange)); }
      if (item.candidates && item.candidates.length) {
        meta.push('候选食材：' + FL.escape(item.candidates.join('、')));
      }
      if (item.ruleName) { meta.push('触发规则：' + FL.escape(item.ruleName)); }
      if (meta.length) { lines += '<p>' + meta.join(' · ') + '</p>'; }

      // 依据单独成行：写明来源知识卡与数据出处，用户可据此核对，而不是只有一个结论
      var source = [];
      if (item.knowledgeCode) {
        source.push('知识卡 ' + FL.escape(item.knowledgeCode) +
          (item.knowledgeTitle ? '「' + FL.escape(item.knowledgeTitle) + '」' : ''));
      }
      if (item.evidenceSource) { source.push('依据：' + FL.escape(item.evidenceSource)); }
      if (source.length) {
        lines += '<p class="suggestion-source">' + source.join(' · ') + '</p>';
      }

      return '<div class="suggestion">' +
        '<span class="suggestion-num">' + (index + 1) + '</span>' +
        '<div><h3>' + FL.escape(title || '调整建议') + '</h3>' + lines + '</div>' +
        '<strong>' + FL.escape(item.changeText || '—') + '</strong>' +
        '</div>';
    }).join('');

    return panel('补偿建议', '<span class="tag">共 ' + list.length + ' 条</span>', html);
  }

  /* ---------------- F. 快照对比 ---------------- */

  function snapshotPanel(data) {
    var baseline = data.baselineSnapshot || null;
    var target = data.targetSnapshot || null;
    if (!baseline && !target) {
      return panel('配方快照对比', '', FL.empty('暂无配方快照', '该任务未保存基准配方或目标方案的快照数据。'));
    }
    var baselineAmounts = amountMap(baseline);
    var targetAmounts = amountMap(target);
    var body = '<div class="snapshot-cols">' +
      snapshotColumn('基准配方', baseline, targetAmounts) +
      snapshotColumn('目标方案', target, baselineAmounts) +
      '</div>';
    return panel('配方快照对比', '<span class="tag">用量发生变化的配料以高亮标记</span>', body);
  }

  function snapshotColumn(role, snapshot, otherAmounts) {
    if (!snapshot) {
      return '<div>' + FL.empty('无' + role + '快照', '该任务未保存这份快照数据。') + '</div>';
    }
    var items = snapshot.items || [];
    var head = '<div class="workspace-label">' + FL.escape(role) + '</div>' +
      '<div style="color:var(--muted);font-size:11.5px;line-height:1.8;margin:2px 0 6px">' +
      FL.escape(snapshot.recipeName || '未命名配方') +
      (snapshot.productType ? ' · ' + FL.escape(snapshot.productType) : '') +
      '<br />总用量 ' + FL.escape(FL.fmtNumber(snapshot.totalAmount, 2)) + ' · ' + items.length + ' 项配料' +
      (snapshot.snapshotAt ? ' · 快照于 ' + FL.escape(FL.fmtDate(snapshot.snapshotAt)) : '') +
      '</div>';

    if (!items.length) {
      return '<div>' + head + FL.empty('快照没有配料明细', '该快照未记录配料。') + '</div>';
    }

    var list = items.map(function (item) {
      var name = item.ingredientName || '未命名食材';
      var amount = toNumber(item.amount);
      var changed = Object.prototype.hasOwnProperty.call(otherAmounts, name) &&
        otherAmounts[name] !== amount;
      return '<li' + (changed ? ' class="changed"' : '') + '>' +
        '<span class="name">' + FL.escape(name) +
        (item.ingredientCategory ? ' <span class="tag">' + FL.escape(item.ingredientCategory) + '</span>' : '') +
        (item.attributeAvailable === false ? ' <span class="tag">缺属性数据</span>' : '') +
        '</span>' +
        '<span class="amount">' + FL.escape(FL.fmtNumber(item.amount, 2)) + ' ' +
        FL.escape(item.unit || 'g') + '</span>' +
        '</li>';
    }).join('');

    return '<div>' + head + '<ul class="snapshot-list">' + list + '</ul></div>';
  }

  /** 食材名 → 用量数值，用于判断两份快照的用量差异 */
  function amountMap(snapshot) {
    var map = {};
    if (!snapshot || !snapshot.items) { return map; }
    snapshot.items.forEach(function (item) {
      var value = toNumber(item.amount);
      if (item.ingredientName && value !== null) { map[item.ingredientName] = value; }
    });
    return map;
  }

  /* ---------------- G. 解释与免责 ---------------- */

  function explainPanel(data) {
    var result = data.result;
    var extra = result && result.engineVersion
      ? '<span class="tag">引擎版本 ' + FL.escape(result.engineVersion) + '</span>' : '';
    if (!result || !result.explanation) {
      return panel('解释与免责', extra, FL.empty('暂无解释说明', '本次分析未生成可解释文本。'));
    }
    return panel('解释与免责', extra, '<div class="explain">' + FL.escape(result.explanation) + '</div>');
  }

  /* ---------------- H. 试产反馈 ---------------- */

  function feedbackPanel(data) {
    var feedback = data.feedback || null;
    var updatedAt = feedback ? (feedback.updatedAt || feedback.createdAt) : null;
    var extra = '<span class="tag">' + (updatedAt ? '最近提交 ' + FL.escape(FL.fmtDate(updatedAt)) : '尚未提交') + '</span>';

    var helpful = feedback && feedback.helpful !== null && feedback.helpful !== undefined
      ? String(feedback.helpful) : '';
    var rating = feedback && feedback.rating !== null && feedback.rating !== undefined
      ? String(feedback.rating) : '';
    var trialResult = feedback && feedback.trialResult ? String(feedback.trialResult) : '';
    var comment = feedback && feedback.comment ? String(feedback.comment) : '';

    var ratingOptions = '<option value="">未评分</option>';
    for (var score = 1; score <= 5; score++) {
      ratingOptions += '<option value="' + score + '"' + (rating === String(score) ? ' selected' : '') +
        '>' + score + ' 分</option>';
    }
    var trialOptions = '<option value="">未标注</option>' + FL.TRIAL_RESULTS.map(function (item) {
      return '<option value="' + FL.escape(item.value) + '"' +
        (trialResult === item.value ? ' selected' : '') + '>' + FL.escape(item.text) + '</option>';
    }).join('');

    var body = '<div class="form-grid">' +
      '<div class="field">' +
      '<label>本结果是否有帮助</label>' +
      '<div class="check"><input type="radio" name="helpful" id="helpfulYes" value="1"' +
      (helpful === '1' ? ' checked' : '') + ' /><label for="helpfulYes">有帮助</label></div>' +
      '<div class="check"><input type="radio" name="helpful" id="helpfulNo" value="0"' +
      (helpful === '0' ? ' checked' : '') + ' /><label for="helpfulNo">帮助有限</label></div>' +
      '</div>' +
      '<div class="field"><label for="rating">评分（1-5）</label>' +
      '<select id="rating">' + ratingOptions + '</select></div>' +
      '<div class="field"><label for="trialResult">试产结果</label>' +
      '<select id="trialResult">' + trialOptions + '</select></div>' +
      '<div class="field span-2"><label for="comment">文字反馈<span class="hint">最多 1000 字</span></label>' +
      '<textarea id="comment" maxlength="1000" placeholder="记录试产表现、风味偏差与需要复盘的细节…">' +
      FL.escape(comment) + '</textarea></div>' +
      '</div>' +
      '<div class="form-actions">' +
      '<button class="primary-btn" type="button" id="feedbackBtn">提交反馈</button>' +
      '</div>';

    return panel('试产反馈', extra, body, false, 'id="feedbackPanel"');
  }

  /* ---------------- I. 事件 ---------------- */

  function bindEvents() {
    var $body = $('#pageBody');
    // I. 打印（打印样式已在 pages.css 中定义）
    $body.on('click', '#printBtn', function () { window.print(); });
    $body.on('click', '#feedbackBtn', submitFeedback);
    $(window).on('resize', function () {
      if (!radarOptions) { return; }
      window.clearTimeout(resizeTimer);
      resizeTimer = window.setTimeout(drawRadar, 180);
    });
  }

  function submitFeedback() {
    var $btn = $('#feedbackBtn');
    if (!task || $btn.prop('disabled')) { return; }

    var helpfulValue = $('input[name="helpful"]:checked').val();
    var ratingValue = $.trim($('#rating').val() || '');
    var trialValue = $.trim($('#trialResult').val() || '');
    var payload = {
      helpful: helpfulValue === undefined || helpfulValue === '' ? null : Number(helpfulValue),
      rating: ratingValue === '' ? null : Number(ratingValue),
      comment: $.trim($('#comment').val() || ''),
      trialResult: trialValue === '' ? null : trialValue
    };

    $btn.prop('disabled', true).text('提交中…');
    FL.post('/api/analysis/' + encodeURIComponent(taskId) + '/feedback', payload).then(function (feedback) {
      task.feedback = feedback || null;
      $('#feedbackPanel').replaceWith(feedbackPanel(task));
      FL.toast('试产反馈已保存，将用于后续规则校准。', 'ok', '提交成功');
    })['catch'](function (err) {
      $btn.prop('disabled', false).text('提交反馈');
      FL.toast(err.message || '反馈提交失败，请稍后重试。', 'error', '提交未完成');
    });
  }

  /* ---------------- 通用小工具 ---------------- */

  function panel(title, extra, body, flush, attrs) {
    return '<section class="panel" style="margin-top:18px"' + (attrs ? ' ' + attrs : '') + '>' +
      '<div class="panel-head"><h3>' + FL.escape(title) + '</h3><div>' + (extra || '') + '</div></div>' +
      (flush ? body : '<div class="panel-body">' + body + '</div>') +
      '</section>';
  }

  function toNumber(value) {
    if (value === null || value === undefined || value === '') { return null; }
    var num = Number(value);
    return isNaN(num) ? null : num;
  }

  function numberText(value) {
    var num = toNumber(value);
    return num === null ? '—' : String(num);
  }

  function percentText(value) {
    var num = toNumber(value);
    return num === null ? '—' : num.toFixed(2) + '%';
  }

  function scoreOf(scores, dimension) {
    var num = toNumber(scores ? scores[dimension] : null);
    return num === null ? 0 : num;
  }

  function labelOf(dimension) {
    for (var i = 0; i < DIMENSIONS.length; i++) {
      if (DIMENSIONS[i].key === dimension) { return DIMENSIONS[i].label; }
    }
    return dimension || '';
  }

  function orderOf(dimension) {
    for (var i = 0; i < DIMENSIONS.length; i++) {
      if (DIMENSIONS[i].key === dimension) { return i; }
    }
    return DIMENSIONS.length;
  }
}(window.jQuery, window.FL));
