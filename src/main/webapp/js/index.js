/* ============================================================
   公共首页：首屏轮播、最新研发知识、真实文章计数
   ============================================================ */
(function ($, FL) {
  'use strict';

  FL.initTheme();

  var HERO_IMAGES = ['assets/hero-kitchen.jpg', 'assets/hero-chef.jpg', 'assets/hero-ingredients.jpg'];
  var heroIndex = 0;
  var heroTimer = null;

  /* ---------------- 顶栏交互 ---------------- */
  $('[data-action="theme"]').on('click', function () {
    FL.toggleTheme();
    $(this).text(document.documentElement.getAttribute('data-theme') === 'dark' ? '☾' : '☼');
  });
  $('[data-action="menu"]').on('click', function () {
    $('#navLinks').toggleClass('open');
  });
  $('.nav-links a').on('click', function () { $('#navLinks').removeClass('open'); });

  /* ---------------- 首屏图片轮播 ---------------- */
  function showHero(index) {
    heroIndex = (index + HERO_IMAGES.length) % HERO_IMAGES.length;
    $('#hero').css('--hero-image', 'url("' + HERO_IMAGES[heroIndex] + '")');
    $('.carousel-dot').removeClass('active').eq(heroIndex).addClass('active');
  }

  function startHeroLoop() {
    if (heroTimer) { window.clearInterval(heroTimer); }
    heroTimer = window.setInterval(function () { showHero(heroIndex + 1); }, 6000);
  }

  $('.carousel-dot').on('click', function () {
    showHero(Number($(this).data('hero')) || 0);
    startHeroLoop();
  });
  showHero(0);
  startHeroLoop();

  /* ---------------- 最新研发知识 ---------------- */
  function renderArticles(page) {
    var list = (page && page.list) || [];
    $('#metricArticles').text((page && page.total ? page.total : list.length) + ' 篇');
    if (!list.length) {
      $('#articleGrid').html(FL.empty('知识库还在建设中', '管理员发布文章后，这里会展示最新的研发知识。'));
      return;
    }
    var covers = ['knowledge-lab.jpg', 'hero-ingredients.jpg', 'knowledge-ingredients.jpg'];
    var html = list.slice(0, 3).map(function (article, index) {
      var cover = article.coverUrl ? article.coverUrl : ('assets/' + covers[index % covers.length]);
      return '<a class="article" href="article-detail.html?id=' + article.id + '">' +
        '<div class="article-media" style="background-image:url(\'' + FL.escape(cover) + '\')"></div>' +
        '<div class="article-body">' +
        '<span class="tag">' + FL.escape(article.categoryName || '研发知识') + '</span>' +
        '<h3>' + FL.escape(article.title) + '</h3>' +
        '<p>' + FL.escape(article.summary || '') + '</p>' +
        '<div class="article-meta"><span>' + FL.escape(article.authorName || '食之有理') + '</span>' +
        '<span>' + FL.escape(FL.fmtDate(article.publishedAt || article.createdAt).substring(0, 10)) + '</span></div>' +
        '</div></a>';
    }).join('');
    $('#articleGrid').html(html);
  }

  FL.get('/api/articles' + FL.qs({ page: 1, pageSize: 3 }))
    .then(renderArticles)
    ['catch'](function () {
      $('#metricArticles').text('研发知识');
      $('#articleGrid').html(FL.empty('文章加载失败', '请稍后刷新页面重试。'));
    });
}(window.jQuery, window.FL));
