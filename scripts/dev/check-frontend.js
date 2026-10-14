/* Dependency-free checks for the reorganized front end and the shared API contract. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '../../frontend');

function files(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? files(path.join(dir, entry.name)) : [path.join(dir, entry.name)]);
}

async function main() {
  const scripts = files(root).filter(file => file.endsWith('.js') && !file.includes(path.sep + 'vendor' + path.sep));
  for (const file of scripts) {
    const result = spawnSync(process.execPath, ['--check', file], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr || String(result.error));
  }

  let pending;
  // Register DOM-ready handlers without running UI code in this transport test.
  function jquery(callback) { assert.equal(typeof callback, 'function'); }
  jquery.ajax = function (settings) {
    pending = { settings };
    const request = {
      done(callback) { pending.done = callback; return request; },
      fail(callback) { pending.fail = callback; return request; }
    };
    return request;
  };
  const location = { pathname: '/flavorlogic/admin/user/users.html', search: '?page=2', href: 'http://localhost/flavorlogic/admin/user/users.html?page=2' };
  const window = { jQuery: jquery, console: { info() {} }, location };
  const context = vm.createContext({ window, location, Promise, URL, document: { querySelector() { return { content: '../../' }; } } });
  for (const file of ['shared/core/common.js', 'shared/core/api.js']) {
    vm.runInContext(fs.readFileSync(path.join(root, file), 'utf8'), context, { filename: file });
  }
  const FL = window.FL;
  assert.equal(FL.ctx, '/flavorlogic');
  let response = FL.get('/api/recipes?page=2');
  assert.equal(pending.settings.url, '/flavorlogic/api/recipes?page=2');
  assert.equal(pending.settings.type, 'GET');
  pending.done({ code: 0, data: { id: 7 } });
  assert.equal((await response).id, 7);

  response = FL.post('/api/analysis', { recipeId: 7, targetItems: [{ ingredientId: 3, amount: 20 }] });
  assert.equal(pending.settings.type, 'POST');
  assert.equal(pending.settings.contentType, 'application/json; charset=UTF-8');
  assert.equal(JSON.parse(pending.settings.data).targetItems[0].amount, 20);
  pending.done({ code: 400, message: '目标方案需要调整' });
  await assert.rejects(response, error => error.bizCode === 400 && error.message === '目标方案需要调整');

  response = FL.put('/api/recipes/7', { name: '新配方' });
  assert.equal(pending.settings.type, 'PUT');
  pending.done({ code: 0, data: null });
  assert.equal(await response, null);
  response = FL.del('/api/recipes/7');
  assert.equal(pending.settings.type, 'DELETE');
  pending.fail({ status: 403, responseText: '{"code":403,"message":"无权修改他人的配方"}' });
  await assert.rejects(response, error => error.bizCode === 403 && error.message === '无权修改他人的配方');

  response = FL.get('/api/auth/me', { silent401: true });
  pending.fail({ status: 401, responseText: '{}' });
  await assert.rejects(response, error => error.bizCode === 401);
  assert.equal(location.href, 'http://localhost/flavorlogic/admin/user/users.html?page=2');
  response = FL.get('/api/recipes');
  pending.fail({ status: 401, responseText: '{}' });
  await assert.rejects(response, error => error.bizCode === 401);
  assert.equal(location.href, '/flavorlogic/modules/auth/login.html?redirect=' + encodeURIComponent('/admin/user/users.html?page=2'));
  location.pathname = '/flavorlogic/modules/auth/login.html';
  location.href = '';
  response = FL.get('/api/auth/me');
  pending.fail({ status: 401, responseText: '{}' });
  await assert.rejects(response);
  assert.equal(location.href, '', 'Login-page probes must not create a redirect loop');
  console.log(`PASS: ${scripts.length} JS syntax checks; API methods, JSON payload, business error, 403, silent 401 and login redirects`);
}

main().catch(error => { console.error(error); process.exitCode = 1; });
