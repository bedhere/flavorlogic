/* ============================================================
   管理后台 · 用户管理
   ------------------------------------------------------------
   接口：GET  /api/admin/users?keyword=&role=&status=&page=&pageSize=
        PUT  /api/admin/users/{id}/role   body {role}
        PUT  /api/admin/users/{id}/status body {status}
   说明：页面不展示任何密码字段（后端也不返回密码哈希）；
        禁用账号与调整角色属于敏感操作，均需二次确认。
   ============================================================ */
(function ($, FL) {
  'use strict';

  /** 列表查询条件（翻页时复用） */
  var query = { keyword: '', role: '', status: '', page: 1, pageSize: 10 };

  FL.start({}, function () {
    FL.layout({
      active: 'admin-users',
      title: '用户管理',
      subtitle: '查看平台注册账号，调整角色与启用状态；账号只做禁用，不做物理删除',
      crumb: '管理后台 / <strong>用户管理</strong>',
      adminPage: true,
      content: renderShell()
    });
    bindToolbar();
    load();
  });

  /* ---------------- 页面外壳 ---------------- */

  /**
   * 渲染「页面外壳」：筛选工具栏（关键字 / 角色 / 状态 / 查询 / 重置）。
   *
   * 本页原先只渲染了 FL.loading 占位，工具栏各控件在 HTML 与 JS 中都不存在，
   * bindToolbar() 的绑定全部落在空集合上，筛选栏与查询按钮不会出现。
   * 这里补齐缺失的控件，既有的 load / render / 事件逻辑保持不变。
   *
   * 列表数据写入 #userListRegion（不再整体覆盖 #pageBody），本函数只在容器缺失时补渲染一次，
   * 因此工具栏会一直留在页面上，bindToolbar() 的一次性事件绑定也不会因为元素被替换而失效。
   *
   * @returns {String} 工具栏与列表容器的 HTML
   */
  function renderShell() {
    var html =
      '<div class="toolbar">' +
      '  <label class="search" for="userKeyword"><span aria-hidden="true">⌕</span>' +
      '    <input type="search" id="userKeyword" placeholder="搜索用户名或昵称" autocomplete="off" aria-label="搜索用户名或昵称" /></label>' +
      '  <div style="display:flex;flex-wrap:wrap;align-items:center;gap:10px">' +
      '    <div class="field" style="min-width:150px">' +
      '      <select id="userRole" aria-label="账号角色">' +
      '        <option value="">全部角色</option>' +
      '        <option value="USER">普通用户</option>' +
      '        <option value="ADMIN">管理员</option>' +
      '      </select></div>' +
      '    <div class="field" style="min-width:130px">' +
      '      <select id="userStatus" aria-label="账号状态">' +
      '        <option value="">全部状态</option>' +
      '        <option value="1">正常</option>' +
      '        <option value="0">已禁用</option>' +
      '      </select></div>' +
      '    <button class="primary-btn" type="button" id="userSearchBtn">查询</button>' +
      '    <button class="secondary-btn" type="button" id="userResetBtn">重置</button>' +
      '  </div>' +
      '</div>' +
      '<div id="userListRegion">' + FL.loading('正在加载用户列表…') + '</div>';

    var body = $('#pageBody');
    // 首次渲染时 #pageBody 还不存在，直接把 HTML 交给 FL.layout 作为 content；
    // 之后只在列表容器丢失时补渲染——不要重复覆盖，否则会摧毁 bindToolbar 的一次性事件绑定
    if (!body.length) {
      return html;
    }
    if (!body.find('#userListRegion').length) {
      body.html(html);
    }
    return html;
  }

  /* ---------------- 工具栏 ---------------- */

  function bindToolbar() {
    $('#userSearchBtn').on('click', function () {
      collect();
      query.page = 1;
      load();
    });
    $('#userResetBtn').on('click', function () {
      $('#userKeyword').val('');
      $('#userRole').val('');
      $('#userStatus').val('');
      collect();
      query.page = 1;
      load();
    });
    // 回车即查询，减少鼠标操作
    $('#userKeyword').on('keydown', function (event) {
      if (event.key === 'Enter') {
        event.preventDefault();
        $('#userSearchBtn').trigger('click');
      }
    });
  }

  /** 把界面上的筛选条件读进 query（0 等合法筛选值不能被当成空值丢掉） */
  function collect() {
    query.keyword = $.trim($('#userKeyword').val() || '');
    query.role = filterValue($('#userRole').val());
    query.status = filterValue($('#userStatus').val());
  }

  /** 下拉值归一化：空字符串按「全部」处理，其余原样传给 FL.qs */
  function filterValue(value) {
    if (value === null || value === undefined || value === '') { return ''; }
    return String(value);
  }

  /* ---------------- 列表 ---------------- */

  function load() {
    // 外壳只在缺失时补渲染（幂等），列表数据写进专用容器，避免覆盖工具栏导致事件绑定失效
    renderShell();
    $('#userListRegion').html(FL.loading('正在加载用户列表…'));
    FL.get('/api/admin/users' + FL.qs(query)).then(render)['catch'](function (err) {
      $('#userListRegion').html(FL.empty('用户列表加载失败', err.message || '请稍后重试。'));
    });
  }

  function render(data) {
    var list = (data && data.list) || [];
    var rows = list.map(row).join('');
    var body = rows
      ? '<div class="table-wrap"><table><thead><tr>' +
        '<th>ID</th><th>用户名</th><th>昵称</th><th>角色</th><th>状态</th>' +
        '<th>最近登录</th><th>注册时间</th><th>操作</th>' +
        '</tr></thead><tbody>' + rows + '</tbody></table></div>' + FL.pager(data, 'goPage')
      : FL.empty('没有匹配的用户', '换个关键字或清空筛选条件再试。');
    $('#userListRegion').html(
      '<section class="panel">' +
      '  <div class="panel-head"><h3>账号列表</h3><div class="tag">共 ' + FL.escape((data && data.total) || 0) + ' 个账号</div></div>' +
      body +
      '</section>' +
      '<div class="notice" style="margin-top:18px">安全提示：接口不会返回密码哈希，后台也不提供密码查看与重置入口；' +
      '为避免误操作，系统禁止管理员禁用自己或取消自己的管理员身份，此类请求会被服务端拒绝并在页面提示原因。</div>'
    );
  }

  function row(user) {
    var isAdmin = user.role === 'ADMIN';
    var enabled = Number(user.status) === 1;
    return '<tr>' +
      '<td>' + FL.escape(user.id) + '</td>' +
      '<td><strong>' + FL.escape(user.username) + '</strong></td>' +
      '<td>' + (user.nickname ? FL.escape(user.nickname) : '<span style="color:var(--muted)">—</span>') + '</td>' +
      '<td>' + FL.statusTag(isAdmin ? '管理员' : '普通用户', isAdmin ? 'info' : 'muted') + '</td>' +
      '<td>' + FL.statusTag(enabled ? '正常' : '已禁用', enabled ? '' : 'danger') + '</td>' +
      '<td>' + FL.escape(FL.fmtDate(user.lastLoginAt)) + '</td>' +
      '<td>' + FL.escape(FL.fmtDate(user.createdAt)) + '</td>' +
      '<td><div class="row-actions">' +
      '<button type="button" class="link-btn" data-action="role" data-id="' + FL.escape(user.id) + '"' +
      ' data-role="' + FL.escape(user.role) + '" data-name="' + FL.escape(user.nickname || user.username) + '">' +
      (isAdmin ? '设为普通用户' : '设为管理员') + '</button>' +
      '<button type="button" class="link-btn' + (enabled ? ' danger' : '') + '" data-action="status"' +
      ' data-id="' + FL.escape(user.id) + '" data-status="' + (enabled ? 1 : 0) + '"' +
      ' data-name="' + FL.escape(user.nickname || user.username) + '">' +
      (enabled ? '禁用' : '启用') + '</button>' +
      '</div></td></tr>';
  }

  /* ---------------- 行内操作 ---------------- */

  $(document).on('click', '#pageBody [data-action]', function () {
    var $btn = $(this);
    var name = $btn.attr('data-name') || '该用户';
    var id = $btn.attr('data-id');
    if ($btn.attr('data-action') === 'role') {
      changeRole($btn, id, name);
    } else {
      changeStatus($btn, id, name);
    }
  });

  function changeRole($btn, id, name) {
    var isAdmin = $btn.attr('data-role') === 'ADMIN';
    var target = isAdmin ? 'USER' : 'ADMIN';
    var text = isAdmin ? ('将「' + name + '」调整为普通用户？该账号将失去管理后台权限。')
      : ('将「' + name + '」设为管理员？该账号将获得用户、文章、食材与任务的管理权限。');
    FL.confirm(text, { title: '调整用户角色', okText: '确认调整' }).then(function (ok) {
      if (!ok) { return; }
      return guard($btn, function () {
        return FL.put('/api/admin/users/' + id + '/role', { role: target });
      }, '角色已更新').then(load);
    })['catch'](function (err) {
      // 例如：不允许取消自己的管理员身份 → 直接把服务端说明展示出来
      FL.toast(err.message || '角色调整失败', 'error', '操作未完成');
    });
  }

  function changeStatus($btn, id, name) {
    var enabled = Number($btn.attr('data-status')) === 1;
    var target = enabled ? 0 : 1;
    var text = enabled
      ? ('禁用「' + name + '」？禁用后该账号无法登录，历史配方与分析记录仍会保留。')
      : ('启用「' + name + '」？该账号将恢复登录与提交分析的权限。');
    FL.confirm(text, { title: enabled ? '禁用账号' : '启用账号', okText: enabled ? '确认禁用' : '确认启用', danger: enabled })
      .then(function (ok) {
        if (!ok) { return; }
        return guard($btn, function () {
          return FL.put('/api/admin/users/' + id + '/status', { status: target });
        }, enabled ? '账号已禁用' : '账号已启用').then(load);
      })['catch'](function (err) {
        FL.toast(err.message || '状态修改失败', 'error', '操作未完成');
      });
  }

  /**
   * 提交期间禁用按钮，避免重复点击；成功后提示，失败向上抛出由调用方提示。
   *
   * @param {Object} $btn    触发按钮
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
    load();
  };
}(window.jQuery, window.FL));
