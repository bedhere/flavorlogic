/* ============================================================
   食之有理 FlavorLogic —— 前端公共层
   ------------------------------------------------------------
   职责：
     1. 统一 API 客户端（jQuery Ajax + {code,message,data} 协议 + 401/403 统一处理）
     2. 统一应用外壳（顶栏 + 侧栏 + 面包屑），保证所有页面共用同一套壳
     3. 通用组件：提示条、确认框（Bootstrap Modal）、分页器、状态标签、空状态
     4. 通用工具：HTML 转义、时间格式化、URL 参数、主题切换
   ============================================================ */
(function (window, $) {
  'use strict';

  var FL = window.FL || {};

  /* ---------------- 上下文与常量 ---------------- */

  // 形如 /flavorlogic/dashboard.html → /flavorlogic ；支持 /flavorlogic/admin/users.html
  FL.ctx = location.pathname.replace(/\/(admin\/)?[^\/]*\.html?$/, '').replace(/\/$/, '');

  FL.GOALS = [
    { value: 'GENERAL', text: '综合分析' },
    { value: 'REPLACE', text: '原料替换' },
    { value: 'REDUCE_SUGAR', text: '控糖调整' },
    { value: 'REDUCE_FAT', text: '控脂调整' },
    { value: 'ADJUST_STIMULATION', text: '刺激度调整' },
    { value: 'REGION_ADAPT', text: '区域适配' }
  ];

  FL.TASK_STATUS = {
    PENDING: { text: '待处理', cls: 'muted' },
    RUNNING: { text: '分析中', cls: 'warn' },
    COMPLETED: { text: '已完成', cls: '' },
    FAILED: { text: '失败', cls: 'danger' }
  };

  FL.ARTICLE_STATUS = {
    0: { text: '草稿', cls: 'muted' },
    1: { text: '已发布', cls: '' },
    2: { text: '已下架', cls: 'warn' },
    3: { text: '已删除', cls: 'danger' }
  };

  FL.goalText = function (value) {
    for (var i = 0; i < FL.GOALS.length; i++) {
      if (FL.GOALS[i].value === value) { return FL.GOALS[i].text; }
    }
    return value || '综合分析';
  };

  FL.TRIAL_RESULTS = [
    { value: 'NOT_TRIED', text: '尚未试产' },
    { value: 'PASSED', text: '试产通过' },
    { value: 'PARTIAL', text: '部分达成' },
    { value: 'FAILED', text: '试产未通过' }
  ];

  /* ---------------- 通用工具 ---------------- */

  FL.escape = function (value) {
    if (value === null || value === undefined) { return ''; }
    return String(value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  };

  FL.query = function (name) {
    var match = new RegExp('[?&]' + name + '=([^&#]*)').exec(location.search);
    return match ? decodeURIComponent(match[1].replace(/\+/g, ' ')) : null;
  };

  FL.fmtDate = function (value) {
    if (!value) { return '—'; }
    return String(value).substring(0, 16);
  };

  FL.fmtNumber = function (value, digits) {
    if (value === null || value === undefined || value === '') { return '—'; }
    var num = Number(value);
    if (isNaN(num)) { return String(value); }
    return num.toFixed(digits === undefined ? 2 : digits);
  };

  /* ---------------- 提示条 ---------------- */

  FL.toast = function (message, type, title) {
    var host = document.getElementById('toastHost');
    if (!host) {
      host = document.createElement('div');
      host.id = 'toastHost';
      host.className = 'toast-host';
      document.body.appendChild(host);
    }
    var el = document.createElement('div');
    el.className = 'toast' + (type ? ' ' + type : '');
    el.innerHTML = (title ? '<strong>' + FL.escape(title) + '</strong>' : '') + FL.escape(message);
    host.appendChild(el);
    window.setTimeout(function () {
      el.style.transition = 'opacity .25s ease, transform .25s ease';
      el.style.opacity = '0';
      el.style.transform = 'translateY(8px)';
      window.setTimeout(function () { if (el.parentNode) { el.parentNode.removeChild(el); } }, 260);
    }, type === 'error' ? 5200 : 3200);
  };

  FL.error = function (message, code) {
    var err = new Error(message || '请求失败');
    err.bizCode = code;
    return err;
  };

  /** 把 Promise 的失败统一转成提示条（页面可自行 catch 覆盖） */
  FL.fail = function (promise) {
    return promise['catch'](function (err) {
      FL.toast(err.message || '操作失败', 'error', '操作未完成');
      throw err;
    });
  };

  /* ---------------- 确认框（Bootstrap Modal） ---------------- */

  FL.confirm = function (message, options) {
    var opts = $.extend({ title: '请确认', okText: '确定', danger: false }, options || {});
    return new Promise(function (resolve) {
      var id = 'flConfirm' + Date.now();
      var html =
        '<div class="modal fade" id="' + id + '" tabindex="-1">' +
        '  <div class="modal-dialog modal-dialog-centered modal-sm">' +
        '    <div class="modal-content" style="border-radius:12px;border:1px solid var(--line)">' +
        '      <div class="modal-header" style="border-bottom:1px solid var(--line)">' +
        '        <h5 class="modal-title" style="font-size:15px">' + FL.escape(opts.title) + '</h5>' +
        '        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="关闭"></button>' +
        '      </div>' +
        '      <div class="modal-body" style="font-size:13px;color:var(--muted);line-height:1.8">' + FL.escape(message) + '</div>' +
        '      <div class="modal-footer" style="border-top:1px solid var(--line)">' +
        '        <button type="button" class="secondary-btn" data-bs-dismiss="modal">取消</button>' +
        '        <button type="button" class="primary-btn" data-role="ok"' +
        (opts.danger ? ' style="background:#c2503a;border-color:#c2503a"' : '') + '>' + FL.escape(opts.okText) + '</button>' +
        '      </div>' +
        '    </div>' +
        '  </div>' +
        '</div>';
      var $el = $(html).appendTo('body');
      var modal = new window.bootstrap.Modal($el[0]);
      var decided = false;
      $el.find('[data-role="ok"]').on('click', function () { decided = true; modal.hide(); });
      $el.on('hidden.bs.modal', function () {
        $el.remove();
        resolve(decided);
      });
      modal.show();
    });
  };

  /* ---------------- 操作成功提示（居中弹出、自动消失） ---------------- */

  /**
   * 操作成功提示：屏幕居中弹出的对勾卡片，短暂停留后自动消失。
   *
   * 与右下角 FL.toast 的分工：toast 用于过程性提示；本方法用于「一个动作已完成」的
   * 明确反馈，并可与跳转串联——传入 redirect 时会在提示消失后自动跳转，
   * 正好对应「保存成功 → 跳转到配方库」这类流程。
   *
   * @param {String} message   补充说明（可空）
   * @param {Object} options   {title:'保存成功', duration:1200, redirect:'/recipes.html'}
   * @return {Promise}         提示消失（并完成跳转）后 resolve
   */
  FL.success = function (message, options) {
    var opts = $.extend({ title: '操作成功', duration: 1200, redirect: null }, options || {});
    return new Promise(function (resolve) {
      var host = document.getElementById('flSuccessHost');
      if (!host) {
        host = document.createElement('div');
        host.id = 'flSuccessHost';
        host.className = 'success-host';
        document.body.appendChild(host);
      }
      host.innerHTML =
        '<div class="success-card">' +
        '  <div class="success-mark" aria-hidden="true">✓</div>' +
        '  <strong class="success-title">' + FL.escape(opts.title) + '</strong>' +
        (message ? '<p class="success-text">' + FL.escape(message) + '</p>' : '') +
        '</div>';
      // 触发进入动画（先渲染一帧再加 is-open）
      window.requestAnimationFrame(function () { host.classList.add('is-open'); });

      window.setTimeout(function () {
        host.classList.remove('is-open');
        window.setTimeout(function () {
          host.innerHTML = '';
          if (opts.redirect) {
            location.href = FL.ctx + opts.redirect;
          }
          resolve(true);
        }, 240);
      }, opts.duration);
    });
  };

  /* ---------------- API 客户端 ---------------- */

  FL.redirectToLogin = function () {
    var path = location.pathname.replace(FL.ctx, '') || '/';
    // 已经在登录/注册页时直接返回：否则 /api/auth/me 的 401 会把自己再次重定向到登录页，
    // 回跳地址每刷新一次就嵌套一层，最终 URL 超长被容器拒绝（400 Bad Request）
    if (/^\/(login|register)\.html$/.test(path)) {
      return;
    }
    // 只保留非 redirect 的查询参数，避免回跳地址层层嵌套
    var params = [];
    var raw = location.search.replace(/^\?/, '');
    if (raw) {
      raw.split('&').forEach(function (pair) {
        if (pair && pair.indexOf('redirect=') !== 0) {
          params.push(pair);
        }
      });
    }
    var back = path + (params.length ? '?' + params.join('&') : '');
    if (back.length > 300) {
      back = path;   // 回跳地址过长时退化为仅路径，确保 URL 始终合法
    }
    location.href = FL.ctx + '/login.html?redirect=' + encodeURIComponent(back);
  };

  FL.api = function (method, path, body, options) {
    var opts = options || {};
    return new Promise(function (resolve, reject) {
      var settings = {
        url: FL.ctx + path,
        type: method,
        dataType: 'json',
        cache: false
      };
      if (body !== undefined && body !== null) {
        settings.contentType = 'application/json; charset=UTF-8';
        settings.data = JSON.stringify(body);
      }
      $.ajax(settings).done(function (res) {
        if (res && res.code === 0) {
          resolve(res.data);
        } else {
          reject(FL.error((res && res.message) || '请求失败', res && res.code));
        }
      }).fail(function (xhr) {
        var message = '网络异常，请稍后重试';
        try {
          var parsed = JSON.parse(xhr.responseText);
          if (parsed && parsed.message) { message = parsed.message; }
        } catch (ignore) { /* 非 JSON 响应 */ }
        if (xhr.status === 401) {
          // silent401：用于登录页/注册页的登录态探测，不触发全局跳转
          if (!opts.silent401) {
            FL.redirectToLogin();
            message = '登录状态已失效，正在跳转登录页';
          } else {
            message = '尚未登录';
          }
        } else if (xhr.status === 403) {
          message = message || '该操作需要管理员权限';
        } else if (xhr.status === 404) {
          message = message || '请求的资源不存在';
        }
        reject(FL.error(message, xhr.status));
      });
    });
  };

  FL.get = function (path, options) { return FL.api('GET', path, null, options); };
  FL.post = function (path, body, options) { return FL.api('POST', path, body, options); };
  FL.put = function (path, body, options) { return FL.api('PUT', path, body, options); };
  FL.del = function (path, options) { return FL.api('DELETE', path, null, options); };

  /** 把对象拼成查询串（跳过空值） */
  FL.qs = function (params) {
    var parts = [];
    $.each(params || {}, function (key, value) {
      if (value !== null && value !== undefined && value !== '') {
        parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(value));
      }
    });
    return parts.length ? '?' + parts.join('&') : '';
  };

  /* ---------------- 登录态 ---------------- */

  FL.user = null;

  FL.loadUser = function () {
    return FL.get('/api/auth/me').then(function (user) {
      FL.user = user;
      return user;
    });
  };

  /**
   * 退出登录：销毁会话后回到公共首页（`/`，由 welcome-file 指向 index.html）。
   * 登录成功后仍进入研发工作台 dashboard.html，两者互相对应。
   */
  FL.logout = function () {
    return FL.post('/api/auth/logout').then(function () {
      location.href = FL.ctx + '/';
    });
  };

  /* ---------------- 主题 ---------------- */

  FL.applyTheme = function (theme) {
    document.documentElement.setAttribute('data-theme', theme === 'dark' ? 'dark' : 'light');
    try { localStorage.setItem('fl-theme', theme); } catch (ignore) { /* 隐私模式 */ }
  };

  FL.initTheme = function () {
    var saved = 'light';
    try { saved = localStorage.getItem('fl-theme') || 'light'; } catch (ignore) { /* 隐私模式 */ }
    FL.applyTheme(saved);
  };

  FL.toggleTheme = function () {
    var current = document.documentElement.getAttribute('data-theme');
    FL.applyTheme(current === 'dark' ? 'light' : 'dark');
  };

  /* ---------------- 应用外壳 ---------------- */

  var NAV = [
    { key: 'dashboard', text: '研发工作台', href: '/dashboard.html', icon: '◈' },
    { key: 'recipes', text: '配方库', href: '/recipes.html', icon: '☰' },
    { key: 'generate', text: '配方生成', href: '/recipe-generate.html', icon: '✦' },
    { key: 'recipe-edit', text: '新建配方', href: '/recipe-edit.html', icon: '＋' },
    { key: 'analysis', text: '风味分析', href: '/analysis-run.html', icon: '◑' },
    { key: 'history', text: '分析历史', href: '/analysis-history.html', icon: '◔' },
    { key: 'articles', text: '研发知识库', href: '/articles.html', icon: '❖' },
    { key: 'profile', text: '个人中心', href: '/profile.html', icon: '☺' }
  ];

  var ADMIN_NAV = [
    { key: 'admin-users', text: '用户管理', href: '/admin/users.html' },
    { key: 'admin-articles', text: '文章管理', href: '/admin/articles.html' },
    { key: 'admin-ingredients', text: '食材与规则', href: '/admin/ingredients.html' },
    { key: 'admin-tasks', text: '分析任务', href: '/admin/tasks.html' }
  ];

  /**
   * 渲染应用外壳。
   * @param {Object} options {active, title, subtitle, crumb, actions, content, adminPage}
   */
  FL.layout = function (options) {
    var opts = $.extend({
      active: '', title: '', subtitle: '', crumb: '', actions: '', content: '', adminPage: false
    }, options || {});
    var root = document.getElementById('app');
    if (!root) { return; }
    var isAdmin = !!(FL.user && (FL.user.isAdmin || FL.user.role === 'ADMIN'));
    var links = NAV.map(function (item) {
      return '<a class="side-link' + (item.key === opts.active ? ' active' : '') + '" href="' + FL.ctx + item.href + '">' +
        '<span>' + item.icon + '</span><span>' + item.text + '</span></a>';
    }).join('');
    if (isAdmin) {
      links += '<div class="workspace-label" style="padding-top:14px">系统管理</div>';
      links += ADMIN_NAV.map(function (item) {
        var key = item.key;
        var href = FL.ctx + item.href;
        var active = key === opts.active ? ' active' : '';
        return '<a class="side-link' + active + '" href="' + href + '"><span>⚙</span><span>' + item.text + '</span></a>';
      }).join('');
    }

    var themeIcon = document.documentElement.getAttribute('data-theme') === 'dark' ? '☾' : '☼';
    var html =
      '<div class="app-page">' +
      '  <header class="topbar">' +
      '    <nav class="nav">' +
      '      <a class="brand" href="' + FL.ctx + '/dashboard.html"><span class="brand-mark"></span><span>食之有理 <span style="font-weight:500;opacity:.72">FlavorLogic</span></span></a>' +
      '      <div class="nav-links">' +
      '        <a href="' + FL.ctx + '/dashboard.html">工作台</a>' +
      '        <a href="' + FL.ctx + '/recipes.html">配方库</a>' +
      '        <a href="' + FL.ctx + '/recipe-generate.html">配方生成</a>' +
      '        <a href="' + FL.ctx + '/analysis-history.html">分析历史</a>' +
      '        <a href="' + FL.ctx + '/articles.html">知识库</a>' +
      '      </div>' +
      '      <div class="nav-actions">' +
      '        <button class="icon-btn" type="button" data-action="theme" aria-label="切换主题">' + themeIcon + '</button>' +
      (FL.user ? '        <button class="text-btn" type="button" id="userMenuBtn">' + FL.escape(FL.user.nickname || FL.user.username) + ' ⌄</button>' : '') +
      '        <a class="primary-btn" href="' + FL.ctx + '/analysis-run.html">发起分析</a>' +
      '        <button class="mobile-menu" type="button" data-action="menu" aria-label="打开菜单">☰</button>' +
      '      </div>' +
      '    </nav>' +
      (FL.user ? '    <div id="userMenu" class="hidden" style="position:absolute;right:24px;top:64px;min-width:190px;padding:8px;border:1px solid var(--line);border-radius:10px;background:var(--surface);box-shadow:var(--shadow)">' +
        '<a class="side-link" href="' + FL.ctx + '/profile.html">个人中心</a>' +
        (isAdmin ? '<a class="side-link" href="' + FL.ctx + '/admin/users.html">管理后台</a>' : '') +
        '<button class="side-link" type="button" data-action="logout" style="width:100%;border:0;background:transparent;text-align:left">退出登录</button>' +
        '</div>' : '') +
      '  </header>' +
      '  <div class="app-layout">' +
      '    <aside class="app-sidebar">' +
      '      <div class="workspace-label">' + (opts.adminPage ? '管理后台' : '研发工作区') + '</div>' +
      '      <nav class="side-nav">' + links + '</nav>' +
      '    </aside>' +
      '    <div class="app-main">' +
      '      <div class="app-head">' +
      '        <div class="crumb">' + (opts.crumb || '<strong>' + FL.escape(opts.title) + '</strong>') + '</div>' +
      '        <div class="app-actions">' + (opts.actions || '') + '</div>' +
      '      </div>' +
      '      <div class="app-content">' +
      '        <div class="page-title"><div><h1>' + FL.escape(opts.title) + '</h1>' +
      (opts.subtitle ? '<p>' + FL.escape(opts.subtitle) + '</p>' : '') + '</div></div>' +
      '        <div id="pageBody">' + opts.content + '</div>' +
      '      </div>' +
      '    </div>' +
      '  </div>' +
      '</div>';
    root.innerHTML = html;

    // 主题切换 / 菜单 / 退出
    $(root).on('click', '[data-action="theme"]', function () {
      FL.toggleTheme();
      $(this).text(document.documentElement.getAttribute('data-theme') === 'dark' ? '☾' : '☼');
    });
    $(root).on('click', '[data-action="menu"]', function () {
      $('.nav-links').toggleClass('open');
    });
    $(root).on('click', '#userMenuBtn', function (event) {
      event.stopPropagation();
      $('#userMenu').toggleClass('hidden');
    });
    $(document).on('click', function () { $('#userMenu').addClass('hidden'); });
    $(root).on('click', '[data-action="logout"]', function () {
      FL.logout();
    });
    return document.getElementById('pageBody');
  };

  /* ---------------- 通用组件 ---------------- */

  FL.statusTag = function (text, cls) {
    return '<span class="status ' + (cls || '') + '">' + FL.escape(text) + '</span>';
  };

  FL.empty = function (title, hint) {
    return '<div class="empty"><strong>' + FL.escape(title || '暂无数据') + '</strong>' +
      FL.escape(hint || '换个筛选条件试试，或先创建一条数据。') + '</div>';
  };

  FL.loading = function (text) {
    return '<div class="loading">' + FL.escape(text || '加载中…') + '</div>';
  };

  /**
   * 渲染分页器。
   * @param {Object} page PageResult（含 page/pageSize/total/totalPages）
   * @param {String} handlerName 全局函数名，点击时调用 handlerName(page)
   */
  FL.pager = function (page, handlerName) {
    if (!page) { return ''; }
    var total = page.total || 0;
    var current = page.page || 1;
    var totalPages = page.totalPages || 0;
    var html = '<div class="pager"><span>共 ' + total + ' 条记录 · 第 ' + current + ' / ' + Math.max(totalPages, 1) + ' 页</span><div class="pager-btns">';
    html += '<button type="button" ' + (current <= 1 ? 'disabled' : '') + ' onclick="' + handlerName + '(' + (current - 1) + ')">上一页</button>';
    var start = Math.max(1, current - 2);
    var end = Math.min(Math.max(totalPages, 1), start + 4);
    for (var i = start; i <= end; i++) {
      html += '<button type="button" class="' + (i === current ? 'active' : '') + '" onclick="' + handlerName + '(' + i + ')">' + i + '</button>';
    }
    html += '<button type="button" ' + (current >= totalPages ? 'disabled' : '') + ' onclick="' + handlerName + '(' + (current + 1) + ')">下一页</button>';
    html += '</div></div>';
    return html;
  };

  /** 启动页面：先取登录态，再渲染外壳 */
  FL.start = function (options, render) {
    FL.initTheme();
    FL.loadUser().then(function (user) {
      render(user);
    })['catch'](function (err) {
      if (err && (err.bizCode === 401 || err.bizCode === 403)) {
        FL.redirectToLogin();
        return;
      }
      FL.toast(err.message || '加载失败', 'error', '页面初始化失败');
    });
  };

  /**
   * 启动「游客可见」页面（首页、知识库列表与详情）。
   * 未登录时以游客身份渲染，不跳转登录页；已登录时正常带出用户菜单。
   */
  FL.startPublic = function (options, render) {
    FL.initTheme();
    FL.loadUser().then(function (user) {
      render(user);
    })['catch'](function () {
      FL.user = null;
      render(null);
    });
  };

  /**
   * 顶部导航的悬停信息面板（首页 / 产品说明页共用）。
   *
   * 展开本身由 CSS 的 :hover 与 :focus-within 完成，这里只补三件 CSS 做不到的事：
   * 1) 触屏等没有 hover 的设备点击标题可切换展开（.open），并同步 aria-expanded；
   * 2) 点击面板外部、按 Esc、鼠标移出后收起，避免面板“关不掉”；
   * 3) 同一时间只展开一个面板。
   */
  FL.navPanels = function () {
    var $items = $('.nav-links .nav-item');
    if (!$items.length) {
      return;
    }
    var closeAll = function () {
      $items.removeClass('open').children('.nav-trigger').attr('aria-expanded', 'false');
    };

    $items.each(function () {
      var $item = $(this);
      $item.children('.nav-trigger').on('click', function (event) {
        event.preventDefault();
        // 阻止冒泡：否则会立刻触发下面的 document 点击处理器，面板刚开就被关掉
        event.stopPropagation();
        var willOpen = !$item.hasClass('open');
        closeAll();
        if (willOpen) {
          $item.addClass('open').children('.nav-trigger').attr('aria-expanded', 'true');
        }
      });
      // 鼠标移出后清掉点击留下的 .open，保证与 :hover 状态一致
      $item.on('mouseleave', closeAll);
    });

    $(document).on('click', closeAll);
    $(document).on('keydown', function (event) {
      if (event.key === 'Escape' || event.keyCode === 27) {
        closeAll();
      }
    });
  };

  // 页面就绪后自动生效：带信息面板的页面不需要各自再调用一次
  $(function () { FL.navPanels(); });

  /**
   * 为表单中的每个密码框添加「显示 / 隐藏密码」按钮。
   *
   * 按钮**常驻显示**（不依赖 hover 或 focus），点击在明文与密文之间切换；
   * 表单重置时自动回到密文状态。注册、登录、修改密码三个表单统一使用。
   *
   * @param {Object} $form 表单
   */
  FL.bindPasswordToggles = function ($form) {
    var EYE_ICON = '<svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor"' +
      ' stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<path d="M1.8 12S5.6 5.5 12 5.5 22.2 12 22.2 12 18.4 18.5 12 18.5 1.8 12 1.8 12Z"/>' +
      '<circle cx="12" cy="12" r="3.2"/>' +
      '<path class="slash" d="M3.6 3.6 20.4 20.4"/></svg>';

    $form.find('input[type="password"]').each(function () {
      var $input = $(this);
      // 避免重复包装（表单被重复渲染时）
      if ($input.parent().hasClass('pwd-wrap')) {
        return;
      }
      $input.wrap('<div class="pwd-wrap"></div>');
      $input.after('<button type="button" class="pwd-toggle" aria-label="显示密码" title="显示密码"' +
        ' aria-pressed="false">' + EYE_ICON + '</button>');

      $input.next('.pwd-toggle').on('click', function () {
        var $toggle = $(this);
        var toPlain = $input.attr('type') === 'password';
        $input.attr('type', toPlain ? 'text' : 'password');
        $toggle.toggleClass('is-visible', toPlain)
          .attr('aria-label', toPlain ? '隐藏密码' : '显示密码')
          .attr('title', toPlain ? '隐藏密码' : '显示密码')
          .attr('aria-pressed', toPlain ? 'true' : 'false');
      });
    });

    // 表单重置后回到密文状态，避免「看不见的明文框」
    $form.on('reset', function () {
      $form.find('.pwd-wrap > input').attr('type', 'password');
      $form.find('.pwd-toggle').removeClass('is-visible')
        .attr('aria-label', '显示密码').attr('title', '显示密码').attr('aria-pressed', 'false');
    });
  };

  /** 表单校验：为字段设置错误文案（就近显示在字段下方） */
  FL.setFieldError = function (selector, message) {
    var $field = $(selector).closest('.field');
    $field.addClass('has-error').removeClass('is-valid');
    $field.find('.error').text(message || '');
  };

  /** 清除单个字段的错误提示 */
  FL.clearFieldError = function (selector) {
    var $field = $(selector).closest('.field');
    $field.removeClass('has-error');
    $field.find('.error').text('');
  };

  FL.clearFieldErrors = function ($form) {
    $form.find('.field').removeClass('has-error').find('.error').text('');
  };

  /**
   * 校验单个字段。
   *
   * @param {Object} rule 规则：{name:'#sel', label:'字段名', required, min, max, pattern, message, validator}
   * @param {Object} $form 表单
   * @return {String} 错误文案，通过校验返回空串
   */
  FL.checkField = function (rule, $form) {
    var value = $.trim($form.find(rule.name).val() || '');
    if (rule.required && !value) {
      return rule.label + '不能为空';
    }
    if (value) {
      if (rule.min && value.length < rule.min) {
        return rule.message || (rule.label + '长度不能少于 ' + rule.min + ' 个字符');
      }
      if (rule.max && value.length > rule.max) {
        return rule.message || (rule.label + '长度不能超过 ' + rule.max + ' 个字符');
      }
      if (rule.pattern && !rule.pattern.test(value)) {
        return rule.message || (rule.label + '格式不正确');
      }
      if (typeof rule.validator === 'function') {
        return rule.validator(value, $form) || '';
      }
    }
    return '';
  };

  /**
   * 表单校验：一次性检查全部字段，把红色提示就近标在**每一个**不合格字段下方，
   * 并聚焦第一个不合格字段（而不是只提示第一个就返回）。
   */
  FL.validate = function ($form, rules) {
    FL.clearFieldErrors($form);
    var $firstInvalid = null;
    (rules || []).forEach(function (rule) {
      var message = FL.checkField(rule, $form);
      if (message) {
        var $input = $form.find(rule.name);
        FL.setFieldError($input, message);
        if (!$firstInvalid) {
          $firstInvalid = $input;
        }
      }
    });
    if ($firstInvalid) {
      try { $firstInvalid.focus(); } catch (ignore) { /* 某些浏览器下不可聚焦，忽略 */ }
    }
    return !$firstInvalid;
  };

  /**
   * 绑定字段交互（登录 / 注册 / 修改密码表单统一使用）：
   *   1) 字段填写后隐藏标签右侧的必填红星（.req），清空后重新出现；
   *   2) 再次输入时立即清除该字段的旧错误提示；
   *   3) 失焦时即时校验格式（字段为空时不提示「不能为空」，留给提交时统一提示）。
   *
   * @param {Object} $form 表单
   * @param {Array}  rules 与 FL.validate 相同的规则数组
   */
  FL.bindFields = function ($form, rules) {
    var ruleMap = {};
    (rules || []).forEach(function (rule) {
      ruleMap[rule.name] = rule;
    });

    $form.find('input, select, textarea').each(function () {
      var $input = $(this);
      var $field = $input.closest('.field');
      if (!$field.length) {
        return;
      }
      var selector = '#' + ($input.attr('id') || '');
      var syncMarker = function () {
        var filled = $.trim($input.val() || '') !== '';
        $field.toggleClass('is-filled', filled);
        if (filled) {
          FL.clearFieldError($input);
        }
      };
      syncMarker();
      $input.on('input change', syncMarker);
      $input.on('blur', function () {
        var rule = ruleMap[selector];
        if (!rule || $.trim($input.val() || '') === '') {
          return;
        }
        var message = FL.checkField(rule, $form);
        if (message) {
          FL.setFieldError($input, message);
        } else {
          FL.clearFieldError($input);
        }
      });
    });
  };

  window.FL = FL;

  /**
   * 前端版本标记：用于确认浏览器到底加载了哪一版静态资源。
   * 打开浏览器 F12 → Console，应能看到这一行；版本号不是最新的就说明仍是缓存文件，
   * 用 Ctrl+F5 强制刷新，或在地址栏给页面加一个没见过的参数（如 dashboard.html?t=1）。
   */
  FL.VERSION = '2026-09-16.3';
  if (window.console && window.console.info) {
    window.console.info('%c食之有理 FlavorLogic 前端 ' + FL.VERSION,
      'color:#157a56;font-weight:600', '（版本号不是最新时请强制刷新）');
  }
}(window, window.jQuery));
