/* 错误文案表：按 ?code= 参数（403 / 404 / 500）切换，其他情况使用默认文案 */
  (function () {
    'use strict';

    var TEXT = {
      '403': { title: '没有访问权限', desc: '当前账号无权访问该页面，若确需使用，请联系平台管理员开通对应权限。' },
      '404': { title: '页面不存在', desc: '你访问的页面可能已被移除或地址输入有误，请检查链接后重试。' },
      '500': { title: '服务器内部错误', desc: '服务暂时无法处理该请求，请稍后重试；若持续出现，请联系平台管理员。' }
    };
    var FALLBACK = { title: '页面出现异常', desc: '请求未能正常完成，你可以返回工作台或首页继续使用。' };

    var matched = /[?&]code=([0-9]{3})/.exec(window.location.search);
    var code = matched ? matched[1] : '';
    var text = TEXT[code] || FALLBACK;

    document.getElementById('errorCode').textContent = code || '!';
    document.getElementById('errorTitle').textContent = text.title;
    document.getElementById('errorDesc').textContent = text.desc;
    document.title = text.title + ' · 食之有理 FlavorLogic';

    // 用上下文路径重写链接：部署在 /flavorlogic 等子路径下也能正确跳转
    var ctx = (window.FL && window.FL.ctx) || '';
    document.getElementById('errorBrand').href = ctx + '/index.html';
    document.getElementById('toDashboard').href = ctx + '/modules/dashboard/dashboard.html';
    document.getElementById('toHome').href = ctx + '/index.html';
  }());
