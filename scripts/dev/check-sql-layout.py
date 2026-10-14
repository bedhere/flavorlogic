"""Verify that SQL partitioning preserves the original DDL and data statements."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
legacy = (ROOT / 'docs/reference/legacy-database/flavor_logic-before-split.sql.txt').read_text(encoding='utf-8')
schema = (ROOT / 'database/schema/flavor_logic.sql').read_text(encoding='utf-8')
seed = (ROOT / 'database/seeds/base_data.sql').read_text(encoding='utf-8')
upgrade = (ROOT / 'database/migrations/001-knowledge-trigger.sql').read_text(encoding='utf-8')
ddl_pattern = r'^CREATE TABLE IF NOT EXISTS `[\w]+` \([\s\S]*?^\) ENGINE=[^;]+;'
old_ddl = re.findall(ddl_pattern, legacy, re.M)
assert len(old_ddl) == 21
assert old_ddl == re.findall(ddl_pattern, schema, re.M), 'Table declarations changed'


def statement_end(text, start):
    quote = None
    i = start
    while i < len(text):
        ch = text[i]
        if quote:
            if ch == '\\':
                i += 2
                continue
            if ch == quote:
                if i + 1 < len(text) and text[i + 1] == quote:
                    i += 2
                    continue
                quote = None
        elif ch in "'\"`":
            quote = ch
        elif text[i:i + 2] == '--':
            i = text.find('\n', i)
            continue
        elif ch == ';':
            return i + 1
        i += 1
    raise ValueError('Unterminated SQL statement')


def statements(text, prefix):
    return [text[m.start():statement_end(text, m.start())]
            for m in re.finditer('^' + re.escape(prefix), text, re.M)]


assert statements(legacy, 'INSERT IGNORE INTO ') == statements(seed, 'INSERT IGNORE INTO '), 'Seed inserts changed'
assert statements(legacy, 'UPDATE `') == statements(seed, 'UPDATE `'), 'Data fixes changed'
assert statements(legacy, 'UPDATE `') == statements(upgrade, 'UPDATE `'), 'Upgrade data fixes changed'
start = legacy.index('DROP PROCEDURE IF EXISTS `fl_add_column_if_absent`;')
end = legacy.index('DROP PROCEDURE IF EXISTS `fl_add_column_if_absent`;', start + 1) + len('DROP PROCEDURE IF EXISTS `fl_add_column_if_absent`;')
assert legacy[start:end] in upgrade, 'Conditional schema upgrade changed'
print('PASS: 21 DDL blocks, 8 seed inserts, 5 data fixes and conditional upgrade are unchanged')
