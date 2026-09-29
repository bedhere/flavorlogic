/* ============================================================
   食材选择器（可搜索下拉）
   ------------------------------------------------------------
   背景：配料明细行原本用一个原生 <select> + <optgroup> 选食材。
   食材扩充到 40 项以上后，用户要在长列表里滚动查找，效率很低。
   本模块把原生下拉改造成「输入关键字 → 实时筛选 → 点选」的搜索框。

   设计要点（改动前请先读完）：
     1. 原生 <select class="ingredient-select"> **保留并隐藏**，继续作为
        唯一数据源。页面既有逻辑（syncRow / collectItems / isDuplicate /
        collectStats / selectIngredient）完全不需要改动——它们照旧读
        $select.val() 与 option 上的 data-name / data-unit。
     2. 选中后仍然对 <select> 派发 change 事件，页面的重复校验与行内
        同步逻辑照常执行。因此「重复食材被回退」时，搜索框的显示会跟着
        回退（显示由 sync() 从 select 反推，数据层与展示层不各存一份）。
     3. 面板挂在 document.body 上用 position:fixed 定位。原因：
        .shell 带 overflow:hidden，绝对定位面板会被外壳裁切
        （与 .mega 导航面板是同一个问题）。
     4. 面板内容每次展开时从 select 的 option 重新读取，所以
        selectIngredient() 动态补的「已停用」占位项会自动出现在列表里。

   使用方式：
     · 行 HTML：.ing-picker > .ing-search + .ing-toggle + .ingredient-select
     · 行内食材状态每次变化后调用 FL.ingredientPicker.sync($tr)
   ============================================================ */
(function ($, FL) {
  'use strict';

  /** 单次最多渲染的候选条数，防止极端数据量下 DOM 过大 */
  var MAX_RENDER = 300;
  /** 面板最大高度，与 pages.css 的 .ing-panel 保持一致 */
  var PANEL_MAX_HEIGHT = 268;

  var $panel = null;      // 共享面板：同一时刻只展开一个
  var $input = null;      // 当前展开的搜索框
  var candidates = [];    // 当前行的全部候选
  var activeIndex = -1;   // 键盘高亮项下标
  var viewportBound = false;

  function esc(text) {
    return FL.escape(text === null || text === undefined ? '' : String(text));
  }

  function currentSelect() {
    return $input ? $input.closest('.ing-picker').find('.ingredient-select') : $();
  }

  /* ---------------- 候选读取与匹配 ---------------- */

  /** 从 select 读取候选；空值占位项（请选择食材）被跳过 */
  function readOptions($select) {
    var list = [];
    $select.find('option').each(function (index) {
      var $option = $(this);
      var id = $.trim($option.val() || '');
      if (!id) { return; }
      list.push({
        id: id,
        name: $option.attr('data-name') || $.trim($option.text()),
        group: $option.parent('optgroup').attr('label') || '未分类',
        missing: $option.attr('data-missing') === '1',
        order: index
      });
    });
    return list;
  }

  /**
   * 子序列匹配：按顺序出现即可，不要求连续。
   * 中文输入时用户常只记得关键字，例如输入「鸡肉」也能命中「鸡胸肉」。
   */
  function isSubsequence(query, text) {
    var i = 0;
    for (var j = 0; j < text.length && i < query.length; j++) {
      if (text.charAt(j) === query.charAt(i)) { i++; }
    }
    return i === query.length;
  }

  /** 相关性打分：越小越靠前；-1 表示不匹配 */
  function scoreOf(item, query) {
    var at = item.name.indexOf(query);
    if (at === 0) { return 1; }
    if (at > 0) { return 2; }
    if (item.group.indexOf(query) >= 0) { return 3; }
    if (isSubsequence(query, item.name)) { return 4; }
    return -1;
  }

  /** 过滤并按相关性排序 */
  function filtered(query) {
    if (!query) { return candidates.slice(); }
    var scored = [];
    candidates.forEach(function (item) {
      var score = scoreOf(item, query);
      if (score >= 0) { scored.push({ item: item, score: score }); }
    });
    scored.sort(function (a, b) {
      return a.score - b.score || a.item.order - b.item.order;
    });
    return scored.map(function (entry) { return entry.item; });
  }

  /* ---------------- 渲染 ---------------- */

  /** 高亮命中的连续片段 */
  function highlight(name, query) {
    if (!query) { return esc(name); }
    var at = name.indexOf(query);
    if (at < 0) { return esc(name); }
    return esc(name.slice(0, at)) +
      '<mark>' + esc(name.slice(at, at + query.length)) + '</mark>' +
      esc(name.slice(at + query.length));
  }

  function optionHtml(item, index, query, selectedId) {
    var cls = 'ing-option' +
      (item.id === selectedId ? ' is-selected' : '') +
      (index === activeIndex ? ' is-active' : '');
    return '<button type="button" class="' + cls + '" role="option"' +
      ' data-id="' + esc(item.id) + '" data-index="' + index + '">' +
      '<span class="ing-option-name">' + highlight(item.name, query) + '</span>' +
      (item.missing ? '<span class="ing-option-meta">已停用</span>' : '') +
      '</button>';
  }

  function render(query) {
    var list = filtered(query);
    var shown = list.slice(0, MAX_RENDER);
    var selectedId = $.trim(currentSelect().val() || '');
    var html = '';

    if (!shown.length) {
      html = '<div class="ing-empty">没有匹配的食材<br />' +
        '可到「管理后台 · 食材与规则」先补充该食材</div>';
    } else if (query) {
      // 有搜索词：按相关性平铺，便于快速定位
      shown.forEach(function (item, index) {
        html += optionHtml(item, index, query, selectedId);
      });
    } else {
      // 无搜索词：按分类分组，保留原生下拉的分组浏览体验
      var lastGroup = null;
      shown.forEach(function (item, index) {
        if (item.group !== lastGroup) {
          lastGroup = item.group;
          html += '<div class="ing-group" role="group">' + esc(item.group) + '</div>';
        }
        html += optionHtml(item, index, query, selectedId);
      });
    }
    $panel.html(html);
  }

  function paintActive() {
    var $options = $panel.find('.ing-option');
    $options.removeClass('is-active');
    var $target = $options.filter('[data-index="' + activeIndex + '"]');
    if (!$target.length) { return; }
    $target.addClass('is-active');
    var el = $target.get(0);
    if (el.scrollIntoView) { el.scrollIntoView({ block: 'nearest' }); }
  }

  /* ---------------- 展开 / 收起 / 定位 ---------------- */

  function ensurePanel() {
    if ($panel && $panel.length) { return $panel; }
    $panel = $('<div>', { 'class': 'ing-panel', 'role': 'listbox' }).prop('hidden', true);
    // mousedown 阻止默认行为：否则点选项时搜索框会先失焦，
    // blur 先于 click 触发，选项还没被选中面板就关了。
    $panel.on('mousedown', function (e) { e.preventDefault(); });
    $panel.on('click', '.ing-option', function () {
      pick($(this).attr('data-id'));
    });
    $('body').append($panel);
    return $panel;
  }

  function isOpen() {
    return !!($panel && $panel.length && !$panel.prop('hidden'));
  }

  function place($el) {
    if (!isOpen() || !$el || !$el.length) { return; }
    var rect = $el.get(0).getBoundingClientRect();
    var margin = 8;
    var width = Math.max(rect.width, 220);
    var left = Math.min(rect.left, Math.max(margin, window.innerWidth - width - margin));
    var panelHeight = Math.min($panel.get(0).offsetHeight || PANEL_MAX_HEIGHT, PANEL_MAX_HEIGHT);
    // 下方空间不足且上方够用时向上翻转，避免面板跑出视口
    var flip = (window.innerHeight - rect.bottom) < (panelHeight + margin) &&
      rect.top > (panelHeight + margin);
    $panel.css({
      left: left + 'px',
      top: (flip ? rect.top - panelHeight - 4 : rect.bottom + 4) + 'px',
      width: width + 'px',
      maxHeight: PANEL_MAX_HEIGHT + 'px'
    });
  }

  function onViewportChange() {
    if (!isOpen() || !$input) { return; }
    // 行已被移除（例如点了「移除」）时直接收起
    if (!$input.get(0).offsetParent) { close(); return; }
    place($input);
  }

  function bindViewport() {
    if (viewportBound) { return; }
    // 用捕获阶段监听 scroll：滚动事件不冒泡，但捕获能拿到任意滚动容器的滚动
    window.addEventListener('scroll', onViewportChange, true);
    window.addEventListener('resize', onViewportChange);
    viewportBound = true;
  }

  function unbindViewport() {
    if (!viewportBound) { return; }
    window.removeEventListener('scroll', onViewportChange, true);
    window.removeEventListener('resize', onViewportChange);
    viewportBound = false;
  }

  /**
   * 展开面板。
   * @param {jQuery} $el   搜索框
   * @param {String} query 初始筛选词；不传表示「浏览全部」。
   *   注意：聚焦到一个已有选中食材的行时，必须按浏览全部处理，否则列表会被
   *   过滤成只剩当前这一项，用户按方向键就没得挑了。输入框里已有的食材名
   *   会在聚焦时被整体选中，用户直接输入即替换（见下方 focus 处理）。
   */
  function open($el, query) {
    if (!$el || !$el.length) { return; }
    var $select = $el.closest('.ing-picker').find('.ingredient-select');
    if (!$select.length) { return; }
    ensurePanel();
    $input = $el;
    candidates = readOptions($select);
    activeIndex = -1;
    render(query === undefined || query === null ? '' : query);
    $panel.prop('hidden', false);
    place($el);
    bindViewport();
  }

  function close() {
    if ($panel && $panel.length) { $panel.prop('hidden', true); }
    unbindViewport();
    $input = null;
    activeIndex = -1;
  }

  /* ---------------- 选中 ---------------- */

  function pick(id) {
    if (!$input || id === null || id === undefined || id === '') { return; }
    var $row = $input.closest('.item-row');
    var $select = $input.closest('.ing-picker').find('.ingredient-select');
    close();
    if ($.trim($select.val() || '') === String(id)) {
      // 选中的还是同一个食材：只恢复显示，不派发 change
      // （避免把用户手动改过的单位重置回食材默认单位）
      sync($row);
      return;
    }
    $select.val(id);
    // 交给页面既有逻辑：重复校验 → syncRow → 回填显示与属性摘要
    $select.trigger('change');
  }

  function moveActive(delta) {
    if (!isOpen()) { return; }
    var count = $panel.find('.ing-option').length;
    if (!count) { return; }
    activeIndex = (activeIndex + delta + count) % count;
    paintActive();
  }

  /** 回车提交：优先取键盘高亮项，否则取第一条 */
  function commitActive() {
    if (!isOpen()) { return false; }
    var $options = $panel.find('.ing-option');
    if (!$options.length) { return false; }
    var $target = activeIndex >= 0
      ? $options.filter('[data-index="' + activeIndex + '"]')
      : $options.eq(0);
    if (!$target.length) { return false; }
    pick($target.attr('data-id'));
    return true;
  }

  /* ---------------- 展示层同步 ---------------- */

  /**
   * 把搜索框的显示同步成 select 的当前值。
   * 所有会改变行内食材状态的路径最终都会走到这里，因此「重复食材回退」
   * 「历史明细回填」「已停用食材占位」三种情况都不会出现显示与数据不一致。
   */
  function sync($row) {
    if (!$row || !$row.length) { return; }
    var $select = $row.find('.ingredient-select');
    var $el = $row.find('.ing-search');
    if (!$select.length || !$el.length) { return; }
    var id = $.trim($select.val() || '');
    var $option = $select.find('option:selected');
    var name = id ? ($option.attr('data-name') || '') : '';
    $el.val(name);
    $el.attr('title', name || '');
    $el.toggleClass('is-missing', id ? ($option.attr('data-missing') === '1') : false);
    // 选完食材后输入框仍保持焦点，此时把内容整体选中：用户接着输入即替换成
    // 新的关键字，而不是把旧食材名当成前缀（否则会出现「酵母抽提物白」这种查询）
    var el = $el.get(0);
    if (el && el === document.activeElement && el.select && name) { el.select(); }
  }

  /* ---------------- 事件绑定（委托到 document，动态新增行自动生效） ---------------- */

  $(document)
    .on('focus', '.ing-search', function () {
      var el = this;
      open($(el));
      // 已有内容时整体选中：用户直接输入关键字即可替换掉旧食材名，
      // 不必先手动清空（与浏览器地址栏的行为一致）
      if (el.value && el.select) { el.select(); }
    })
    .on('input', '.ing-search', function () {
      var $el = $(this);
      var query = $.trim($el.val() || '');
      if (!isOpen() || !$input || $input.get(0) !== $el.get(0)) {
        open($el, query);
        return;
      }
      activeIndex = -1;
      render(query);
      place($el);
    })
    .on('keydown', '.ing-search', function (e) {
      var key = e.key;
      if (key === 'ArrowDown') {
        e.preventDefault();
        if (isOpen()) { moveActive(1); } else { open($(this)); }
        return;
      }
      if (key === 'ArrowUp') {
        if (!isOpen()) { return; }
        e.preventDefault();
        moveActive(-1);
        return;
      }
      if (key === 'Enter') {
        if (!isOpen()) { return; }
        e.preventDefault();
        commitActive();
        return;
      }
      if (key === 'Escape') {
        if (!isOpen()) { return; }
        e.preventDefault();
        close();
        sync($(this).closest('.item-row'));
        return;
      }
      if (key === 'Tab') {
        close();
      }
    })
    .on('blur', '.ing-search', function () {
      // 点面板选项时输入框不会失焦（面板 mousedown 已 preventDefault），
      // 所以能走到这里说明用户确实点了别处：把没点选的关键字还原成已选食材。
      var $el = $(this);
      window.setTimeout(function () {
        if ($input && $input.get(0) === $el.get(0)) { close(); }
        sync($el.closest('.item-row'));
      }, 0);
    })
    .on('mousedown', '.ing-toggle', function (e) {
      // 同样阻止默认行为，避免按钮抢走焦点后先触发 blur 关闭面板
      e.preventDefault();
    })
    .on('click', '.ing-toggle', function (e) {
      e.preventDefault();
      var $el = $(this).closest('.ing-picker').find('.ing-search');
      if (!$el.length) { return; }
      if (isOpen() && $input && $input.get(0) === $el.get(0)) {
        close();
        sync($el.closest('.item-row'));
        return;
      }
      // 聚焦即展开，交互与直接点输入框完全一致
      $el.trigger('focus');
    });

  FL.ingredientPicker = {
    /** 同步某一行搜索框的显示（行内食材状态变化后调用） */
    sync: sync,
    /** 收起面板（切页、提交前可调用） */
    close: close,
    /** 面板是否展开 */
    isOpen: isOpen
  };
}(window.jQuery, window.FL));
