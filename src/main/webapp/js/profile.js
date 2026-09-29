/* ============================================================
   个人中心：账号资料（只读）+ 修改密码 + 最近研发活动
   ------------------------------------------------------------
   接口：GET /api/auth/me（FL.start 已获取，用于资料区）
        GET /api/dashboard（统计与最近任务 / 配方）
        POST /api/auth/password（修改密码）
   说明：项目未开放昵称修改接口，资料区只读展示，改由管理员在后台维护。
   ============================================================ */
(function ($, FL) {
  'use strict';

  FL.start({}, function (user) {
    FL.layout({
      active: 'profile',
      title: '个人中心',
      subtitle: '查看账号资料、维护登录密码，并回顾最近的配方与分析任务',
      crumb: '研发工作区 / <strong>个人中心</strong>',
      actions: '<a class="secondary-btn" href="' + FL.ctx + '/dashboard.html">返回工作台</a>',
      content: shell(user)
    });
    bindPasswordForm();
    loadActivity();
  });

  /** 页面骨架：统计区 + 资料 / 密码两栏 + 最近活动两栏（数据异步填充） */
  function shell(user) {
    return '<div id="statRegion">' + FL.loading('正在加载统计数据…') + '</div>' +
      '<div class="app-grid" style="margin-top:18px">' + profilePanel(user) + passwordPanel() + '</div>' +
      '<div class="app-grid" style="margin-top:18px">' + taskPanel() + recipePanel() + '</div>';
  }

  /* ---------------- 基本资料（只读） ---------------- */

  function profilePanel(user) {
    var roleText = roleTextOf(user);
    return '<section class="panel">' +
      '<div class="panel-head"><h3>基本资料</h3><div><span class="tag">' + FL.escape(roleText) + '</span></div></div>' +
      '<div class="panel-body">' +
      '<div class="form-grid">' +
      readOnlyField('用户名', 'username', user.username || '—') +
      readOnlyField('昵称', 'nickname', user.nickname || '未设置') +
      '<div class="field span-2"><label for="role">账号角色</label>' +
      '<input type="text" id="role" value="' + FL.escape(roleText) + '" disabled ' +
      'style="background:var(--surface-2);color:var(--muted);cursor:not-allowed" />' +
      '<div class="hint">账号角色决定可访问的功能范围</div></div>' +
      '</div>' +
      '<div class="notice" style="margin-top:16px">昵称与账号信息由管理员在后台维护，如需调整请联系平台管理员；登录密码可在右侧自行修改。</div>' +
      '</div></section>';
  }

  /** 只读字段：disabled 输入框 + 说明 */
  function readOnlyField(label, id, value) {
    return '<div class="field"><label for="' + id + '">' + FL.escape(label) + '</label>' +
      '<input type="text" id="' + id + '" value="' + FL.escape(value) + '" disabled ' +
      'style="background:var(--surface-2);color:var(--muted);cursor:not-allowed" />' +
      '<div class="hint">只读字段，由系统维护</div></div>';
  }

  /** 角色中文名 */
  function roleTextOf(user) {
    return (user && (user.isAdmin || user.role === 'ADMIN')) ? '管理员' : '普通研发用户';
  }

  /* ---------------- 修改密码 ---------------- */

  /** 修改密码校验规则（与后端 ValidationUtil.requirePassword 保持一致：6-20 位） */
  var PASSWORD_RULES = [
    { name: '#oldPassword', label: '原密码', required: true },
    {
      name: '#newPassword', label: '新密码', required: true, min: 6, max: 20,
      message: '新密码长度必须在 6-20 位之间',
      validator: function (value, $form) {
        return value === $form.find('#oldPassword').val() ? '新密码不能与原密码相同' : '';
      }
    },
    {
      name: '#confirmPassword', label: '确认新密码', required: true,
      validator: function (value, $form) {
        return value === $form.find('#newPassword').val() ? '' : '两次输入的新密码不一致';
      }
    }
  ];

  function passwordPanel() {
    return '<section class="panel">' +
      '<div class="panel-head"><h3>修改密码</h3></div>' +
      '<div class="panel-body">' +
      '<form id="passwordForm" novalidate>' +
      '<div class="form-grid one">' +
      passwordField('原密码', 'oldPassword', 'current-password', '请输入当前登录密码') +
      passwordField('新密码', 'newPassword', 'new-password', '6-20 位，建议字母与数字组合') +
      passwordField('确认新密码', 'confirmPassword', 'new-password', '请再次输入新密码') +
      '</div>' +
      '<div class="form-actions">' +
      '<button class="secondary-btn" type="reset" id="resetPwdBtn">清空</button>' +
      '<button class="primary-btn" type="submit" id="passwordBtn">保存新密码</button>' +
      '</div></form>' +
      '</div></section>';
  }

  function passwordField(label, id, autocomplete, placeholder) {
    return '<div class="field">' +
      '<label for="' + id + '">' + FL.escape(label) + ' <span class="req">*</span></label>' +
      '<input type="password" id="' + id + '" autocomplete="' + autocomplete + '" placeholder="' + FL.escape(placeholder) + '" />' +
      '<div class="error"></div></div>';
  }

  /** 修改密码：前端先做长度 / 一致性 / 新旧不同校验，再提交给后端复核 */
  function bindPasswordForm() {
    // 密码框常驻「显示密码」按钮
    FL.bindPasswordToggles($('#passwordForm'));
    // 必填红星随填写状态隐藏，输入时清除旧错误
    FL.bindFields($('#passwordForm'), PASSWORD_RULES);

    $('#passwordForm').on('reset', function () {
      FL.clearFieldErrors($(this));
      $(this).find('.field').removeClass('is-filled');
    });

    $('#passwordForm').on('submit', function (event) {
      event.preventDefault();
      var $form = $(this);
      if (!FL.validate($form, PASSWORD_RULES)) {
        return;
      }

      var oldPassword = $('#oldPassword').val();
      var newPassword = $('#newPassword').val();
      var confirmPassword = $('#confirmPassword').val();

      var $btn = $('#passwordBtn').prop('disabled', true).text('提交中…');
      FL.post('/api/auth/password', {
        oldPassword: oldPassword,
        newPassword: newPassword,
        confirmPassword: confirmPassword
      }).then(function () {
        $btn.prop('disabled', false).text('保存新密码');
        $form[0].reset();
        FL.clearFieldErrors($form);
        // 重置后必填红星要重新出现
        $form.find('.field').removeClass('is-filled');
        FL.toast('新密码已生效，下次登录请使用新密码', 'ok', '密码修改成功');
      })['catch'](function (err) {
        $btn.prop('disabled', false).text('保存新密码');
        var message = (err && err.message) || '密码修改失败，请稍后重试';
        // 后端提示按字段归位：原密码 / 两次不一致 / 新密码
        if (message.indexOf('原密码') >= 0) {
          FL.setFieldError('#oldPassword', message);
        } else if (message.indexOf('不一致') >= 0) {
          FL.setFieldError('#confirmPassword', message);
        } else if (message.indexOf('新密码') >= 0) {
          FL.setFieldError('#newPassword', message);
        } else {
          FL.setFieldError('#oldPassword', message);
        }
        FL.toast(message, 'error', '密码未修改');
      });
    });
  }

  /* ---------------- 统计与最近活动 ---------------- */

  /** 拉取工作台汇总数据，填充统计卡片与两张活动表 */
  function loadActivity() {
    FL.get('/api/dashboard').then(function (data) {
      var overview = data || {};
      renderStats(overview);
      renderTasks(overview.recentTasks || []);
      renderRecipes(overview.recentRecipes || []);
    })['catch'](function (err) {
      var message = (err && err.message) || '请稍后重试。';
      $('#statRegion').html(FL.empty('统计与活动数据加载失败', message) +
        '<div style="padding-bottom:40px;text-align:center">' +
        '<button class="secondary-btn" type="button" id="retryBtn">重新加载</button></div>');
      $('#taskBody').html(FL.empty('分析任务加载失败', message));
      $('#recipeBody').html(FL.empty('配方记录加载失败', message));
      $('#retryBtn').on('click', loadActivity);
    });
  }

  /** 三个统计卡片：配方数、任务数、已完成分析数 */
  function renderStats(data) {
    $('#statRegion').html('<div class="stat-row" style="grid-template-columns:repeat(3,minmax(0,1fr))">' +
      statCard('我的配方', data.recipeCount, '已保存的基准配方数量') +
      statCard('分析任务', data.taskCount, '累计创建的风味分析任务') +
      statCard('已完成分析', data.completedCount, '可直接回看结果与补偿建议') +
      '</div>');
  }

  function statCard(label, value, hint) {
    return '<div class="stat-card accent">' +
      '<span class="label">' + FL.escape(label) + '</span>' +
      '<span class="value">' + FL.escape(value === null || value === undefined ? 0 : value) + '</span>' +
      '<span class="delta">' + FL.escape(hint) + '</span></div>';
  }

  function taskPanel() {
    return '<section class="panel">' +
      '<div class="panel-head"><h3>最近分析任务</h3><div>' +
      '<a class="link-btn" href="' + FL.ctx + '/analysis-history.html">全部记录 →</a></div></div>' +
      '<div id="taskBody">' + FL.loading('正在加载分析任务…') + '</div></section>';
  }

  function recipePanel() {
    return '<section class="panel">' +
      '<div class="panel-head"><h3>最近配方</h3><div>' +
      '<a class="link-btn" href="' + FL.ctx + '/recipes.html">配方库 →</a></div></div>' +
      '<div id="recipeBody">' + FL.loading('正在加载配方…') + '</div></section>';
  }

  /** 最近分析任务：任务名（跳到结果页）+ 状态 + 创建时间 */
  function renderTasks(tasks) {
    var rows = (tasks || []).map(function (task) {
      var status = FL.TASK_STATUS[task.status] || { text: task.status || '未知状态', cls: 'muted' };
      var sub = FL.goalText(task.goalType) + (task.recipeName ? ' · ' + task.recipeName : '');
      return '<tr>' +
        '<td><a class="link-btn" href="' + FL.ctx + '/analysis-result.html?taskId=' + encodeURIComponent(task.id) + '">' +
        FL.escape(task.taskName) + '</a><br /><span style="color:var(--muted)">' + FL.escape(sub) + '</span></td>' +
        '<td>' + FL.statusTag(status.text, status.cls) + '</td>' +
        '<td>' + FL.escape(FL.fmtDate(task.createdAt)) + '</td>' +
        '</tr>';
    }).join('');

    $('#taskBody').html(rows
      ? '<div class="table-wrap"><table><thead><tr><th>任务</th><th>状态</th><th>创建时间</th></tr></thead><tbody>' + rows + '</tbody></table></div>'
      : FL.empty('还没有分析任务', '从「新建配方与分析」开始你的第一次风味推演。'));
  }

  /** 最近配方：配方名（跳到编辑页）+ 配料数 + 最近修改时间 */
  function renderRecipes(recipes) {
    var rows = (recipes || []).map(function (recipe) {
      var itemText = recipe.itemCount === null || recipe.itemCount === undefined
        ? '配料未录入' : recipe.itemCount + ' 项配料';
      return '<tr>' +
        '<td><a class="link-btn" href="' + FL.ctx + '/recipe-edit.html?id=' + encodeURIComponent(recipe.id) + '">' +
        FL.escape(recipe.name) + '</a><br /><span style="color:var(--muted)">' + FL.escape(itemText) + '</span></td>' +
        '<td>' + FL.escape(FL.fmtDate(recipe.updatedAt)) + '</td>' +
        '</tr>';
    }).join('');

    $('#recipeBody').html(rows
      ? '<div class="table-wrap"><table><thead><tr><th>配方</th><th>最近修改</th></tr></thead><tbody>' + rows + '</tbody></table></div>'
      : FL.empty('还没有配方', '先录入一份基准配方，系统才能计算风味偏移。'));
  }
}(window.jQuery, window.FL));
