# 食之有理 FlavorLogic

正式前端在 **frontend/**，后端在 **backend/**，数据库脚本在 **database/**。打开本目录的 pom.xml 导入项目；不要修改构建或运行副本。

## 从界面找到源码

| 你正在演示的功能 | 前端位置 | 后端位置（backend/src/main/java/com/flavorlogic/ 下） |
|---|---|---|
| 登录、注册、用户管理 | modules/auth；admin/user | module/user |
| 配方列表、编辑、生成 | modules/recipe | module/recipe |
| 风味分析、结果、历史、反馈 | modules/analysis；admin/analysis | module/analysis |
| 食材维护、区域画像 | admin/ingredient；分析页面使用选项 | module/ingredient；module/region |
| 研发文章 | modules/article；admin/article | module/article |
| 来源资料检索、审核、证据 | modules/knowledge；admin/knowledge | module/knowledge |
| 工作台 | modules/dashboard | module/dashboard |

前端位置均从 frontend/ 起算。页面和 JS 同名同目录；*-api.js 集中定义接口。后端各模块按 servlet → service → engine/dao → model 组织。

例如提交分析：

```text
frontend/modules/analysis/analysis-run.html
  → analysis-run.js：submitAnalysis()
  → analysis-api.js：POST /api/analysis
  → backend/.../module/analysis/servlet/AnalysisServlet.java
  → service/impl/AnalysisServiceImpl.java：analyze()
  → engine/ + dao/impl/
  → analysis_task / analysis_result
```

## 答辩与查找入口

1. [项目结构说明](docs/01-项目结构说明.md)：文件职责、业务归属、命名和维护方法。
2. [功能源码对照](docs/02-功能源码对照.md)：45 个具体操作、22 个页面、接口及表反查源码。
3. [接口说明](docs/03-接口说明.md)：HTTP 方法、请求、响应、权限。
4. [数据库说明](docs/04-数据库说明.md)：字段对应、关系和事务。
5. [答辩流程](docs/05-答辩流程.md)：从保存配方到分析、回看和反馈的完整讲解。

## 构建与启动

环境：JDK 11+、Maven、Tomcat 9、MySQL。后端仍是原生 Servlet/JDBC，前端仍为 HTML/CSS/jQuery，不引入新的应用框架。

```powershell
# 在本目录运行
mvn -B clean package
& ./scripts/start.ps1 -TomcatHome "你的 Tomcat 9 目录"
# 或边构建边启动
& ./scripts/start.ps1 -Build -TomcatHome "你的 Tomcat 9 目录"
& ./scripts/stop.ps1 -TomcatHome "你的 Tomcat 9 目录"
```

WAR：backend/target/flavorlogic.war。运行目录：.runtime/tomcat。首页： http://localhost:8080/flavorlogic/ 。原页面地址会兼容跳转到新模块路径，API 地址保持原样。

数据库配置模板：[db.properties.example](backend/src/main/resources/db.properties.example)。复制为同目录 db.properties 后填写本机信息，该文件被 Git 忽略。环境变量优先级见模板与 DBUtil。

新数据库按 [数据库脚本说明](database/README.md) 初始化；已有数据库无需因为本次目录调整执行迁移。

```powershell
& ./scripts/data/init-database.ps1
# 应用启动后，可通过 API 初始化演示数据
& ./scripts/data/seed-demo-data.ps1
```

## 验证与迁移记录

```powershell
python scripts/dev/source-index.py
python scripts/dev/source-index.py --check
python scripts/dev/check-sql-layout.py
node scripts/dev/check-frontend.js
node scripts/dev/check-features-page.js
```

运行回归脚本 scripts/dev/check-runtime.mjs 仅允许指定的独立测试数据库与 18080 测试服务，不能用于正式业务库。它验证登录权限、页面资源、配方、分析、反馈、历史快照、文章和审核检索。

迁移清单与旧/新类名、地址映射在 docs/reference/。本机迁移前备份在 .runtime/backups/。历史需求与开发材料在 docs/reference/，外层原型与课程提交材料整理到工作区的“历史资料”。
