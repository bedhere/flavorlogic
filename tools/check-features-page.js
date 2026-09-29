/* 一次性校验脚本（不进入应用）：
   1) 校验 index.html / features.html 里用到的自定义类是否都在 CSS 中有定义（防拼写错误）
   2) 校验 CSS 花括号与 calc()/color-mix() 括号是否配平（防语法错误导致整块样式失效） */
const fs = require('fs');
const path = require('path');

const dir = 'D:/食之有理/xm-flavor_logic/src/main/webapp';
const html = ['index.html', 'features.html'].map(f => fs.readFileSync(path.join(dir, f), 'utf8')).join('\n');
const css = ['css/app.css', 'css/pages.css'].map(f => fs.readFileSync(path.join(dir, f), 'utf8')).join('\n');

// 1) 类名检查：只看本次新增/使用的相关前缀，避免被 Bootstrap 工具类干扰
const WATCH = /^(mega|nav-item|nav-trigger|nav-links|doc-|trust-|showcase-link|faq|formula)/;
const used = new Set();
for (const m of html.matchAll(/class="([^"]+)"/g)) {
  m[1].split(/\s+/).filter(Boolean).forEach(c => { if (WATCH.test(c)) used.add(c); });
}
const defined = new Set([...css.matchAll(/\.([a-zA-Z][\w-]*)/g)].map(m => m[1]));
const undefinedClasses = [...used].filter(c => !defined.has(c));
console.log('检查的类数量:', used.size);
console.log('CSS 中缺失定义的类:', undefinedClasses.length ? undefinedClasses.join(', ') : '（无）');

// 2) 括号配平
function balanced(text, open, close, label) {
  let depth = 0;
  for (const ch of text) {
    if (ch === open) depth++;
    if (ch === close) depth--;
    if (depth < 0) return `${label}: 出现多余的 ${close}`;
  }
  return depth === 0 ? `${label}: 配平` : `${label}: 缺少 ${depth} 个 ${close}`;
}
console.log(balanced(css, '{', '}', 'CSS 花括号'));
console.log(balanced(css, '(', ')', 'CSS 圆括号'));

// 3) HTML 里是否残留已废弃的面板类
const stale = ['mega-grid', 'mega-title'].filter(c => new RegExp('class="[^"]*\\b' + c + '\\b').test(html));
console.log('HTML 残留的旧类:', stale.length ? stale.join(', ') : '（无）');
