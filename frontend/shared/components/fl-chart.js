/* ============================================================
   食之有理 FlavorLogic —— 轻量图表组件（原生 Canvas，无第三方依赖）
   ------------------------------------------------------------
   提供：
     FL.radar(canvas, options)  八维风味雷达图（基准 vs 目标）
     FL.bars(container, options) 维度对比条形图（可选）
   颜色默认取设计系统的强调色，保证与页面视觉一致。
   ============================================================ */
(function (window) {
  'use strict';

  var FL = window.FL || {};

  var DEFAULTS = {
    accent: '#157a56',
    blue: '#527ee9',
    grid: '#d8ded7',
    ink: '#17231f',
    muted: '#68746d',
    surface: '#ffffff'
  };

  function cssVar(name, fallback) {
    try {
      var value = getComputedStyle(document.documentElement).getPropertyValue(name);
      return value && value.trim() ? value.trim() : fallback;
    } catch (ignore) {
      return fallback;
    }
  }

  function palette() {
    return {
      accent: cssVar('--accent', DEFAULTS.accent),
      blue: cssVar('--blue', DEFAULTS.blue),
      grid: cssVar('--line', DEFAULTS.grid),
      ink: cssVar('--ink', DEFAULTS.ink),
      muted: cssVar('--muted', DEFAULTS.muted),
      surface: cssVar('--surface', DEFAULTS.surface)
    };
  }

  function initCanvas(canvas, height) {
    var ratio = window.devicePixelRatio || 1;
    var width = canvas.parentNode ? canvas.parentNode.clientWidth : canvas.clientWidth;
    if (!width) { width = 420; }
    canvas.width = width * ratio;
    canvas.height = height * ratio;
    canvas.style.width = width + 'px';
    canvas.style.height = height + 'px';
    var ctx = canvas.getContext('2d');
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.clearRect(0, 0, width, height);
    return { ctx: ctx, width: width, height: height };
  }

  /**
   * 绘制八维雷达图。
   * @param {HTMLCanvasElement} canvas 画布
   * @param {Object} options {labels:[], series:[{name, color, values:[]}], max}
   */
  FL.radar = function (canvas, options) {
    if (!canvas) { return; }
    var opts = options || {};
    var labels = opts.labels || [];
    var series = opts.series || [];
    var colors = palette();
    var box = initCanvas(canvas, opts.height || 320);
    var ctx = box.ctx;
    var width = box.width;
    var height = box.height;
    var cx = width / 2;
    var cy = height / 2 + 6;
    var radius = Math.min(width, height) / 2 - 54;
    if (radius < 60) { radius = 60; }
    var count = labels.length || 1;

    // 最大值：取数据与 100 的较大者，保证刻度可比
    var max = opts.max || 0;
    series.forEach(function (item) {
      (item.values || []).forEach(function (value) {
        if (value > max) { max = value; }
      });
    });
    if (max <= 0) { max = 100; }
    max = Math.ceil(max / 20) * 20;

    function point(index, ratio) {
      var angle = -Math.PI / 2 + (Math.PI * 2 * index) / count;
      return {
        x: cx + Math.cos(angle) * radius * ratio,
        y: cy + Math.sin(angle) * radius * ratio
      };
    }

    // 网格（5 圈）
    ctx.lineWidth = 1;
    for (var ring = 1; ring <= 5; ring++) {
      var ratio = ring / 5;
      ctx.beginPath();
      for (var i = 0; i < count; i++) {
        var p = point(i, ratio);
        if (i === 0) { ctx.moveTo(p.x, p.y); } else { ctx.lineTo(p.x, p.y); }
      }
      ctx.closePath();
      ctx.strokeStyle = colors.grid;
      ctx.globalAlpha = ring === 5 ? 1 : 0.65;
      ctx.stroke();
    }
    ctx.globalAlpha = 1;

    // 轴线
    for (var a = 0; a < count; a++) {
      var end = point(a, 1);
      ctx.beginPath();
      ctx.moveTo(cx, cy);
      ctx.lineTo(end.x, end.y);
      ctx.strokeStyle = colors.grid;
      ctx.globalAlpha = 0.7;
      ctx.stroke();
    }
    ctx.globalAlpha = 1;

    // 数据多边形
    // 支持按序列控制填充与线型：对比模式下基准用虚线且不填充，避免两条半透明多边形叠在一起糊成一团
    series.forEach(function (item, seriesIndex) {
      var color = item.color || (seriesIndex === 0 ? colors.accent : colors.blue);
      var values = item.values || [];
      ctx.beginPath();
      for (var i = 0; i < count; i++) {
        var value = Number(values[i] || 0);
        var r = max === 0 ? 0 : Math.max(0, Math.min(value / max, 1));
        var p = point(i, r);
        if (i === 0) { ctx.moveTo(p.x, p.y); } else { ctx.lineTo(p.x, p.y); }
      }
      ctx.closePath();

      if (item.fill !== false) {
        ctx.fillStyle = color;
        ctx.globalAlpha = item.fillAlpha === undefined ? 0.16 : item.fillAlpha;
        ctx.fill();
        ctx.globalAlpha = 1;
      }
      ctx.setLineDash(item.dashed ? [5, 4] : []);
      ctx.strokeStyle = color;
      ctx.lineWidth = item.dashed ? 1.6 : 2;
      ctx.stroke();
      ctx.setLineDash([]);

      // 数据点（虚线序列不画点，减少视觉噪声）
      if (item.dashed) { return; }
      for (var j = 0; j < count; j++) {
        var value2 = Number(values[j] || 0);
        var ratio2 = max === 0 ? 0 : Math.max(0, Math.min(value2 / max, 1));
        var pt = point(j, ratio2);
        ctx.beginPath();
        ctx.arc(pt.x, pt.y, 3, 0, Math.PI * 2);
        ctx.fillStyle = color;
        ctx.fill();
      }
    });

    // 轴标签与数值
    ctx.font = '11px "Microsoft YaHei", system-ui, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    for (var k = 0; k < count; k++) {
      var outer = point(k, 1.22);
      ctx.fillStyle = colors.ink;
      ctx.fillText(labels[k], outer.x, outer.y - 7);
      if (series.length) {
        var parts = series.map(function (item) {
          return Number((item.values || [])[k] || 0).toFixed(1);
        });
        ctx.fillStyle = colors.muted;
        ctx.font = '10px "Microsoft YaHei", system-ui, sans-serif';
        ctx.fillText(parts.join(' / '), outer.x, outer.y + 7);
        ctx.font = '11px "Microsoft YaHei", system-ui, sans-serif';
      }
    }
    return box;
  };

  /**
   * 绘制横向对比条（基准 vs 目标）。
   * @param {HTMLElement} container 容器
   * @param {Object} options {items:[{label, baseline, target, unit}], max}
   */
  FL.bars = function (container, options) {
    if (!container) { return; }
    var opts = options || {};
    var items = opts.items || [];
    var colors = palette();
    var max = opts.max || 0;
    items.forEach(function (item) {
      max = Math.max(max, Number(item.baseline || 0), Number(item.target || 0));
    });
    if (max <= 0) { max = 100; }
    max = Math.ceil(max / 10) * 10;

    var html = '<div class="bars">';
    items.forEach(function (item) {
      var baseline = Number(item.baseline || 0);
      var target = Number(item.target || 0);
      html += '<div class="bar-row" style="grid-template-columns:56px minmax(0,1fr) 84px">' +
        '<span class="bar-label">' + (item.label || '') + '</span>' +
        '<span style="display:grid;gap:5px">' +
        '<span class="track"><span class="bar" style="--value:' + Math.min(baseline / max * 100, 100).toFixed(1) + '%;background:' + colors.accent + '"></span></span>' +
        '<span class="track"><span class="bar target" style="--value:' + Math.min(target / max * 100, 100).toFixed(1) + '%;background:' + colors.blue + '"></span></span>' +
        '</span>' +
        '<span class="bar-value">' + baseline.toFixed(1) + '<br />' + target.toFixed(1) + '</span>' +
        '</div>';
    });
    html += '</div>';
    container.innerHTML = html;
  };

  /**
   * 绝对差值条形图：以 0 为中心左右展开，用于比较各维度的**绝对变化量**。
   *
   * 存在的理由：雷达图八维共用一根轴（上限由最大值决定），基准值很低的维度
   * （例如甜 1.58）即使变化比例很大，在图上也只是半径上的一小截；
   * 本图按绝对差值归一化，直接回答「哪一维真正动得多」。
   *
   * @param {HTMLElement} container 容器
   * @param {Object} options {items:[{label, diff, tip, lowBaseline}]}
   */
  FL.diffBars = function (container, options) {
    if (!container) { return; }
    var opts = options || {};
    var items = opts.items || [];
    if (!items.length) { container.innerHTML = ''; return; }
    var colors = palette();
    var escape = (window.FL && window.FL.escape) ? window.FL.escape : function (v) { return v == null ? '' : String(v); };

    var max = 0;
    items.forEach(function (item) {
      var value = Math.abs(Number(item.diff || 0));
      if (value > max) { max = value; }
    });
    if (max <= 0) { max = 1; }

    var html = '<div class="diff-bars">';
    items.forEach(function (item) {
      var diff = Number(item.diff || 0);
      var isUp = diff >= 0;
      // 单侧最多占 50% 宽度，正负各自从中心线向外伸展
      var width = Math.min(Math.abs(diff) / max, 1) * 50;
      var valueText = (isUp ? '+' : '') + diff.toFixed(2);
      html += '<div class="diff-row' + (item.lowBaseline ? ' is-low' : '') + '">' +
        '<span class="bar-label">' + escape(item.label) + '</span>' +
        '<span class="diff-axis" title="' + escape(item.tip || '') + '">' +
        '  <span class="diff-bar ' + (isUp ? 'up' : 'down') + '" style="width:' + width.toFixed(2) + '%"></span>' +
        '</span>' +
        '<span class="diff-value ' + (isUp ? 'diff-up' : 'diff-down') + '">' + escape(valueText) +
        (item.lowBaseline ? ' <span class="diff-flag" title="基准值过低，比例会被放大，请以差值判断">基准低</span>' : '') +
        '</span>' +
        '</div>';
    });
    html += '</div>';
    container.innerHTML = html;
  };

  window.FL = FL;
}(window));
