# 参考材料与追溯记录

正式阅读入口为 docs/01～05，当前源码位置以这些文档为准。

| 文件 | 用途 |
|---|---|
| 业务流程映射.json | 45 个操作的语义、前端方法、后端方法、主要读写表；生成器的维护源 |
| 文件迁移清单.json | 迁移过程中旧文件位置与最终位置，包括从 MetaService 提取服务的中间文件 |
| Java类位置映射.json | Java 包迁移与业务归属映射 |
| 页面地址映射.json | 旧页面到正式模块地址 |
| 工作区历史资料映射.json | 外层参考文件到“历史资料”的位置 |
| 整理验证结果.md | 本次实施和实际验证范围 |
| legacy-services / legacy-package-info / legacy-database | 原始实现和综合 SQL 的文本存档，用于迁移比对，不参与构建或正式 SQL 执行 |
| 旧项目说明、开发进度、知识库实施说明、首批数据源清单、答辩演示脚本 | 历史背景与专题材料；其中部分位置描述来自迁移前版本 |

原 MetaService 的职责已分别落入 module/ingredient/service、module/region/service、module/analysis/service，不能将 legacy-services 中的文本当作现行业务实现。
