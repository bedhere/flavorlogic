/* ============================================================
   产品说明页（features.html）
   ------------------------------------------------------------
   与首页共用同一套顶栏与信息面板（面板交互由 common.js 的 FL.navPanels 负责），
   本文件只处理说明页自己的三件事：
     1. 主题切换与移动端菜单（与 index.js 保持一致的行为）
     2. 目录的当前章节高亮（滚动时同步）
     3. 从其它页面带 #锚点 进入时，补齐被固定顶栏遮挡的定位
   ============================================================ */
(function ($) {
  'use strict';

  /* ---------------- 顶栏交互 ---------------- */
  $('[data-action="theme"]').on('click', function () {
    window.FL.toggleTheme();
    $(this).text(document.documentElement.getAttribute('data-theme') === 'dark' ? '☾' : '☼');
  });
  $('[data-action="menu"]').on('click', function () {
    $('#navLinks').toggleClass('open');
  });
  $('.nav-links a').on('click', function () { $('#navLinks').removeClass('open'); });

  /* ---------------- 目录高亮 ---------------- */
  var $links = $('.doc-nav a');
  var $sections = $('.doc-section');

  if ($links.length && $sections.length) {
    var highlight = function () {
      var offset = 140; // 固定顶栏 + 一点余量
      var current = $sections.eq(0).attr('id');
      $sections.each(function () {
        if ($(this).offset().top - offset <= 0) {
          current = this.id;
        }
      });
      $links.removeClass('is-active').filter('[href="#' + current + '"]').addClass('is-active');
    };

    highlight();
    $(window).on('scroll', highlight);
    $links.on('click', function () {
      $links.removeClass('is-active');
      $(this).addClass('is-active');
    });
  }

  /* ---------------- 锚点定位补偿 ---------------- */
  // 浏览器默认滚动会把标题顶到顶栏下面（顶栏是 fixed 的），这里手动回退一段距离
  if (location.hash && location.hash.length > 1) {
    var target = document.getElementById(location.hash.substring(1));
    if (target) {
      window.setTimeout(function () {
        window.scrollTo(0, Math.max(0, $(target).offset().top - 96));
      }, 0);
    }
  }
}(window.jQuery));
