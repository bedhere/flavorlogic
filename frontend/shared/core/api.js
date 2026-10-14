/**
 * HTTP/JSON 公共层：统一请求、响应解包和登录失效处理。
 * 加载顺序：jquery → core/common.js → core/api.js → components → pages。
 * 页面调用 FL.get/post/put/del；后端统一返回 {code, message, data}。
 */
(function (window, $) {
  'use strict';
  var FL = window.FL;

  /* ---------------- API 客户端 ---------------- */

  FL.redirectToLogin = function () {
    var path = location.pathname.replace(FL.ctx, '') || '/';
    // 已经在登录/注册页时直接返回：否则 /api/auth/me 的 401 会把自己再次重定向到登录页，
    // 回跳地址每刷新一次就嵌套一层，最终 URL 超长被容器拒绝（400 Bad Request）
    if (/^\/(?:modules\/auth\/)?(login|register)\.html$/.test(path)) {
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
    location.href = FL.ctx + '/modules/auth/login.html?redirect=' + encodeURIComponent(back);
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

}(window, window.jQuery));
