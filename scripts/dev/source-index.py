"""Generate a repository-relative source map and check structural references.

Python standard library only. Run from any directory; no database/network access.
This is a class-level static index, not an endpoint-level execution trace.
"""
import argparse
from pathlib import Path
import re
import json
from urllib.parse import quote, unquote, urlsplit

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / 'frontend'
JAVA = ROOT / 'backend/src/main/java/com/flavorlogic'
OUTPUT = ROOT / 'docs/02-功能源码对照.md'


def read(path):
    return path.read_text(encoding='utf-8-sig')


def line(text, offset):
    return text.count('\n', 0, offset) + 1


def link(path, label=None, number=None):
    relative = Path(path).relative_to(ROOT).as_posix()
    target = quote('../' + relative, safe='/.-')
    suffix = f'#L{number}' if number else ''
    return f'[{label or relative}]({target}{suffix})'


def code_only(text):
    # Preserve strings and line numbers, blank comments (including URL-looking text).
    token = r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/'
    return re.sub(token, lambda m: re.sub(r'[^\n]', ' ', m[0])
                  if m[0].startswith(('//', '/*')) else m[0], text)


API_MAP = {}


def requests(text):
    clean = code_only(text)
    result = []
    for match in re.finditer(r'FL\.(?:(get|post|put|del)|(\w+Api)\.(\w+))\s*\(\s*', clean):
        start, pos, depth, string = match.end(), match.end(), 0, None
        while pos < len(clean):
            ch = clean[pos]
            if string:
                if ch == '\\':
                    pos += 2
                    continue
                if ch == string:
                    string = None
            elif ch in "'\"":
                string = ch
            elif ch in '([{':
                depth += 1
            elif ch in ')]}':
                if depth == 0:
                    break
                depth -= 1
            elif ch == ',' and depth == 0:
                break
            pos += 1
        expression = ' '.join(text[start:pos].split())
        if match[1]:
            prefix = re.match(r'[\'"](/api/[^\'"]*)', expression)
            if not prefix:
                raise ValueError(f'Cannot resolve API expression: {expression}')
            method = {'get': 'GET', 'post': 'POST', 'put': 'PUT', 'del': 'DELETE'}[match[1]]
            endpoint = prefix[1]
            facade = None
        else:
            key = (match[2], match[3])
            if key not in API_MAP:
                raise ValueError(f'Unknown API facade: {key}')
            method, endpoint, facade = API_MAP[key]
        result.append((method, expression, endpoint.split('?')[0], line(text, match.start()), facade))
    return result


def generate():
    errors = []
    sources = {p.stem: (p, read(p)) for p in sorted(JAVA.rglob('*.java'))
               if p.name != 'package-info.java'}
    service_factory = code_only(sources['Services'][1])
    singletons = dict(re.findall(r'private static final \w+ (\w+) = new (\w+)\(', service_factory))
    factory = {method: singletons[constant] for method, constant in
               re.findall(r'public static \w+ (\w+)\(\)\s*\{\s*return (\w+);', service_factory)}
    ddl_path = ROOT / 'database/schema/flavor_logic.sql'
    ddl = read(ddl_path)
    tables = {m[1]: line(ddl, m.start()) for m in
              re.finditer(r'^CREATE TABLE IF NOT EXISTS `(\w+)`', ddl, re.M)}
    dao_tables, dependencies, routes = {}, {}, []
    for name, (path, text) in sources.items():
        clean = code_only(text)
        dependencies[name] = set(re.findall(r'\bnew (\w+)\s*\(', clean)) & sources.keys()
        dependencies[name].update(factory[m] for m in re.findall(r'Services\.(\w+)\(', clean) if m in factory)
        if '/dao/impl/' in path.as_posix():
            # Table names only from SQL string literals, not JavaDoc or guesses from class names.
            strings = ' '.join(re.findall(r'"((?:\\.|[^"\\])*)"', clean))
            found = set(re.findall(r'\b(?:FROM|JOIN|INTO|UPDATE)\s+`?(\w+)`?', strings, re.I))
            dao_tables[name] = found & tables.keys()
        route = re.search(r'@WebServlet\("([^"\n]+)"\)', clean)
        if route:
            methods = set(re.findall(r'protected void do(Get|Post|Put|Delete)\(', clean))
            rejected = set(re.findall(r'protected void do(Get|Post|Put|Delete)\([^)]*\)\s*\{\s*methodNotAllowed\(response\);\s*\}', clean))
            routes.append((route[1], name, line(text, route.start()),
                           methods - rejected))
    if len({r[0] for r in routes}) != len(routes):
        errors.append('Duplicate Servlet URL mappings')

    def reachable(name):
        found, pending = set(), [name]
        while pending:
            current = pending.pop()
            if current in found:
                continue
            found.add(current)
            pending.extend(dependencies.get(current, set()) - found)
        return found

    def match_route(prefix):
        candidates = [r for r in routes if prefix == r[0] or
                      (r[0].endswith('/*') and (prefix == r[0][:-2] or prefix.startswith(r[0][:-1])))]
        return max(candidates, key=lambda r: len(r[0]), default=None)

    pages = sorted(WEB.rglob('*.html'))
    scripts = sorted(p for p in WEB.rglob('*.js') if 'vendor' not in p.parts)
    API_MAP.clear()
    for script in scripts:
        if not script.name.endswith('-api.js'):
            continue
        text = read(script)
        namespace = re.search(r'FL\.(\w+Api) =', text)
        if not namespace:
            continue
        for definition in re.finditer(r'/\*\* (GET|POST|PUT|DELETE) (/api/[^ ]+) \*/\s*(\w+): function', text):
            API_MAP[(namespace[1], definition[3])] = (definition[1], definition[2], script)
    counts = f'{len(sources)} 个 Java 类、{len(pages)} 个 HTML 页面、{len(scripts)} 个自有 JS 文件、{len(routes)} 个 Servlet 映射、{len(tables)} 张表'
    out = ['# 功能源码对照（自动生成）', '',
           '> 生成：`python scripts/dev/source-index.py`；校验：`python scripts/dev/source-index.py --check`。请勿手工修改。', '',
           counts + '。第三方 vendor 文件与 package-info.java 不计入类/脚本数量。', '',
           '按页面标题、URL、类名或表名用 Ctrl+F 查找。链接均相对仓库，可在 IDE/Markdown 预览中打开；`#L` 给出生成时的源码行号，部分查看器需要手动跳到该行。', '',
           '**索引口径**：页面请求逐条来自实际 JS；完整地址前缀在同模块 *-api.js，页面传入编号/查询串后缀。Servlet 到 Service/DAO/表的对应为类级依赖集合，可能包含同一类的其他分支，不能理解为某次请求一定访问全部表。精确业务过程见 [答辩流程](05-答辩流程.md)。公共登录探测和退出请求在末尾单列。', '',
           '## 页面 → 前端文件 → 接口入口', '']
    trace_path = ROOT / 'docs/reference/业务流程映射.json'
    if trace_path.is_file():
        operations = json.loads(read(trace_path))
        actions = ['## 主要操作的精确对应关系', '',
                   '这里按具体业务方法区分读取与写入；记录维护在 docs/reference/业务流程映射.json。下方的类级集合仅作为补充查找。', '',
                   '| 界面操作 / 页面 | 页面方法 / API 文件 | HTTP / Servlet 方法 | 业务方法 / DAO 方法 | 主要读取表 | 主要写入表 / 事务 |',
                   '|---|---|---|---|---|---|']
        for operation in operations:
            page = WEB / operation['page']
            script = page.with_suffix('.js')
            api = page.parent / operation['apiFile']
            for file in (page, script, api):
                if not file.is_file():
                    errors.append(f'Missing operation file: {file}')
            if operation['frontendMethod'] and not re.search(r'function\s+' + re.escape(operation['frontendMethod']) + r'\s*\(', read(script)):
                errors.append('Missing page method: ' + operation['frontendMethod'])
            script_source = read(script)
            frontend_line = None
            if operation['frontendMethod']:
                frontend_match = re.search(r'function\s+' + re.escape(operation['frontendMethod']) + r'\s*\(', script_source)
                if frontend_match:
                    frontend_line = line(script_source, frontend_match.start())
            else:
                expected_method, expected_url = operation['http'].split(' ', 1)
                action = re.search(r'/\{id\}/([^/?]+)$', expected_url)
                for request_method, expression, prefix, number, facade in requests(script_source):
                    if request_method == expected_method and expected_url.startswith(prefix.rstrip('/')):
                        if action and ('/' + action[1]) not in expression:
                            continue
                        frontend_line = number
                        break
                if frontend_line is None:
                    errors.append('No frontend request located for operation: ' + operation['name'])
            calls = []
            for item in operation['backend']:
                class_name, method = item.split('.', 1)
                if class_name not in sources:
                    errors.append('Missing trace class: ' + class_name)
                    continue
                path, source = sources[class_name]
                pattern = r'(?:public|protected|private)\s+[^;{}=]*?\b' + re.escape(method) + r'\s*\('
                match = re.search(pattern, source)
                if not match:
                    errors.append('Missing trace method: ' + item)
                calls.append(link(path, item, line(source, match.start()) if match else None))
            for table in operation['reads'] + operation['writes']:
                if table not in tables:
                    errors.append('Missing trace table: ' + table)
            actions.append('| ' + operation['name'] + '<br>' + link(page, page.name) + ' | ' +
                           link(script, (operation['frontendMethod'] or script.name) + (':' + str(frontend_line) if frontend_line else ''), frontend_line) + '<br>' + link(api, api.name) + ' | `' + operation['http'] + '`<br>' +
                           calls[0] + ' | ' + '<br>'.join(calls[1:]) + ' | ' + '<br>'.join(operation['reads']) +
                           ' | ' + '<br>'.join(operation['writes']) + '<br>' + operation['transaction'] + ' |')
        actions.append('')
        out[10:10] = actions
    used_scripts = set()
    for page in pages:
        text = read(page)
        title = re.search(r'<title>(.*?)</title>', text, re.S)
        title = re.sub(r'\s*[·|].*', '', title[1]).strip() if title else page.stem
        page_scripts = []
        for match in re.finditer(r'<(?:script|link|img)\b[^>]*?\b(?:src|href)="([^"]+)"', text):
            ref = urlsplit(match[1])
            if ref.scheme or ref.netloc or not ref.path:
                continue
            resource = (WEB / unquote(ref.path.lstrip('/')) if ref.path.startswith('/')
                        else page.parent / unquote(ref.path)).resolve()
            if not resource.is_relative_to(WEB.resolve()) or not resource.is_file():
                errors.append(f'{page.relative_to(ROOT)}:{line(text, match.start())}: missing resource {match[1]}')
            if match[0].startswith('<script') and resource.is_relative_to(WEB.resolve()) and 'vendor' not in resource.parts:
                page_scripts.append(resource)
                used_scripts.add(resource)
        meta = re.search(r'<meta name="app-root" content="([^"]+)"', text)
        if not meta or (page.parent / meta[1]).resolve() != WEB.resolve():
            errors.append(f'{page.name}: app-root must resolve to frontend root')
        for nav in re.finditer(r'<a\b[^>]*href="([^"]+)"', text):
            value = urlsplit(nav[1])
            if value.scheme or value.netloc or not value.path:
                continue
            destination = (WEB / value.path.lstrip('/') if value.path.startswith('/') else page.parent / value.path).resolve()
            if value.path.endswith('.html') and not destination.is_file():
                errors.append(f'{page.name}: broken page link {nav[1]}')
        expected = page.with_suffix('.js')
        if expected.resolve() not in page_scripts:
            errors.append(f'{page.name}: must load corresponding page script {expected.relative_to(WEB)}')
        required = [WEB / 'shared/core/common.js', WEB / 'shared/core/api.js']
        if any(p.resolve() not in page_scripts for p in required):
            errors.append(f'{page.name}: missing common/API script')
        elif not (page_scripts.index(required[0].resolve()) < page_scripts.index(required[1].resolve())
                  < page_scripts.index(expected.resolve())):
            errors.append(f'{page.name}: script loading order must be common → api → page')
        if re.search(r'<script\b(?![^>]*\bsrc=)[^>]*>\s*\S', text):
            errors.append(f'{page.name}: inline business script must move next to its HTML')
        out += [f'### {title} — /{page.relative_to(WEB).as_posix()}', '',
                '页面：' + link(page) + '；页面/组件脚本：' + '、'.join(link(p) for p in page_scripts if '/core/' not in p.as_posix()), '',
                '| 方法 | 实际接口地址前缀 / 动态参数 | 页面调用位置 | 接口文件 / Servlet |',
                '|---|---|---|---|']
        hits = 0
        for script in page_scripts:
            if '/core/' in script.as_posix() or script.name.endswith('-api.js'):
                continue
            for method, expression, prefix, number, facade in requests(read(script)):
                route = match_route(prefix)
                if route is None:
                    errors.append(f'{script.name}:{number}: no Servlet mapping for {prefix}')
                    continue
                if method.title() not in route[3]:
                    errors.append(f'{script.name}:{number}: {route[1]} does not implement {method}')
                endpoint_display = (f'`{prefix}`<br>后缀：`{expression.replace(chr(124), "&#124;")}`' if facade else f'`{expression}`')
                out.append(f'| {method} | {endpoint_display} | {link(script, script.name + ":" + str(number), number)} | {link(facade, facade.name) if facade else "公共直接请求"} → {link(sources[route[1]][0], route[1], route[2])} |')
                hits += 1
        if not hits:
            out.append('| — | 本页无直接业务 API 请求 | — | — |')
        out.append('')
    for script in scripts:
        for nav in re.finditer(r'[\'"](/(?:modules|admin)/[A-Za-z0-9/-]+\.html)', read(script)):
            if not (WEB / nav[1].lstrip('/')).is_file():
                errors.append(f'{script.name}: broken generated route {nav[1]}')
        if script.resolve() not in used_scripts:
            errors.append(f'Unreferenced own script: {script.relative_to(ROOT)}')
    out += ['## Servlet → Service / 引擎 → DAO → 表（类级依赖）', '',
            '| URL 映射 / 方法 | Servlet | 业务实现 / 引擎 | JDBC DAO 实现 | 涉及表集合 |',
            '|---|---|---|---|---|']
    for route, name, number, methods in sorted(routes):
        found = reachable(name)
        business = sorted(n for n in found if '/service/impl/' in sources[n][0].as_posix() or '/engine/' in sources[n][0].as_posix())
        daos = sorted(found & dao_tables.keys())
        table_set = set().union(*(dao_tables[d] for d in daos))
        out.append(f'| `{route}`<br>{", ".join(m.upper() for m in sorted(methods))} | {link(sources[name][0], name, number)} | ' +
                   '<br>'.join(link(sources[n][0], n) for n in business) + ' | ' +
                   '<br>'.join(link(sources[n][0], n) for n in daos) + ' | ' +
                   '<br>'.join(link(ddl_path, t, tables[t]) for t in sorted(table_set)) + ' |')
    out += ['', '## 数据库表 → 实际 JDBC 文件（反向查找）', '',
            '| 表 / DDL 行 | 读取或写入该表的 DAO（包括 JOIN） |', '|---|---|']
    for table, number in tables.items():
        owners = sorted(n for n, names in dao_tables.items() if table in names)
        out.append(f'| {link(ddl_path, table + ":" + str(number), number)} | ' +
                   ('<br>'.join(link(sources[n][0], n) for n in owners) or '当前未检测到 JDBC DAO 访问；仅有建表/种子定义，不能声称已接入业务') + ' |')
    out += ['', '## 公共登录请求', '']
    common = WEB / 'shared/core/common.js'
    for method, expression, prefix, number, facade in requests(read(common)):
        route = match_route(prefix)
        if route is None:
            errors.append(f'Unmapped common request: {prefix}')
        else:
            out.append(f'- {method} `{expression}`：{link(common, "common.js:" + str(number), number)} → {link(sources[route[1]][0], route[1], route[2])}。')
    out += ['', 'HTTP 封装：' + link(WEB / 'shared/core/api.js') + '；后端入口模板：' + link(JAVA / 'common/servlet/BaseServlet.java') +
            '；事务模板：' + link(JAVA / 'common/service/BaseService.java') + '；SQL 执行：' + link(JAVA / 'common/persistence/JdbcHelper.java') + '。', '']
    if errors:
        raise ValueError('\n'.join(errors))
    return '\n'.join(out), counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Validate references and reject a stale generated index')
    args = parser.parse_args()
    try:
        content, counts = generate()
        if args.check:
            if not OUTPUT.is_file() or read(OUTPUT) != content:
                raise ValueError('源码索引 is stale: run python scripts/dev/source-index.py')
            print('PASS: resource references, page pairing, API mappings, script order and source index; ' + counts)
        else:
            OUTPUT.write_text(content, encoding='utf-8', newline='\n')
            print('Generated docs/02-功能源码对照.md; ' + counts)
    except ValueError as error:
        parser.exit(1, 'FAIL: ' + str(error) + '\n')


if __name__ == '__main__':
    main()
