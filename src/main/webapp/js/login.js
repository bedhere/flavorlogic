/* 登录页交互：表单校验 + 防重复提交 + 登录后回跳 */
(function ($, FL) {
  'use strict';

  FL.initTheme();

  var REMEMBER_KEY = 'fl-remember-username';

  // 登录只校验必填，不限制用户名与密码格式（历史账号与各种命名都能登录）
  var RULES = [
    { name: '#username', label: '用户名', required: true },
    { name: '#password', label: '密码', required: true }
  ];

  // 注册成功后跳回登录页时会带上 registered=<用户名>：自动填好用户名，光标落在密码框
  var registeredUser = FL.query('registered');

  // 记住用户名
  try {
    var saved = localStorage.getItem(REMEMBER_KEY);
    if (saved) {
      $('#username').val(saved);
      $('#remember').prop('checked', true);
      $('#password').focus();
    }
  } catch (ignore) { /* 隐私模式 */ }

  if (registeredUser) {
    $('#username').val(registeredUser);
  }

  // 密码框常驻「显示密码」按钮 + 必填标记与即时校验（记住的用户名会先把必填红星隐藏掉）
  FL.bindPasswordToggles($('#loginForm'));
  FL.bindFields($('#loginForm'), RULES);

  if (registeredUser) {
    FL.toast('账号「' + registeredUser + '」注册成功，请输入密码登录', 'ok', '注册成功');
    $('#password').focus();
  }

  // 若已登录，直接进入工作台；silent401 让未登录的 401 只作为探测结果，不触发全局跳转
  FL.get('/api/auth/me', { silent401: true }).then(function () {
    location.href = FL.ctx + '/dashboard.html';
  })['catch'](function () { /* 未登录属正常 */ });

  $('#loginForm').on('submit', function (event) {
    event.preventDefault();
    var $form = $(this);
    if (!FL.validate($form, RULES)) {
      FL.toast('请填写用户名与密码', 'warn', '信息不完整');
      return;
    }

    var username = $.trim($('#username').val());
    var $btn = $('#loginBtn').prop('disabled', true).text('登录中…');

    FL.post('/api/auth/login', { username: username, password: $('#password').val() })
      .then(function (user) {
        try {
          if ($('#remember').prop('checked')) {
            localStorage.setItem(REMEMBER_KEY, username);
          } else {
            localStorage.removeItem(REMEMBER_KEY);
          }
        } catch (ignore) { /* 隐私模式 */ }
        FL.toast('欢迎回来，' + (user.nickname || user.username), 'ok', '登录成功');
        var redirect = FL.query('redirect');
        // 防御：仅接受站内绝对路径，且忽略指向登录/注册页的嵌套回跳地址，避免再次回到登录页
        if (!redirect || redirect.indexOf('/') !== 0 || /^\/(login|register)\.html/.test(redirect)) {
          redirect = '/dashboard.html';
        }
        location.href = FL.ctx + redirect;
      })
      ['catch'](function (err) {
        $btn.prop('disabled', false).text('登录');
        FL.setFieldError('#password', err.message);
        FL.toast(err.message, 'error', '登录失败');
      });
  });
}(window.jQuery, window.FL));
