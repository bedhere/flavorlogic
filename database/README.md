# 数据库脚本说明

本次目录整理不要求对已有 flavor_logic 执行迁移。SQL 按实际用途拆分，表与字段保持原样。

| 路径 | 内容 / 时机 |
|---|---|
| schema/flavor_logic.sql | 新环境建库、21 张表、索引、外键 |
| seeds/base_data.sql | 原有 8 段种子 INSERT 与 5 条数据修正；新建表后执行 |
| migrations/001-knowledge-trigger.sql | 缺少 trigger_constraint 字段的旧库兼容升级与修正 |
| migrations/002-knowledge-tables.sql | 旧 15 张表新增来源/审核/证据的 6 张表 |
| seeds/source_sites.sql | 候选来源白名单，默认关闭采集 |
| seeds/prepare_product_label_claims.sql | 已导入产品资料后生成待审核标签声明；不自动批准或更新食材 |
| demo/demo_data.sql | SQL 演示数据快照 |

新环境顺序：schema/flavor_logic.sql → seeds/base_data.sql。也可在项目根目录执行：

```powershell
& ./scripts/data/init-database.ps1
# 应用启动后通过 API 初始化演示样本：
& ./scripts/data/seed-demo-data.ps1
```

初始化脚本从 backend/src/main/resources/db.properties 或环境变量读取连接配置；密码不写到命令参数。-DatabaseName 可指定独立测试库。

已有数据库按缺失字段/表选择 migrations 脚本并先备份。INSERT IGNORE 不覆盖已有行，base_data.sql 中的特定数据修正沿用原脚本逻辑，执行前应核对影响。

原综合 SQL 归档为 docs/reference/legacy-database/flavor_logic-before-split.sql.txt，仅用于对照；正式入口是本目录的分类脚本。scripts/dev/check-sql-layout.py 核对 DDL、种子、修正和升级过程是否与原文件一致。

表反查源码见 [功能源码对照](../docs/02-功能源码对照.md)，字段与业务关系见 [数据库说明](../docs/04-数据库说明.md)。
