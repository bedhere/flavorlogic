/** Integration checks for the isolated migration database/server only.
 * Start Tomcat at 18080 with FLAVORLOGIC_DB_URL selecting the test database first.
 * This creates fixtures in that isolated database; never point it at the normal app.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const base = 'http://127.0.0.1:18080/flavorlogic';
const database = process.env.FLAVORLOGIC_TEST_DB;
assert.equal(database, 'flavorlogic_structure_check_20261014', 'Explicit isolated test database is required');
const settings = Object.fromEntries(fs.readFileSync(path.join(root, 'backend/src/main/resources/db.properties'), 'utf8')
  .split(/\r?\n/).filter(line => /^db\.[\w]+=/.test(line)).map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const mysql = process.env.MYSQL_COMMAND || 'mysql';
const mysqlArgs = ['-h', '127.0.0.1', '-P', '3306', '-u', settings['db.user'] || 'root', '--default-character-set=utf8mb4', '--batch', '--skip-column-names'];
function sql(statement, schema = database) {
  const result = spawnSync(mysql, [...mysqlArgs, schema], {
    input: statement, encoding: 'utf8', env: { ...process.env, MYSQL_PWD: settings['db.password'] }
  });
  assert.equal(result.status, 0, result.stderr || String(result.error));
  return result.stdout.trim();
}
const observations = [];
function pass(message) { observations.push(message); console.log('PASS: ' + message); }

function client() {
  let cookie = '';
  return async function request(method, endpoint, body, expectedCode = 0) {
    const response = await fetch(base + endpoint, {
      method, redirect: 'manual', headers: { Cookie: cookie, ...(body ? { 'Content-Type': 'application/json' } : {}) },
      body: body ? JSON.stringify(body) : undefined
    });
    const setCookie = response.headers.get('set-cookie');
    if (setCookie) { cookie = setCookie.split(';')[0]; }
    if (!endpoint.startsWith('/api/')) { return response; }
    const result = await response.json();
    assert.equal(result.code, expectedCode, `${method} ${endpoint}: ${result.message}`);
    return result.data;
  };
}
const guest = client();
const user = client();
const admin = client();
const other = client();
const aliases = JSON.parse(fs.readFileSync(path.join(root, 'docs/reference/页面地址映射.json'), 'utf8'));
for (const [oldPath, newPath] of Object.entries(aliases)) {
  const response = await guest('GET', oldPath + '?id=7&x=1');
  assert.equal(response.status, 302);
  assert.equal(response.headers.get('location'), '/flavorlogic' + newPath + '?id=7&x=1');
}
const protectedPage = await guest('GET', '/modules/analysis/analysis-run.html?recipeId=7');
assert.equal(protectedPage.status, 302);
assert.equal(protectedPage.headers.get('location'), '/flavorlogic/modules/auth/login.html?redirect=' + encodeURIComponent('/modules/analysis/analysis-run.html?recipeId=7'));
await guest('GET', '/api/recipes', null, 401);
pass('Legacy page redirects preserve parameters; protected pages/APIs keep login protection');

const suffix = String(Date.now());
const username = 'st_u_' + suffix;
const adminName = 'st_a_' + suffix;
const otherName = 'st_o_' + suffix;
const password = 'Structure@123';
for (const [session, name] of [[user, username], [admin, adminName], [other, otherName]]) {
  await session('POST', '/api/auth/register', { username: name, nickname: '结构回归验证', password, confirmPassword: password });
}
const loggedIn = await user('POST', '/api/auth/login', { username, password });
assert.equal(Number(sql(`SELECT id FROM sys_user WHERE username='${username}'`)), loggedIn.id, 'HTTP must use the isolated schema');
const adminId = Number(sql(`SELECT id FROM sys_user WHERE username='${adminName}'`));
assert.ok(Number.isSafeInteger(adminId) && adminId > 0);
sql(`UPDATE sys_user SET role='ADMIN' WHERE id=${adminId} AND username='${adminName}'`);
assert.equal((await admin('POST', '/api/auth/login', { username: adminName, password })).isAdmin, true);
await other('POST', '/api/auth/login', { username: otherName, password });
await user('GET', '/api/admin/users', null, 403);
pass('Registration/login and administrator protection remain correct');

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => entry.isDirectory() ? walk(path.join(dir, entry.name)) : [path.join(dir, entry.name)]);
}
const pages = walk(path.join(root, 'frontend')).filter(file => file.endsWith('.html'));
for (const file of pages) {
  const route = '/' + path.relative(path.join(root, 'frontend'), file).split(path.sep).join('/');
  const response = await (route.startsWith('/admin/') ? admin : user)('GET', route);
  assert.equal(response.status, 200, route);
  assert.match(await response.text(), /<meta name="app-root" content=/);
}
const resources = walk(path.join(root, 'frontend')).filter(file => /\.(js|css)$/.test(file));
for (const file of resources) {
  const route = '/' + path.relative(path.join(root, 'frontend'), file).split(path.sep).join('/');
  const response = await guest('GET', route);
  assert.equal(response.status, 200, route);
  assert.ok(!response.headers.get('content-type')?.includes('text/html'), route);
}
pass(`${pages.length} canonical pages and ${resources.length} JS/CSS resources load at their actual deployed paths`);

const ingredientData = await user('GET', '/api/ingredients');
const ingredients = ingredientData.list;
const sugar = ingredients.find(item => item.name === '白砂糖') || [...ingredients].sort((a, b) => b.sweet - a.sweet)[0];
const flour = ingredients.find(item => item.name === '低筋面粉') || ingredients.find(item => item.id !== sugar.id && item.sweet !== sugar.sweet);
assert.ok(sugar && flour);
const items = [flour, sugar].map((ingredient, index) => ({ ingredientId: ingredient.id, ingredientName: ingredient.name, amount: index ? 100 : 200, unit: 'g', costPerUnit: 0 }));
const created = await user('POST', '/api/recipes', { name: '结构迁移回归配方', productType: '烘焙', items });
assert.ok(created.id > 0);
assert.equal(Number(sql(`SELECT COUNT(*) FROM recipe_item WHERE recipe_id=${created.id}`)), 2);
const detail = await user('GET', '/api/recipes/' + created.id);
assert.equal(detail.items.length, 2);
await other('GET', '/api/recipes/' + created.id, null, 403);
const beforeCompose = sql('SELECT (SELECT COUNT(*) FROM recipe), (SELECT COUNT(*) FROM analysis_task)');
await user('GET', `/api/recipes/${created.id}/compose?goalType=REDUCE_SUGAR`);
assert.equal(sql('SELECT (SELECT COUNT(*) FROM recipe), (SELECT COUNT(*) FROM analysis_task)'), beforeCompose);
pass('Recipe and item persistence, ownership checks and read-only recipe composition');

const targetItems = items.map(item => ({ ...item, amount: item.ingredientId === sugar.id ? 50 : item.amount }));
const task = await user('POST', '/api/analysis', { recipeId: created.id, taskName: '目录迁移控糖验证', goalType: 'REDUCE_SUGAR', targetItems, constraints: { healthGoal: 'NONE' } });
assert.equal(task.status, 'COMPLETED');
assert.ok(task.result && task.baselineSnapshot && task.targetSnapshot);
assert.equal(sql(`SELECT status FROM analysis_task WHERE id=${task.id}`), 'COMPLETED');
assert.equal(Number(sql(`SELECT COUNT(*) FROM analysis_result WHERE task_id=${task.id}`)), 1);
const persistedInput = sql(`SELECT baseline_snapshot_json FROM analysis_task WHERE id=${task.id}`);
const persistedOutput = sql(`SELECT baseline_json, target_json, delta_json, suggestion_json FROM analysis_result WHERE task_id=${task.id}`);
await user('POST', `/api/analysis/${task.id}/feedback`, { helpful: 1, rating: 4, comment: '首次反馈', trialResult: 'PARTIAL' });
await user('POST', `/api/analysis/${task.id}/feedback`, { helpful: 1, rating: 5, comment: '已完成试产', trialResult: 'PASSED' });
assert.equal(sql(`SELECT COUNT(*), MAX(rating) FROM analysis_feedback WHERE task_id=${task.id} AND user_id=${loggedIn.id}`), '1\t5');
const updatedRecipe = await user('PUT', '/api/recipes/' + created.id, { ...detail, name: '已修改配方', items: items.map(item => ({ ...item, amount: item.amount + 20 })) });
assert.equal(updatedRecipe.versionNo, 2);
await admin('PUT', '/api/admin/ingredients/' + sugar.id, { ...sugar, sweet: Math.max(0, Number(sugar.sweet) - 5) });
await user('POST', '/api/auth/logout', {});
await user('POST', '/api/auth/login', { username, password });
const reloaded = await user('GET', '/api/analysis/' + task.id);
assert.equal(reloaded.feedback.rating, 5);
assert.equal(sql(`SELECT baseline_snapshot_json FROM analysis_task WHERE id=${task.id}`), persistedInput);
assert.equal(sql(`SELECT baseline_json, target_json, delta_json, suggestion_json FROM analysis_result WHERE task_id=${task.id}`), persistedOutput);
await other('GET', '/api/analysis/' + task.id, null, 403);
await user('GET', '/api/analysis/history');
await user('GET', '/api/dashboard');
await admin('GET', '/api/admin/tasks');
pass('Analysis input/result persistence, feedback upsert, version update and frozen history after ingredient changes/relogin');

const categories = await admin('GET', '/api/admin/articles/categories');
const article = await admin('POST', '/api/admin/articles', { title: '目录迁移验证文章 ' + suffix, categoryId: categories[0].id, content: '<p>这是验证用的研发文章正文。' + suffix + '</p>', summary: '验证文章链路', status: 1, coverUrl: 'assets/knowledge-lab.jpg' });
await guest('GET', '/api/articles/' + article.id);
assert.equal(Number(sql(`SELECT view_count FROM knowledge_article WHERE id=${article.id}`)), 1);
const document = await admin('POST', '/api/admin/knowledge/documents', { url: 'https://example.com/structure-' + suffix, title: '结构验证资料 ' + suffix, contentText: 'structureverification 食材风味资料验证内容 ' + suffix, summary: '独立测试样本', sourceType: 'WEB' });
assert.equal(document.reviewStatus, 'REVIEW_PENDING');
const pendingSearch = (await user('GET', '/api/knowledge/search?q=structureverification&limit=20')).items;
assert.ok(!pendingSearch.some(item => item.documentId === document.id));
await admin('PUT', '/api/admin/knowledge/documents/' + document.id, { reviewStatus: 'APPROVED', reviewNote: '独立测试数据库验证' });
assert.equal(sql(`SELECT review_status FROM source_document WHERE id=${document.id}`), 'APPROVED');
const approvedSearch = (await user('GET', '/api/knowledge/search?q=structureverification&limit=20')).items;
assert.ok(approvedSearch.length > pendingSearch.length);
await admin('GET', '/api/admin/knowledge/claims');
await admin('GET', '/api/admin/knowledge/attributes');
await admin('GET', '/api/admin/knowledge/evidence-links');
pass('Article persistence/view counting, knowledge import/review/search and evidence administration routes');

await user('DELETE', '/api/analysis/' + task.id);
assert.equal(sql(`SELECT (SELECT COUNT(*) FROM analysis_result WHERE task_id=${task.id}), (SELECT COUNT(*) FROM analysis_feedback WHERE task_id=${task.id})`), '0\t0');
await user('DELETE', '/api/recipes/' + created.id);
assert.equal(sql(`SELECT status FROM recipe WHERE id=${created.id}`), '0');
pass('Analysis cascading deletion and recipe soft deletion');
fs.writeFileSync(path.join(root, '.runtime/verification/runtime-results.json'), JSON.stringify({ date: '2026-10-14', database, observations }, null, 2));
