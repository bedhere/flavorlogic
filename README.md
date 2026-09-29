# 食之有理 FlavorLogic

> 面向食品研发场景的**配方管理与风味分析 Web 应用**（第一阶段 Web 课设版）
> 一句话定位：把「凭经验试错」变成「有依据的配方推演」。

输入一份基准配方与研发目标（原料替换 / 控糖 / 控脂 / 调整刺激度 / 区域适配），系统按食材八维风味属性
计算调整前后的风味向量、给出每个维度的偏移量与变化比例，并由规则库生成**带触发原因和参考幅度**的补偿建议，
最后把「当时的配方 + 属性 + 结果」整体存为分析快照，供后续回看与试产反馈。

---

## 一、技术栈与设计要求

| 层次 | 选型 | 说明 |
|---|---|---|
| 前端 | HTML5 + CSS3 + jQuery 3.7.1 + Bootstrap 5.3.3 | 全部本地引用（`src/main/webapp/lib/`），**不依赖 CDN**，断网可用 |
| 图表 | 自研 Canvas 雷达图（`js/fl-chart.js`） | 不引入第三方图表库，离线可跑 |
| 后端 | Java 11 + Servlet 4.0（`javax.servlet`）+ 原生 JDBC | **未使用 Spring / Spring MVC / MyBatis / Hibernate 等任何框架** |
| 数据库 | MySQL 5.7.37 | 库名 `flavor_logic`，utf8mb4 / utf8mb4_general_ci |
| 运行容器 | Tomcat 9 | 项目自带独立 `CATALINA_BASE`，不影响系统其他实例 |
| 构建 | Maven（war 打包） | 也提供纯脚本方式部署 |

前端界面按项目设计基准页 `index.html` 与《UI 设计复刻规范》一比一实现：
同一套设计令牌（`css/app.css` 由基准页样式提取）、同一套应用外壳（顶栏 + 侧栏 + 内容区）、
同样的组件语言（panel / table / status / toolbar / pager），业务补充样式集中在 `css/pages.css`。

---

## 二、目录结构

```
xm-flavor_logic/
├── pom.xml                         Maven 构建脚本（war 打包，finalName=flavorlogic）
├── start-flavorlogic.ps1           一键启动（可选 -Build 重新编译）
├── stop-flavorlogic.ps1            停止服务
├── README.md                       本文件
├── database/
│   ├── flavor_logic.sql            建库建表 + 基础数据（15 张表，可重复执行）
│   ├── demo_data.sql               演示数据快照（账号 / 配方 / 分析 / 文章）
│   └── seed-demo-data.ps1          通过接口初始化演示数据（幂等，可重复执行）
├── docs/
│   ├── 接口说明.md                 全部 REST 接口清单与请求/响应示例
│   └── 答辩演示脚本.md             答辩演示流程与话术要点
├── tomcat-runtime/                 运行时目录（CATALINA_BASE，脚本自动生成）
└── src/main/
    ├── java/com/flavorlogic/
    │   ├── model/        19 个实体与 DTO（含风味向量、偏移明细、建议、快照）
    │   ├── dao/          12 个 DAO 接口（JdbcHelper 统一参数化访问）
    │   │   └── impl/     11 个 JDBC 实现
    │   ├── service/      8 个业务接口 + 6 个实现（统一事务模板 BaseService）
    │   ├── engine/       3 个分析引擎类（FlavorEngine / RuleEngine / AnalysisEngine）
    │   ├── servlet/      14 个接口类（REST 风格，统一 JSON 协议）
    │   ├── filter/       3 个过滤器（编码与安全头 / 登录 / 管理员）
    │   └── util/         11 个工具类（DB / 密码 / JSON / 分页 / 校验 / 操作日志 / Session …）
    └── webapp/
        ├── index.html                公共首页（游客可见，顶部导航含悬停信息面板）
        ├── features.html             产品说明（口径 / 阈值 / 数据来源 / 边界与免责）
        ├── login.html register.html  登录 / 注册
        ├── dashboard.html            研发工作台
        ├── recipes.html              配方库
        ├── recipe-edit.html          配方录入 / 编辑（配料明细与工艺参数）
        ├── analysis-run.html         发起风味分析（导入基准配方 → 调整目标方案 → 提交）
        ├── analysis-result.html      分析结果（雷达图 / 偏移 / 建议 / 快照 / 反馈）
        ├── analysis-history.html     分析历史
        ├── articles.html             研发知识库
        ├── article-detail.html       文章详情
        ├── profile.html              个人中心
        ├── error.html                403 / 404 / 500 提示页
        ├── admin/                    后台管理：users / articles / ingredients / tasks
        ├── css/                      app.css（设计系统）+ pages.css（业务补充）
        ├── js/                       20 个脚本（common.js 公共层 + fl-chart.js 图表 + fl-ingredient-picker.js 食材搜索下拉 + 各页面脚本）
        ├── lib/                      jquery / bootstrap / chart（本地发行文件）
        └── assets/                   首页与文章配图
```

规模：**87 个 Java 文件、约 1 万行后端代码；17 个页面、18 个前端脚本。**

---

## 三、环境要求

| 组件 | 版本 | 本机实测 |
|---|---|---|
| JDK | 11 及以上 | Temurin 21.0.11 |
| Maven | 3.6+ | 3.8.1（镜像：阿里云） |
| MySQL | 5.7 及以上 | 5.7.37（`D:\MySQL Server 5.7`） |
| Tomcat | 9.x（Servlet 4.0） | 9.0.121（`D:\Tomcat\apache-tomcat-9.0.121`） |

> 注意：MySQL **5.7 不支持** `utf8mb4_0900_ai_ci`（8.0 才有），本项目脚本统一使用 `utf8mb4_general_ci`；
> 另外 5.7 **只解析、不执行 CHECK 约束**，字段合法性由 Service 层校验兜底。

---

## 四、快速开始

### 1. 建库建表

```bash
mysql -u root -p < database/flavor_logic.sql
```

或在 Navicat 中打开 `database/flavor_logic.sql` 执行全部语句（脚本使用 `CREATE TABLE IF NOT EXISTS` + `INSERT IGNORE`，可安全重复执行）。
执行后会创建 `flavor_logic` 库、15 张表，并写入基础数据：5 个文章分类、44 种食材及八维属性、4 个区域画像、4 条补偿规则、64 张配料调配知识卡、22 条配料搭配关系、50 条目标模板规则。

> 注意：`flavor_logic.sql` 在仓库中存在多个历史副本（项目根目录、`课程提交任务/` 等）。
> **唯一正本是 `xm-flavor_logic/database/flavor_logic.sql`**，建库时请以该路径为准，避免执行到旧副本后缺少知识库表。

### 2. 配置数据库连接

默认配置在 `src/main/resources/db.properties`：

```properties
db.url=jdbc:mysql://127.0.0.1:3306/flavor_logic?useUnicode=true&characterEncoding=UTF-8&characterSetResults=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
db.user=root
db.password=你的密码
```

配置优先级：**环境变量 > JVM 系统属性 > db.properties > 内置默认值**，因此不改代码也能切换环境：

```bash
set FLAVORLOGIC_DB_URL=jdbc:mysql://127.0.0.1:3306/flavor_logic?...
set FLAVORLOGIC_DB_USER=root
set FLAVORLOGIC_DB_PASSWORD=******
```

> `characterEncoding=UTF-8` 必须保留：MySQL 5.7 服务器默认字符集是 latin1，
> 不显式声明会导致中文食材名与文章正文乱码。

### 3. 编译与启动

**方式 A：一键脚本（推荐）**

```powershell
powershell -ExecutionPolicy Bypass -File start-flavorlogic.ps1 -Build
# 启动后：http://localhost:8080/flavorlogic/
powershell -ExecutionPolicy Bypass -File stop-flavorlogic.ps1
```

脚本会：编译打包 → 部署到项目自带 `tomcat-runtime`（独立 CATALINA_BASE）→ 启动 Tomcat 并探测首页。
Tomcat 不在默认路径时用 `-TomcatHome "你的路径"` 指定。

**方式 B：手动 Maven + 自己的 Tomcat**

```bash
mvn clean package                 # 产物：target/flavorlogic.war
# 把 war 或解压后的目录放入 Tomcat 的 webapps/，启动 Tomcat
```

**方式 C：IDE**（IDEA / Eclipse）
按 Maven 项目导入 → 配置 Tomcat 9 Server → 部署 `flavorlogic:war exploded` → 运行。

### 4. 初始化演示数据（可选，推荐）

```powershell
powershell -ExecutionPolicy Bypass -File database/seed-demo-data.ps1
```

脚本通过接口创建演示账号、6 篇研发知识文章、4 份示例配方及对应分析记录与试产反馈，**可重复执行**（已存在则跳过）。

| 账号 | 密码 | 角色 | 用途 |
|---|---|---|---|
| `admin` | `Admin@123` | 管理员 | 后台管理：用户 / 文章 / 食材属性 / 分析任务 |
| `demo` | `Demo@123` | 普通用户 | 配方库、发起分析、查看历史与反馈 |

也可以直接导入数据快照（等效，含以上账号的密码哈希）：

```bash
mysql -u root -p flavor_logic < database/demo_data.sql
```

---

## 五、功能与验收对照

| 验收项 | 实现情况 | 主要入口 |
|---|---|---|
| 用户闭环 | 注册、登录、退出、Session 重建、禁用账号拦截；**注册只创建账号、不自动登录**，需回到登录页手动登录；PBKDF2-HMAC-SHA256 加盐哈希，库中无明文密码 | `login.html` / `register.html` / `AuthServlet` / `UserServiceImpl` |
| 配方闭环 | 配方 + 多条配料同事务写入；动态增删配料行；用量正数校验；重复食材拦截；编辑自动升版本号；删除为软删除 | `recipes.html` / `recipe-edit.html` / `RecipeServiceImpl` |
| 分析闭环 | 5 类分析目标；基准与目标快照；八维偏移计算；规则引擎补偿建议；任务状态机 PENDING→RUNNING→COMPLETED/FAILED。编辑与发起分析拆为两个入口：配方库点「编辑配料」进编辑页，点「发起分析」进分析页（可选择/导入基准配方、调整目标方案） | `analysis-run.html` / `AnalysisServiceImpl` / `engine/*` |
| 结果闭环 | 雷达图 + 偏移明细表 + 建议表（含触发原因/参考幅度/风险）+ 配方快照对比 + 解释与免责 + 打印样式；刷新或重新登录仍可回看 | `analysis-result.html` |
| 知识闭环 | 文章列表（分类 / 关键词 / 分页）、详情、浏览计数；后台发布 / 编辑 / 下架 / 删除；来源地址与内容指纹双重去重 + 标题相似提示 | `articles.html` / `admin/articles.html` |
| 管理闭环 | 用户分页与启用禁用、角色调整；食材属性维护（八维 0-100 校验、名称唯一、停用）；规则只读展示；分析任务查看 | `admin/*.html` |
| 数据安全 | 密码哈希存储；全部 SQL 参数化（PreparedStatement）；Service 层归属校验（越权访问返回 403）；文章正文白名单过滤；管理操作写文件日志 | `JdbcHelper` / 各 Service 实现 / `TextUtil.sanitizeHtml` / `OperationLog` |
| 工程结构 | Model → DAO → Service → Servlet 四层 + Filter 层，无框架依赖；统一 JSON 协议与异常转换 | 见目录结构 |
| 页面体验 | 与设计基准页一致的响应式布局（1080px / 900px / 640px 断点）、加载/空/失败三态、字段级错误提示、防重复提交 | `css/app.css` + `js/*` |
| 对外可解释性 | 首页顶部导航悬停展开信息面板（分组条目 + 推荐卡片，交互参考 retool.com），条目直达产品说明页的对应章节；说明页公开计算口径、判定阈值、数据来源、置信度算法、真实案例与免责边界，游客无需登录即可阅读 | `index.html` / `features.html` / `js/common.js`（`FL.navPanels`） |

---

## 六、分层架构与关键实现

### 6.1 分层职责

- **Model**：数据库表映射与业务数据对象。JSON 列在实体中以 `transient` 字段承载，接口输出的是解析后的结构化对象；
  `passwordHash` 同样是 `transient`，任何接口都不会返回密码哈希。
- **DAO**：只做持久化。所有 SQL 经 `JdbcHelper` 以 `PreparedStatement` 参数化执行（含 `LIMIT ? OFFSET ?`），
  连接由 Service 传入，DAO **不关闭/不提交**连接，便于上层控制事务。
- **Service**：业务规则与事务边界。`BaseService` 提供 `read / write / writeVoid` 模板：
  写入失败整体回滚，保证「配方 + 明细」「任务 + 结果」这类组合写入的一致性；所有读写都校验数据归属。
- **Servlet**：`BaseServlet` 统一处理参数读取、登录/管理员校验、异常转换与 JSON 输出，业务逻辑不写在 Servlet 中。
- **Filter**：`EncodingFilter`（UTF-8 + 安全响应头）→ `AuthFilter`（页面跳登录页 / 接口 401）→ `AdminFilter`（后台 403）。

### 6.2 风味分析口径（可解释、可复现）

```
食材占比 = 该食材用量 / 参与计算的总用量
维度得分 = Σ（食材占比 × 该食材该维度属性值）         // 加权平均，消除配方总量差异
偏移比例 =（目标得分 - 基准得分）/ max(|基准得分|, 1) × 100%   // 设最小阈值，避免除零放大
```

- 缺少风味属性数据的食材**不参与加权**（既不进分子也不进分母），其用量占比会降低「数据完整度」；
- 置信度 = 数据完整度 × 0.8 + 规则覆盖率 × 0.2（规则覆盖率按命中 3 条饱和），结果页会同时展示完整度与置信度；
- 变化幅度小于 ±5% 视为「稳定」，其余标记为「上升 / 下降」，命中规则的维度额外标记「超出建议范围」；
- 选择目标区域时，基准与目标向量**同时**按该区域八维权重折算，保证比较在同一口径下进行；
- 每条建议都带规则编码、触发原因、参考幅度与风险提示，避免「黑盒结论」。

### 6.3 分析快照

分析任务保存 `baseline_snapshot_json` 与 `target_snapshot_json`，快照内**固化每个食材当时的八维属性**。
因此即使管理员事后调整了食材属性，历史结果仍然可复现 —— 这是「过程沉淀」的关键设计。

---

## 七、文档索引

| 文档 | 内容 |
|---|---|
| `docs/接口说明.md` | 全部接口的路径、方法、参数、响应结构、权限要求与错误码 |
| `docs/答辩演示脚本.md` | 10 分钟演示流程、每步操作与讲解要点、常见提问应答 |
| `features.html`（页面内） | 产品说明：计算口径、判定阈值表、数据来源、置信度算法、真实案例逐维解读、规则清单、免责边界与常见问题 |
| `database/flavor_logic.sql` | 数据库 DDL（含表注释、索引、外键与基础数据） |
| 项目根目录《项目介绍》《功能介绍》《UI 设计复刻规范》 | 需求、功能边界与 UI 复刻规范 |

---

## 八、常见问题

**Q1. 启动后页面 404 / 502？**
确认访问路径带上下文：`http://localhost:8080/flavorlogic/`；查看 `tomcat-runtime/logs/catalina.*.log`。

**Q2. 端口 8080 被占用？**
修改 `tomcat-runtime/conf/server.xml` 中 `<Connector port="8080" ...>`，或先执行 `stop-flavorlogic.ps1`。

**Q3. 中文显示成问号或乱码？**
检查三处：`db.properties` 的 `characterEncoding=UTF-8`、数据库/表的 `utf8mb4`、页面 `<meta charset="UTF-8">`。
可用 `SHOW CREATE DATABASE flavor_logic;` 确认排序规则为 `utf8mb4_general_ci`。

**Q4. 执行建库脚本报 `Unknown collation: utf8mb4_0900_ai_ci`？**
脚本已统一为 `utf8mb4_general_ci`（5.7/8.0 通用）；若你手动改成了 8.0 专有排序规则，请改回。

**Q5. 数据库约束好像没生效？**
MySQL 5.7 只解析 CHECK 约束但不执行（8.0.16+ 才强制）。属性和枚举的合法性由 Service 层校验保证，
这一点在答辩中应主动说明，不要声称「数据库已经约束」。

**Q6. Maven 无法下载依赖？**
项目依赖仅 3 个（`javax.servlet-api`、`gson`、`mysql-connector-j`），可配置阿里云镜像；
内网环境可先用 `mvn -o` 离线模式，或用 IDE 自带的 Maven 仓库。

**Q7. 演示数据脚本报「应用未就绪」？**
先把应用启动起来（脚本需要调用接口写入数据），再执行 `database/seed-demo-data.ps1`。

**Q8. 改了 CSS/JS 之后页面没变化？**
Tomcat 默认不给静态文件发 `Cache-Control`，浏览器会按「启发式新鲜度」把 css/js 缓存数小时，普通刷新也不会回源。
本项目已在 `WEB-INF/web.xml` 中启用 Tomcat 自带的 `ExpiresFilter`：css/js/html 为 `max-age=0`
（每次回源校验，未修改返回 304、已修改立即生效），图片保留 30 天缓存。
**注意**：该过滤器的参数名首字母必须大写（`ExpiresByType text/css`），写成小写会被当作未知参数静默忽略——
这一点已实测确认（启动日志会打印 `忽略值为[...]的未知参数[expiresByType text/css]`）。
若你把它换到别的容器（如 Jetty）或删掉该过滤器，改动后请用 `Ctrl+F5` 强制刷新。

---

## 九、交付物清单

- 源代码：`src/main/java`（87 个类）+ `src/main/webapp`（16 个页面 + 17 个脚本 + 设计系统样式）
- 构建脚本：`pom.xml`、`start-flavorlogic.ps1`、`stop-flavorlogic.ps1`
- 数据库：`database/flavor_logic.sql`（DDL + 基础数据）、`database/demo_data.sql`（演示数据快照）、
  `database/seed-demo-data.ps1`（演示数据初始化）
- 文档：本 README、`docs/接口说明.md`、`docs/答辩演示脚本.md`
