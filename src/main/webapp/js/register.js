/* ============================================================
   注册页交互
   ------------------------------------------------------------
   校验规则（与后端 ValidationUtil 保持一致）：
     · 用户名：中文 / 字母 / 数字 / 下划线，2-20 位（支持汉字姓名）
     · 昵称  ：必填，不超过 50 字
     · 密码  ：6-20 位，两次输入必须一致
   交互：字段填写后隐藏必填红星；提交时一次性标出所有不合格字段。
   ============================================================ */
(function ($, FL) {
  'use strict';

  FL.initTheme();

  /** 允许中文、字母、数字与下划线 */
  var USERNAME_PATTERN = /^[\u4e00-\u9fa5A-Za-z0-9_]{2,20}$/;

  var RULES = [
    {
      name: '#username', label: '用户名', required: true, pattern: USERNAME_PATTERN,
      message: '用户名只能包含中文、字母、数字和下划线，长度 2-20 位'
    },
    { name: '#nickname', label: '昵称', required: true, max: 50 },
    {
      name: '#password', label: '密码', required: true, min: 6, max: 20,
      message: '密码长度必须在 6-20 位之间'
    },
    {
      name: '#confirmPassword', label: '确认密码', required: true,
      validator: function (value, $form) {
        return value === $form.find('#password').val() ? '' : '两次输入的密码不一致';
      }
    }
  ];

  // 已登录则直接进入工作台
  FL.get('/api/auth/me', { silent401: true }).then(function () {
    location.href = FL.ctx + '/dashboard.html';
  })['catch'](function () { /* 未登录属正常 */ });

  // 密码框常驻「显示密码」按钮 + 必填标记与即时校验
  FL.bindPasswordToggles($('#registerForm'));
  FL.bindFields($('#registerForm'), RULES);

  $('#registerForm').on('submit', function (event) {
    event.preventDefault();
    var $form = $(this);
    if (!FL.validate($form, RULES)) {
      FL.toast('请先修正标红的字段', 'warn', '还有内容需要补充');
      return;
    }

    var $btn = $('#registerBtn').prop('disabled', true).text('注册中…');
    var username = $.trim($('#username').val());
    FL.post('/api/auth/register', {
      username: username,
      nickname: $.trim($('#nickname').val()),
      password: $('#password').val(),
      confirmPassword: $('#confirmPassword').val()
    }).then(function () {
      // 注册不自动登录：回到登录页由用户手动输入账号密码
      FL.toast('账号「' + username + '」注册成功，请登录', 'ok', '注册成功');
      location.href = FL.ctx + '/login.html?registered=' + encodeURIComponent(username);
    })['catch'](function (err) {
      $btn.prop('disabled', false).text('注册账号');
      var message = err.message || '注册失败';
      if (message.indexOf('用户名') >= 0) {
        FL.setFieldError('#username', message);
      } else if (message.indexOf('密码') >= 0) {
        FL.setFieldError('#password', message);
      } else if (message.indexOf('昵称') >= 0) {
        FL.setFieldError('#nickname', message);
      }
      FL.toast(message, 'error', '注册失败');
    });
  });
}(window.jQuery, window.FL));
