-- ============================================================
-- 项目：食之有理 FlavorLogic
-- 用途：第一阶段 Web 课设版 MySQL 建库、建表与基础数据脚本
-- 环境：MySQL 5.7+（本机 MySQL 5.7.37 验证）/ Navicat for MySQL
-- 字符集：utf8mb4 / 排序规则：utf8mb4_general_ci
--   · utf8mb4_general_ci 在 MySQL 5.5+ / 5.6 / 5.7 / 8.0 与 MariaDB 上均可用，是 5.7 中 utf8mb4 的默认排序规则；
--   · 若日后升级到 MySQL 8.0 并想用新版排序规则，可全局替换为 utf8mb4_0900_ai_ci（在 5.7 上会报 Unknown collation）；
--   · 注意：CHECK 约束在 MySQL 5.7 中「只解析、不生效」，8.0.16+ 才真正强制校验，字段合法性需由后端 Service 层兜底。
--
-- 使用方法：
-- 1. 在 Navicat 中打开“查询”；
-- 2. 载入本文件并执行全部语句；
-- 3. 脚本不会删除已有数据库或表，可安全重复执行建表部分；
-- 4. 管理员账号应由应用注册或单独初始化，禁止在 SQL 中保存明文密码。
-- ============================================================

-- ============================================================
-- 结构变更记录
-- ------------------------------------------------------------
-- 用途：登记每一次表结构变更。
-- 背景：本脚本使用 CREATE TABLE IF NOT EXISTS —— 表已存在时整句会被跳过，
--       因此「只改 CREATE 语句」无法给已有的表加字段（删表重建会丢数据）。
--       给已有数据库升级时，必须执行第 9 节的 ALTER TABLE 语句，并在此登记一笔。
-- 格式：日期 | 变更内容 | 涉及对象 / 脚本位置
-- ============================================================
-- 2026-09-15  首次建库，11 张表 + 演示基础数据                  第 1 ~ 6 节
-- 2026-09-18  新增配料调配知识库 3 张表（表数 11 → 14）          第 7 节
--             flavor_knowledge / ingredient_pairing / ai_call_log
-- 2026-09-18  食材由 18 条扩充至 44 条                          第 8.1 节
-- 2026-09-18  新增调配知识卡 64 张、配料搭配关系 22 条            第 8.2 / 8.3 节
-- 2026-09-18  停用 4 条定位不符的知识卡（启用数 64 → 60）          第 9.4 节
--             UK-FAT-004 / UK-FAT-008 / UK-GEN-001 / UK-GEN-006
--             原因：candidate_ingredient 填的不是真实配料，被引擎当成候选配料输出
-- 2026-09-18  知识卡新增 trigger_constraint（附加触发条件）字段      第 9.3 节
--             并把 UK-GEN-005「过敏原替代」设为 ALLERGEN_ONLY       第 9.4 节
--             原因：该卡只在用户填了过敏原约束时才成立，原标为 GENERAL 导致无关场景也出现
-- 2026-09-19  新增目标模板表 goal_target（表数 14 → 15）            第 3 节
--             50 条规则 ＝ 6 个研发目标 × 8 维（48）＋ 2 条成分层动作
--             用途：配方生成器的输入契约，把研发目标展开成八维目标向量
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `flavor_logic`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `flavor_logic`;

-- ============================================================
-- 1. 用户与权限
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户编号',
    `username`        VARCHAR(50) NOT NULL COMMENT '登录名',
    `password_hash`   VARCHAR(255) NOT NULL COMMENT '密码哈希，禁止存储明文密码',
    `nickname`        VARCHAR(50) NOT NULL COMMENT '用户昵称',
    `role`            VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER普通用户，ADMIN管理员',
    `status`          TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常，0禁用',
    `last_login_at`   DATETIME NULL COMMENT '最近登录时间',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_username` (`username`),
    KEY `idx_sys_user_role_status` (`role`, `status`),
    CONSTRAINT `chk_sys_user_role` CHECK (`role` IN ('USER', 'ADMIN')),
    CONSTRAINT `chk_sys_user_status` CHECK (`status` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统用户表';

-- ============================================================
-- 2. 研发知识文章
-- ============================================================

CREATE TABLE IF NOT EXISTS `article_category` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类编号',
    `name`            VARCHAR(50) NOT NULL COMMENT '分类名称',
    `description`     VARCHAR(255) NULL COMMENT '分类说明',
    `sort_no`         INT NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `status`          TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_article_category_name` (`name`),
    KEY `idx_article_category_status_sort` (`status`, `sort_no`),
    CONSTRAINT `chk_article_category_status` CHECK (`status` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文章分类表';

CREATE TABLE IF NOT EXISTS `knowledge_article` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文章编号',
    `category_id`     BIGINT UNSIGNED NULL COMMENT '文章分类编号',
    `author_id`       BIGINT UNSIGNED NULL COMMENT '发布人编号',
    `title`           VARCHAR(200) NOT NULL COMMENT '文章标题',
    `summary`         VARCHAR(500) NULL COMMENT '文章摘要',
    `content`         LONGTEXT NOT NULL COMMENT '文章正文',
    `cover_url`       VARCHAR(500) NULL COMMENT '封面地址',
    `source_name`     VARCHAR(100) NULL COMMENT '来源名称',
    `source_url`      VARCHAR(500) NULL COMMENT '原文地址',
    `content_hash`    CHAR(64) NULL COMMENT '清洗后内容的SHA-256指纹',
    `status`          TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0草稿，1发布，2下架，3删除',
    `view_count`      INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '浏览次数',
    `published_at`    DATETIME NULL COMMENT '发布时间',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_knowledge_article_source_url` (`source_url`),
    UNIQUE KEY `uk_knowledge_article_content_hash` (`content_hash`),
    KEY `idx_article_category_status_time` (`category_id`, `status`, `published_at`),
    KEY `idx_article_author` (`author_id`),
    KEY `idx_article_title` (`title`),
    CONSTRAINT `fk_article_category`
        FOREIGN KEY (`category_id`) REFERENCES `article_category` (`id`)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT `fk_article_author`
        FOREIGN KEY (`author_id`) REFERENCES `sys_user` (`id`)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT `chk_knowledge_article_status` CHECK (`status` IN (0, 1, 2, 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='研发知识文章表';

-- ============================================================
-- 3. 食材、区域画像与分析规则
-- ============================================================

CREATE TABLE IF NOT EXISTS `ingredient` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '食材编号',
    `name`            VARCHAR(100) NOT NULL COMMENT '食材名称',
    `category`        VARCHAR(50) NULL COMMENT '食材分类',
    `default_unit`    VARCHAR(20) NOT NULL DEFAULT 'g' COMMENT '默认用量单位',
    `sweet`           DECIMAL(6,2) NOT NULL DEFAULT 0.00 COMMENT '甜度相对值，0-100',
    `salty`           DECIMAL(6,2) NOT NULL DEFAULT 0.00 COMMENT '咸度相对值，0-100',
    `sour`            DECIMAL(6,2) NOT NULL DEFAULT 0.00 COMMENT '酸度相对值，0-100',
    `bitter`          DECIMAL(6,2) NOT NULL DEFAULT 0.00 COMMENT '苦度相对值，0-100',
    `umami`           DECIMAL(6,2) NOT NULL DEFAULT 0.00 COMMENT '鲜味相对值，0-100',
    `spicy`           DECIMAL(6,2) NOT NULL DEFAULT 0.00 COMMENT '辣度相对值，0-100',
    `numbing`         DECIMAL(6,2) NOT NULL DEFAULT 0.00 COMMENT '麻度相对值，0-100',
    `fat_aroma`       DECIMAL(6,2) NOT NULL DEFAULT 0.00 COMMENT '脂香相对值，0-100',
    `description`     VARCHAR(500) NULL COMMENT '食材及数据说明',
    `data_source`     VARCHAR(255) NULL COMMENT '数据来源或演示数据说明',
    `status`          TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ingredient_name` (`name`),
    KEY `idx_ingredient_category_status` (`category`, `status`),
    CONSTRAINT `chk_ingredient_sweet` CHECK (`sweet` BETWEEN 0 AND 100),
    CONSTRAINT `chk_ingredient_salty` CHECK (`salty` BETWEEN 0 AND 100),
    CONSTRAINT `chk_ingredient_sour` CHECK (`sour` BETWEEN 0 AND 100),
    CONSTRAINT `chk_ingredient_bitter` CHECK (`bitter` BETWEEN 0 AND 100),
    CONSTRAINT `chk_ingredient_umami` CHECK (`umami` BETWEEN 0 AND 100),
    CONSTRAINT `chk_ingredient_spicy` CHECK (`spicy` BETWEEN 0 AND 100),
    CONSTRAINT `chk_ingredient_numbing` CHECK (`numbing` BETWEEN 0 AND 100),
    CONSTRAINT `chk_ingredient_fat_aroma` CHECK (`fat_aroma` BETWEEN 0 AND 100),
    CONSTRAINT `chk_ingredient_status` CHECK (`status` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='食材及八维风味属性表';

CREATE TABLE IF NOT EXISTS `region_profile` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '区域画像编号',
    `region_code`     VARCHAR(30) NOT NULL COMMENT '区域编码',
    `region_name`     VARCHAR(50) NOT NULL COMMENT '区域名称',
    `sweet_weight`    DECIMAL(6,3) NOT NULL DEFAULT 1.000 COMMENT '甜度偏好权重',
    `salty_weight`    DECIMAL(6,3) NOT NULL DEFAULT 1.000 COMMENT '咸度偏好权重',
    `sour_weight`     DECIMAL(6,3) NOT NULL DEFAULT 1.000 COMMENT '酸度偏好权重',
    `bitter_weight`   DECIMAL(6,3) NOT NULL DEFAULT 1.000 COMMENT '苦度偏好权重',
    `umami_weight`    DECIMAL(6,3) NOT NULL DEFAULT 1.000 COMMENT '鲜味偏好权重',
    `spicy_weight`    DECIMAL(6,3) NOT NULL DEFAULT 1.000 COMMENT '辣度偏好权重',
    `numbing_weight`  DECIMAL(6,3) NOT NULL DEFAULT 1.000 COMMENT '麻度偏好权重',
    `fat_aroma_weight` DECIMAL(6,3) NOT NULL DEFAULT 1.000 COMMENT '脂香偏好权重',
    `description`     VARCHAR(500) NULL COMMENT '区域画像说明',
    `data_source`     VARCHAR(255) NULL COMMENT '数据来源或演示数据说明',
    `status`          TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_region_profile_code` (`region_code`),
    UNIQUE KEY `uk_region_profile_name` (`region_name`),
    KEY `idx_region_profile_status` (`status`),
    CONSTRAINT `chk_region_profile_status` CHECK (`status` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='区域口味画像表';

CREATE TABLE IF NOT EXISTS `flavor_rule` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '规则编号',
    `rule_code`       VARCHAR(50) NOT NULL COMMENT '规则编码',
    `rule_name`       VARCHAR(100) NOT NULL COMMENT '规则名称',
    `goal_type`       VARCHAR(30) NOT NULL DEFAULT 'GENERAL' COMMENT '适用目标类型',
    `priority`        INT NOT NULL DEFAULT 100 COMMENT '规则优先级，越小越先执行',
    `condition_json`  JSON NOT NULL COMMENT '触发条件',
    `action_json`     JSON NOT NULL COMMENT '建议动作与模板',
    `explanation`     VARCHAR(500) NULL COMMENT '可解释说明',
    `status`          TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flavor_rule_code` (`rule_code`),
    KEY `idx_flavor_rule_goal_status_priority` (`goal_type`, `status`, `priority`),
    CONSTRAINT `chk_flavor_rule_status` CHECK (`status` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='风味补偿规则表';

-- ------------------------------------------------------------
-- 目标模板表：把「研发目标」展开成八维目标向量与成分层动作的规则。
-- 这是配方生成器的输入契约（见《项目介绍》4.3）。**规则写在数据里而不是代码里**——
-- 新增研发目标、调整某个目标的维度意图，都只需要改这张表，不需要改 Java。
--
-- target_kind 两种取值：
--   FLAVOR    —— 针对八个风味维度的意图，构成「目标风味向量」
--   COMPONENT —— 针对某一类食材的成分层动作，告诉合成器"哪类原料要减"
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `goal_target` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '目标项编号',
    `goal_type`   VARCHAR(30)  NOT NULL COMMENT '研发目标类型：REPLACE、REDUCE_SUGAR、REDUCE_FAT、ADJUST_STIMULATION、REGION_ADAPT、GENERAL',
    `target_kind` VARCHAR(20)  NOT NULL COMMENT '目标种类：FLAVOR风味维度、COMPONENT原料类目',
    `target_key`  VARCHAR(50)  NOT NULL COMMENT 'FLAVOR 时为八维键；COMPONENT 时为食材类目名',
    `intent`      VARCHAR(20)  NOT NULL DEFAULT 'KEEP' COMMENT '意图：KEEP保持基准、CHANGE定向到目标值、ALLOW允许自由偏离',
    `magnitude`   DECIMAL(6,3) NULL COMMENT 'CHANGE 时相对基准的倍数；NULL 表示按区域画像权重折算',
    `weight`      DECIMAL(6,3) NOT NULL DEFAULT 1.000 COMMENT '优化权重，0 表示不参与优化',
    `note`        VARCHAR(255) NULL COMMENT '该行口径说明，用于结果页展示"为什么这样定目标"',
    `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_goal_target` (`goal_type`, `target_kind`, `target_key`),
    KEY `idx_goal_target_goal_status` (`goal_type`, `status`),
    CONSTRAINT `chk_goal_target_status` CHECK (`status` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='研发目标的目标项定义表（目标模板）';

-- ------------------------------------------------------------
-- 目标模板数据：6 个研发目标 × 8 维 ＝ 48 条风味层规则 ＋ 2 条成分层规则。
--
-- 两条不能随手改的语义判断（依据见《项目介绍》4.3.2）：
--   · REDUCE_SUGAR 控糖：成分上减「甜味料」，风味上要求 sweet「保持」——
--     因为糖可以被代糖在甜度上复现，所以要求保持，而不是允许降。
--   · REDUCE_FAT 控脂：成分上减「油脂」，风味上把 fat_aroma 设为「放行」——
--     因为脂香无法被非脂原料完整复原，只能尽量补，不能假装保得住。
--
-- 为什么成分层只需要 2 条：ADJUST_STIMULATION 与 REGION_ADAPT 的风味层已经是
-- CHANGE（有明确的升降目标），合成器可据此自行推导该动哪些原料；
-- 只有风味层为 KEEP / ALLOW、缺少"要减少"信号的目标，才需要成分层显式补一条。
-- ------------------------------------------------------------
INSERT IGNORE INTO `goal_target`
    (`goal_type`, `target_kind`, `target_key`, `intent`, `magnitude`, `weight`, `note`) VALUES
    -- 原料替换：换料但味道尽量别变，这是该场景的全部意义
    ('REPLACE','FLAVOR','sweet',    'KEEP',NULL,1.000,'换料后尽量维持基准甜度'),
    ('REPLACE','FLAVOR','salty',    'KEEP',NULL,1.000,'换料后尽量维持基准咸度'),
    ('REPLACE','FLAVOR','sour',     'KEEP',NULL,1.000,'换料后尽量维持基准酸度'),
    ('REPLACE','FLAVOR','bitter',   'KEEP',NULL,1.000,'换料后尽量不让苦味上升'),
    ('REPLACE','FLAVOR','umami',    'KEEP',NULL,1.000,'换料后尽量维持基准鲜味'),
    ('REPLACE','FLAVOR','spicy',    'KEEP',NULL,1.000,'换料后尽量维持基准辣度'),
    ('REPLACE','FLAVOR','numbing',  'KEEP',NULL,1.000,'换料后尽量维持基准麻度'),
    ('REPLACE','FLAVOR','fat_aroma','KEEP',NULL,1.000,'换料后尽量维持基准脂香'),
    -- 控糖：成分上减甜味料，风味上保持甜度（靠代糖补回）
    ('REDUCE_SUGAR','FLAVOR','sweet',    'KEEP',NULL,1.000,'甜度要求保持：成分减糖后由代糖把甜度补回，这是控糖唯一可行路径'),
    ('REDUCE_SUGAR','FLAVOR','salty',    'KEEP',NULL,1.000,'减糖会削弱甜味对咸味的缓和作用，咸度需保持以免更咸'),
    ('REDUCE_SUGAR','FLAVOR','sour',     'KEEP',NULL,1.000,'甜度下降会放大酸感，酸度需保持'),
    ('REDUCE_SUGAR','FLAVOR','bitter',   'KEEP',NULL,1.000,'代糖往往带来后苦，苦味需保持以免叠加'),
    ('REDUCE_SUGAR','FLAVOR','umami',    'KEEP',NULL,1.000,'整体风味强度下降时鲜味需保持'),
    ('REDUCE_SUGAR','FLAVOR','spicy',    'KEEP',NULL,1.000,'保持基准辣度'),
    ('REDUCE_SUGAR','FLAVOR','numbing',  'KEEP',NULL,1.000,'保持基准麻度'),
    ('REDUCE_SUGAR','FLAVOR','fat_aroma','KEEP',NULL,1.000,'保持基准脂香'),
    ('REDUCE_SUGAR','COMPONENT','甜味料','REDUCE',0.500,1.000,'成分层动作：甜味料（如白砂糖）按 0.5 倍减量。倍率是可用数据调整的默认起点，不代表企业真实配方'),
    -- 控脂：成分上减油脂，风味上允许脂香下降
    ('REDUCE_FAT','FLAVOR','sweet',    'KEEP',NULL,1.000,'保持基准甜度'),
    ('REDUCE_FAT','FLAVOR','salty',    'KEEP',NULL,1.000,'减油后咸味感知会被放大，咸度需保持以免过咸'),
    ('REDUCE_FAT','FLAVOR','sour',     'KEEP',NULL,1.000,'保持基准酸度'),
    ('REDUCE_FAT','FLAVOR','bitter',   'KEEP',NULL,1.000,'保持基准苦味'),
    ('REDUCE_FAT','FLAVOR','umami',    'KEEP',NULL,1.000,'脂香下降会让鲜味显得单薄，鲜味需保持'),
    ('REDUCE_FAT','FLAVOR','spicy',    'KEEP',NULL,1.000,'减油后辣感更冲，辣度需保持以免刺激度上升'),
    ('REDUCE_FAT','FLAVOR','numbing',  'KEEP',NULL,1.000,'保持基准麻度'),
    ('REDUCE_FAT','FLAVOR','fat_aroma','ALLOW',NULL,0.000,'脂香放行：减油后脂香物理上无法完整复原，只能尽量减少，不能设定为必须达到的值'),
    ('REDUCE_FAT','COMPONENT','油脂','REDUCE',0.600,1.000,'成分层动作：油脂（如菜籽油、鸡油）按 0.6 倍减量。倍率是可用数据调整的默认起点，不代表企业真实配方'),
    -- 降刺激：辣、麻定向降到基准的 80%（可直接编辑 magnitude 调整默认降幅）
    ('ADJUST_STIMULATION','FLAVOR','sweet',    'KEEP',  NULL,1.000,'保持基准甜度'),
    ('ADJUST_STIMULATION','FLAVOR','salty',    'KEEP',  NULL,1.000,'保持基准咸度'),
    ('ADJUST_STIMULATION','FLAVOR','sour',     'KEEP',  NULL,1.000,'保持基准酸度'),
    ('ADJUST_STIMULATION','FLAVOR','bitter',   'KEEP',  NULL,1.000,'降低香辛料用量后苦味可能变化，需保持'),
    ('ADJUST_STIMULATION','FLAVOR','umami',    'KEEP',  NULL,1.000,'刺激度下降会让风味显得平淡，鲜味需保持'),
    ('ADJUST_STIMULATION','FLAVOR','spicy',    'CHANGE',0.800,1.000,'辣度定向降到基准的 80%'),
    ('ADJUST_STIMULATION','FLAVOR','numbing',  'CHANGE',0.800,1.000,'麻度定向降到基准的 80%'),
    ('ADJUST_STIMULATION','FLAVOR','fat_aroma','KEEP',  NULL,1.000,'保持基准脂香'),
    -- 区域适配：八维全部定向到「基准 × 区域权重」，magnitude 留空表示按所选区域画像折算
    ('REGION_ADAPT','FLAVOR','sweet',    'CHANGE',NULL,1.000,'目标值＝基准甜度 × 区域甜度权重'),
    ('REGION_ADAPT','FLAVOR','salty',    'CHANGE',NULL,1.000,'目标值＝基准咸度 × 区域咸度权重'),
    ('REGION_ADAPT','FLAVOR','sour',     'CHANGE',NULL,1.000,'目标值＝基准酸度 × 区域酸度权重'),
    ('REGION_ADAPT','FLAVOR','bitter',   'CHANGE',NULL,1.000,'目标值＝基准苦度 × 区域苦度权重'),
    ('REGION_ADAPT','FLAVOR','umami',    'CHANGE',NULL,1.000,'目标值＝基准鲜味 × 区域鲜味权重'),
    ('REGION_ADAPT','FLAVOR','spicy',    'CHANGE',NULL,1.000,'目标值＝基准辣度 × 区域辣度权重，并按 0-100 截断'),
    ('REGION_ADAPT','FLAVOR','numbing',  'CHANGE',NULL,1.000,'目标值＝基准麻度 × 区域麻度权重，并按 0-100 截断'),
    ('REGION_ADAPT','FLAVOR','fat_aroma','CHANGE',NULL,1.000,'目标值＝基准脂香 × 区域脂香权重'),
    -- 综合分析：无明确目标，只观察稳定性
    ('GENERAL','FLAVOR','sweet',    'KEEP',NULL,1.000,'综合场景无明确目标，以维持基准为准'),
    ('GENERAL','FLAVOR','salty',    'KEEP',NULL,1.000,'综合场景无明确目标，以维持基准为准'),
    ('GENERAL','FLAVOR','sour',     'KEEP',NULL,1.000,'综合场景无明确目标，以维持基准为准'),
    ('GENERAL','FLAVOR','bitter',   'KEEP',NULL,1.000,'综合场景无明确目标，以维持基准为准'),
    ('GENERAL','FLAVOR','umami',    'KEEP',NULL,1.000,'综合场景无明确目标，以维持基准为准'),
    ('GENERAL','FLAVOR','spicy',    'KEEP',NULL,1.000,'综合场景无明确目标，以维持基准为准'),
    ('GENERAL','FLAVOR','numbing',  'KEEP',NULL,1.000,'综合场景无明确目标，以维持基准为准'),
    ('GENERAL','FLAVOR','fat_aroma','KEEP',NULL,1.000,'综合场景无明确目标，以维持基准为准');

-- ============================================================
-- 4. 配方管理
-- ============================================================

CREATE TABLE IF NOT EXISTS `recipe` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '配方编号',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '所属用户编号',
    `name`            VARCHAR(100) NOT NULL COMMENT '配方名称',
    `product_type`    VARCHAR(50) NULL COMMENT '产品类型',
    `process_note`    TEXT NULL COMMENT '工艺说明',
    `remark`          VARCHAR(500) NULL COMMENT '配方备注',
    `version_no`      INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '配方版本号',
    `status`          TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常，0已删除',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_recipe_user_status_time` (`user_id`, `status`, `updated_at`),
    KEY `idx_recipe_name` (`name`),
    CONSTRAINT `fk_recipe_user`
        FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT `chk_recipe_status` CHECK (`status` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='配方主表';

CREATE TABLE IF NOT EXISTS `recipe_item` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '配方明细编号',
    `recipe_id`       BIGINT UNSIGNED NOT NULL COMMENT '配方编号',
    `ingredient_id`   BIGINT UNSIGNED NULL COMMENT '食材编号，历史自定义食材可为空',
    `ingredient_name` VARCHAR(100) NOT NULL COMMENT '食材名称快照',
    `amount`          DECIMAL(12,3) NOT NULL COMMENT '食材用量',
    `unit`            VARCHAR(20) NOT NULL DEFAULT 'g' COMMENT '用量单位',
    `cost_per_unit`   DECIMAL(12,4) NULL COMMENT '单位成本，可选',
    `sort_no`         INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_recipe_item_recipe_sort` (`recipe_id`, `sort_no`),
    KEY `idx_recipe_item_ingredient` (`ingredient_id`),
    CONSTRAINT `fk_recipe_item_recipe`
        FOREIGN KEY (`recipe_id`) REFERENCES `recipe` (`id`)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT `fk_recipe_item_ingredient`
        FOREIGN KEY (`ingredient_id`) REFERENCES `ingredient` (`id`)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT `chk_recipe_item_amount` CHECK (`amount` > 0),
    CONSTRAINT `chk_recipe_item_cost` CHECK (`cost_per_unit` IS NULL OR `cost_per_unit` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='配方食材明细表';

-- ============================================================
-- 5. 风味分析任务、结果与反馈
-- ============================================================

CREATE TABLE IF NOT EXISTS `analysis_task` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分析任务编号',
    `user_id`             BIGINT UNSIGNED NOT NULL COMMENT '创建用户编号',
    `recipe_id`           BIGINT UNSIGNED NULL COMMENT '基准配方编号',
    `region_profile_id`   BIGINT UNSIGNED NULL COMMENT '目标区域画像编号',
    `task_name`           VARCHAR(100) NOT NULL COMMENT '任务名称',
    `goal_type`           VARCHAR(30) NOT NULL COMMENT '目标：REPLACE、REDUCE_SUGAR、REDUCE_FAT、ADJUST_STIMULATION、REGION_ADAPT',
    `target_region`       VARCHAR(50) NULL COMMENT '目标区域名称快照',
    `constraint_json`     JSON NULL COMMENT '成本、健康、过敏原等约束',
    `baseline_snapshot_json` JSON NOT NULL COMMENT '分析时的基准配方完整快照',
    `target_snapshot_json`   JSON NOT NULL COMMENT '分析时的目标配方完整快照',
    `status`              VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、RUNNING、COMPLETED、FAILED',
    `error_message`       VARCHAR(500) NULL COMMENT '失败原因',
    `created_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `started_at`          DATETIME NULL COMMENT '开始时间',
    `finished_at`         DATETIME NULL COMMENT '完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_analysis_task_user_status_time` (`user_id`, `status`, `created_at`),
    KEY `idx_analysis_task_recipe` (`recipe_id`),
    KEY `idx_analysis_task_goal_type` (`goal_type`),
    KEY `idx_analysis_task_region` (`region_profile_id`),
    CONSTRAINT `fk_analysis_task_user`
        FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT `fk_analysis_task_recipe`
        FOREIGN KEY (`recipe_id`) REFERENCES `recipe` (`id`)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT `fk_analysis_task_region`
        FOREIGN KEY (`region_profile_id`) REFERENCES `region_profile` (`id`)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT `chk_analysis_task_goal` CHECK (`goal_type` IN (
        'REPLACE', 'REDUCE_SUGAR', 'REDUCE_FAT',
        'ADJUST_STIMULATION', 'REGION_ADAPT', 'GENERAL'
    )),
    CONSTRAINT `chk_analysis_task_status` CHECK (`status` IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='风味分析任务表';

CREATE TABLE IF NOT EXISTS `analysis_result` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分析结果编号',
    `task_id`         BIGINT UNSIGNED NOT NULL COMMENT '分析任务编号',
    `baseline_json`   JSON NOT NULL COMMENT '基准八维风味向量',
    `target_json`     JSON NOT NULL COMMENT '目标八维风味向量',
    `delta_json`      JSON NOT NULL COMMENT '绝对偏移和相对变化数据',
    `suggestion_json` JSON NULL COMMENT '建议集合快照',
    `confidence`      DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '规则结果置信度，0-100',
    `data_completeness` DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '食材数据完整度，0-100',
    `summary`         VARCHAR(1000) NULL COMMENT '结果摘要',
    `explanation`     TEXT NULL COMMENT '可解释说明与免责声明',
    `engine_version`  VARCHAR(30) NOT NULL DEFAULT 'RULE-1.0' COMMENT '分析引擎版本',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_analysis_result_task` (`task_id`),
    CONSTRAINT `fk_analysis_result_task`
        FOREIGN KEY (`task_id`) REFERENCES `analysis_task` (`id`)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT `chk_analysis_result_confidence` CHECK (`confidence` BETWEEN 0 AND 100),
    CONSTRAINT `chk_analysis_result_completeness` CHECK (`data_completeness` BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='风味分析结果表';

CREATE TABLE IF NOT EXISTS `analysis_feedback` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '反馈编号',
    `task_id`         BIGINT UNSIGNED NOT NULL COMMENT '分析任务编号',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '反馈用户编号',
    `helpful`         TINYINT NULL COMMENT '是否有帮助：1有帮助，0无帮助，NULL未选择',
    `rating`          TINYINT NULL COMMENT '评分：1-5',
    `comment`         VARCHAR(1000) NULL COMMENT '文字反馈',
    `trial_result`    VARCHAR(20) NULL COMMENT '试产结果：NOT_TRIED、PASSED、PARTIAL、FAILED',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_feedback_task_user` (`task_id`, `user_id`),
    KEY `idx_feedback_user_time` (`user_id`, `created_at`),
    CONSTRAINT `fk_feedback_task`
        FOREIGN KEY (`task_id`) REFERENCES `analysis_task` (`id`)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT `fk_feedback_user`
        FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT `chk_feedback_helpful` CHECK (`helpful` IS NULL OR `helpful` IN (0, 1)),
    CONSTRAINT `chk_feedback_rating` CHECK (`rating` IS NULL OR `rating` BETWEEN 1 AND 5),
    CONSTRAINT `chk_feedback_trial_result` CHECK (
        `trial_result` IS NULL OR `trial_result` IN ('NOT_TRIED', 'PASSED', 'PARTIAL', 'FAILED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分析效果与试产反馈表';

-- ============================================================
-- 6. 第一阶段演示基础数据
-- 说明：以下数据用于功能演示和规则验证，不代表实验室检测结果。
-- ============================================================

INSERT IGNORE INTO `article_category`
    (`name`, `description`, `sort_no`, `status`)
VALUES
    ('风味科学', '味觉、香气与风味基础知识', 10, 1),
    ('原料知识', '食品原料及其风味特征', 20, 1),
    ('健康化改造', '控糖、控脂和减钠相关知识', 30, 1),
    ('区域口味', '不同区域的口味偏好与适配思路', 40, 1),
    ('研发案例', '配方调整与试产案例', 50, 1);

INSERT IGNORE INTO `ingredient`
    (`name`, `category`, `default_unit`, `sweet`, `salty`, `sour`, `bitter`, `umami`, `spicy`, `numbing`, `fat_aroma`, `description`, `data_source`, `status`)
VALUES
    ('白砂糖', '甜味料', 'g', 100, 0, 0, 0, 0, 0, 0, 0, '提供直接甜味', '课设演示相对评分', 1),
    ('食盐', '基础调味料', 'g', 0, 100, 0, 0, 8, 0, 0, 0, '提供咸味并增强整体风味感知', '课设演示相对评分', 1),
    ('白醋', '酸味料', 'ml', 0, 0, 95, 2, 0, 0, 0, 0, '提供明显酸味', '课设演示相对评分', 1),
    ('酱油', '复合调味料', 'ml', 5, 75, 8, 5, 65, 0, 0, 8, '提供咸味、鲜味和发酵香气', '课设演示相对评分', 1),
    ('味精', '增鲜剂', 'g', 0, 8, 0, 0, 100, 0, 0, 0, '提供直接鲜味', '课设演示相对评分', 1),
    ('酵母抽提物', '增鲜剂', 'g', 2, 10, 1, 5, 88, 0, 0, 8, '提供复合鲜味与一定醇厚感', '课设演示相对评分', 1),
    ('辣椒粉', '香辛料', 'g', 2, 1, 1, 8, 2, 95, 0, 5, '提供辣味及辣椒香气', '课设演示相对评分', 1),
    ('花椒粉', '香辛料', 'g', 1, 0, 1, 12, 1, 15, 100, 6, '提供麻感和花椒香气', '课设演示相对评分', 1),
    ('菜籽油', '油脂', 'ml', 0, 0, 0, 2, 0, 0, 0, 88, '提供油脂载香与脂香', '课设演示相对评分', 1),
    ('鸡油', '油脂', 'g', 0, 1, 0, 1, 12, 0, 0, 100, '提供动物脂香和一定鲜味', '课设演示相对评分', 1),
    ('鸡胸肉', '肉类', 'g', 0, 2, 0, 1, 48, 0, 0, 25, '低脂肉类原料，鲜味中等', '课设演示相对评分', 1),
    ('牛肉', '肉类', 'g', 0, 3, 0, 2, 72, 0, 0, 70, '鲜味和肉脂香较明显', '课设演示相对评分', 1),
    ('猪肉', '肉类', 'g', 0, 3, 0, 1, 58, 0, 0, 82, '具有较明显脂香和肉香', '课设演示相对评分', 1),
    ('赤藓糖醇', '代糖', 'g', 65, 0, 0, 3, 0, 0, 0, 0, '提供甜味并可能带来凉感', '课设演示相对评分', 1),
    ('甜菊糖苷', '代糖', 'g', 95, 0, 0, 18, 0, 0, 0, 0, '高甜度，过量时可能出现后苦味', '课设演示相对评分', 1),
    ('柠檬汁', '果蔬原料', 'ml', 8, 0, 90, 6, 0, 0, 0, 0, '提供清晰酸味和果香', '课设演示相对评分', 1),
    ('香菇粉', '天然增鲜原料', 'g', 2, 2, 0, 5, 82, 0, 0, 5, '提供菌菇鲜味', '课设演示相对评分', 1),
    ('洋葱粉', '香辛料', 'g', 28, 2, 1, 3, 18, 2, 0, 6, '提供甜香与一定鲜味', '课设演示相对评分', 1);

INSERT IGNORE INTO `region_profile`
    (`region_code`, `region_name`, `sweet_weight`, `salty_weight`, `sour_weight`, `bitter_weight`, `umami_weight`, `spicy_weight`, `numbing_weight`, `fat_aroma_weight`, `description`, `data_source`, `status`)
VALUES
    ('NATIONAL', '全国基准', 1.000, 1.000, 1.000, 1.000, 1.000, 1.000, 1.000, 1.000, '不进行区域偏好加权', '课设演示画像', 1),
    ('SOUTHWEST', '西南地区', 0.950, 1.050, 1.000, 0.950, 1.050, 1.200, 1.250, 1.050, '示例画像：偏麻辣与复合鲜香', '课设演示画像', 1),
    ('EAST_CHINA', '华东地区', 1.150, 0.950, 0.950, 0.900, 1.050, 0.800, 0.800, 0.950, '示例画像：适当提高甜度并降低刺激度', '课设演示画像', 1),
    ('SOUTH_CHINA', '华南地区', 1.000, 0.900, 1.000, 0.900, 1.150, 0.750, 0.750, 0.850, '示例画像：重视鲜味并适当降低油腻和刺激感', '课设演示画像', 1);

INSERT IGNORE INTO `flavor_rule`
    (`rule_code`, `rule_name`, `goal_type`, `priority`, `condition_json`, `action_json`, `explanation`, `status`)
VALUES
    (
        'UMAMI_DROP_GENERAL',
        '鲜味明显下降补偿',
        'GENERAL',
        10,
        JSON_OBJECT('dimension', 'umami', 'operator', 'LTE', 'changePercent', -10),
        JSON_OBJECT('actionType', 'ADD', 'candidates', JSON_ARRAY('酵母抽提物', '香菇粉'), 'referencePercentMin', 0.05, 'referencePercentMax', 0.30),
        '当鲜味相对下降达到10%时，从启用的高鲜味原料中给出候选补偿方向。',
        1
    ),
    (
        'FAT_AROMA_DROP_REDUCE_FAT',
        '控脂场景脂香下降提醒',
        'REDUCE_FAT',
        20,
        JSON_OBJECT('dimension', 'fat_aroma', 'operator', 'LTE', 'changePercent', -15),
        JSON_OBJECT('actionType', 'NOTICE', 'candidates', JSON_ARRAY('低用量增香方案', '工艺增香方案'), 'restoreFatDirectly', false),
        '控脂目标下脂香下降时，不直接建议恢复原油脂用量，应优先给出低用量或工艺补偿方向。',
        1
    ),
    (
        'SPICY_OVER_TARGET',
        '辣度超过目标范围',
        'ADJUST_STIMULATION',
        30,
        JSON_OBJECT('dimension', 'spicy', 'operator', 'GTE', 'changePercent', 15),
        JSON_OBJECT('actionType', 'REDUCE', 'ingredientCategory', '香辛料', 'referencePercentMin', 5, 'referencePercentMax', 20),
        '当辣度明显上升时，提示降低主要辣味原料并重新分析。',
        1
    ),
    (
        'SWEET_DROP_REDUCE_SUGAR',
        '减糖场景甜味平衡',
        'REDUCE_SUGAR',
        40,
        JSON_OBJECT('dimension', 'sweet', 'operator', 'LTE', 'changePercent', -20),
        JSON_OBJECT('actionType', 'NOTICE', 'candidates', JSON_ARRAY('代糖复配', '酸甜平衡调整'), 'requireAftertasteCheck', true),
        '减糖后甜味下降明显时提示代糖复配与后味风险，仍需感官试验验证。',
        1
    );

-- ============================================================
-- 7. 配料调配知识库与 AI 调用日志
--
-- 设计说明：
--   · flavor_knowledge 把「调配经验」拆成结构化知识卡，而不是长篇文章。
--     每张卡回答一个问题：某个风味维度在某个研发场景下缺了，可以补什么料、
--     补多少、为什么有效、有什么风险。规则引擎直接消费这张表，
--     因此建议可以从「建议加强鲜味」升级为具体的配料与用量区间。
--   · ingredient_pairing 记录配料之间的协同、掩蔽、拮抗与平衡关系，
--     这是「配料调配」区别于「单个食材属性」的核心知识。
--   · ai_call_log 记录每一次大模型调用的场景、引用与耗时。
--     AI 只承担解释层与检索层职责，日志用于答辩时说明其可控与可观测。
-- ============================================================

CREATE TABLE IF NOT EXISTS `flavor_knowledge` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '知识卡编号',
    `knowledge_code`       VARCHAR(60) NOT NULL COMMENT '知识卡编码',
    `title`                VARCHAR(150) NOT NULL COMMENT '知识卡标题',
    `dimension`            VARCHAR(30) NOT NULL COMMENT '目标风味维度：sweet、salty、sour、bitter、umami、spicy、numbing、fat_aroma',
    `goal_type`            VARCHAR(30) NOT NULL DEFAULT 'GENERAL' COMMENT '适用研发目标：REPLACE、REDUCE_SUGAR、REDUCE_FAT、ADJUST_STIMULATION、REGION_ADAPT、GENERAL',
    `trigger_constraint`   VARCHAR(30) NOT NULL DEFAULT 'NONE' COMMENT '附加触发条件：NONE无、ALLERGEN_ONLY仅当分析设置了过敏原约束时启用',
    `scene`                VARCHAR(200) NULL COMMENT '触发场景描述',
    `direction`            VARCHAR(20) NOT NULL DEFAULT 'ADD' COMMENT '补偿方向：ADD增加、REDUCE减少、REPLACE替换、NOTICE提示',
    `candidate_ingredient` VARCHAR(100) NOT NULL COMMENT '候选配料名称',
    `candidate_category`   VARCHAR(50) NULL COMMENT '候选配料类别',
    `amount_min`           DECIMAL(8,3) NULL COMMENT '建议用量下限',
    `amount_max`           DECIMAL(8,3) NULL COMMENT '建议用量上限',
    `amount_unit`          VARCHAR(20) NOT NULL DEFAULT '%' COMMENT '用量单位，默认为占配方总量百分比',
    `ratio_note`           VARCHAR(200) NULL COMMENT '复配或替代的比例说明',
    `mechanism`            VARCHAR(500) NULL COMMENT '起效原理，用于可解释输出',
    `risk_note`            VARCHAR(500) NULL COMMENT '风险与注意事项',
    `allergen_tags`        VARCHAR(255) NULL COMMENT '过敏原标签，逗号分隔',
    `evidence_source`      VARCHAR(255) NULL COMMENT '依据来源或演示数据说明',
    `keywords`             VARCHAR(255) NULL COMMENT '检索关键词，逗号分隔',
    `priority`             INT NOT NULL DEFAULT 100 COMMENT '匹配优先级，越小越优先',
    `status`               TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    `created_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flavor_knowledge_code` (`knowledge_code`),
    KEY `idx_knowledge_dimension_goal` (`dimension`, `goal_type`, `status`, `priority`),
    KEY `idx_knowledge_ingredient` (`candidate_ingredient`),
    CONSTRAINT `chk_knowledge_direction` CHECK (`direction` IN ('ADD', 'REDUCE', 'REPLACE', 'NOTICE')),
    CONSTRAINT `chk_knowledge_goal` CHECK (`goal_type` IN (
        'REPLACE', 'REDUCE_SUGAR', 'REDUCE_FAT',
        'ADJUST_STIMULATION', 'REGION_ADAPT', 'GENERAL'
    )),
    CONSTRAINT `chk_knowledge_amount` CHECK (
        `amount_min` IS NULL OR `amount_max` IS NULL OR `amount_max` >= `amount_min`
    ),
    CONSTRAINT `chk_knowledge_status` CHECK (`status` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='配料调配知识卡表';

CREATE TABLE IF NOT EXISTS `ingredient_pairing` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '搭配关系编号',
    `pairing_code`      VARCHAR(60) NOT NULL COMMENT '搭配关系编码',
    `primary_ingredient` VARCHAR(100) NOT NULL COMMENT '主配料名称',
    `paired_ingredient` VARCHAR(100) NOT NULL COMMENT '搭配配料名称',
    `relation_type`     VARCHAR(20) NOT NULL COMMENT '关系类型：SYNERGY协同、MASKING掩蔽、ANTAGONISM拮抗、BALANCE平衡',
    `dimension`         VARCHAR(30) NULL COMMENT '主要作用的风味维度',
    `ratio_note`        VARCHAR(200) NULL COMMENT '推荐比例或替代率说明',
    `effect`            VARCHAR(500) NOT NULL COMMENT '作用效果说明',
    `risk_note`         VARCHAR(500) NULL COMMENT '风险提示',
    `evidence_source`   VARCHAR(255) NULL COMMENT '依据来源或演示数据说明',
    `status`            TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ingredient_pairing_code` (`pairing_code`),
    KEY `idx_pairing_primary` (`primary_ingredient`, `status`),
    KEY `idx_pairing_paired` (`paired_ingredient`, `status`),
    KEY `idx_pairing_relation_dimension` (`relation_type`, `dimension`),
    CONSTRAINT `chk_pairing_relation` CHECK (`relation_type` IN ('SYNERGY', 'MASKING', 'ANTAGONISM', 'BALANCE')),
    CONSTRAINT `chk_pairing_status` CHECK (`status` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='配料搭配关系表';

CREATE TABLE IF NOT EXISTS `ai_call_log` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志编号',
    `user_id`           BIGINT UNSIGNED NULL COMMENT '触发用户编号',
    `task_id`           BIGINT UNSIGNED NULL COMMENT '关联分析任务编号',
    `scene`             VARCHAR(30) NOT NULL COMMENT '调用场景：RESULT_EXPLAIN、KNOWLEDGE_QA、DRAFT_PARSE',
    `model_name`        VARCHAR(60) NULL COMMENT '模型名称',
    `prompt_digest`     VARCHAR(500) NULL COMMENT '提示词摘要，不记录完整用户隐私内容',
    `reference_codes`   VARCHAR(500) NULL COMMENT '本次引用的知识卡编码，逗号分隔',
    `prompt_tokens`     INT NULL COMMENT '输入 token 数',
    `completion_tokens` INT NULL COMMENT '输出 token 数',
    `elapsed_ms`        INT NULL COMMENT '调用耗时毫秒',
    `success`           TINYINT NOT NULL DEFAULT 1 COMMENT '是否成功：1成功，0失败',
    `fallback_used`     TINYINT NOT NULL DEFAULT 0 COMMENT '是否走了本地降级：1是，0否',
    `error_message`     VARCHAR(500) NULL COMMENT '失败原因',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_ai_log_user_time` (`user_id`, `created_at`),
    KEY `idx_ai_log_scene_time` (`scene`, `created_at`),
    KEY `idx_ai_log_task` (`task_id`),
    CONSTRAINT `fk_ai_log_user`
        FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT `fk_ai_log_task`
        FOREIGN KEY (`task_id`) REFERENCES `analysis_task` (`id`)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT `chk_ai_log_scene` CHECK (`scene` IN ('RESULT_EXPLAIN', 'KNOWLEDGE_QA', 'DRAFT_PARSE')),
    CONSTRAINT `chk_ai_log_success` CHECK (`success` IN (0, 1)),
    CONSTRAINT `chk_ai_log_fallback` CHECK (`fallback_used` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 调用日志表';

-- ============================================================
-- 8. 调配知识库演示数据
-- 说明：以下食材属性与调配知识均为课设演示用相对数据与经验规则，
--       用于验证「知识检索 → 规则匹配 → 可解释建议」的技术闭环，
--       不等同于实验室检测结果，也不能替代感官评价与法规审核。
-- ============================================================

-- 8.1 补充食材：知识卡中出现的候选配料需要有对应的属性数据才能参与计算
INSERT IGNORE INTO `ingredient`
    (`name`, `category`, `default_unit`, `sweet`, `salty`, `sour`, `bitter`, `umami`, `spicy`, `numbing`, `fat_aroma`, `description`, `data_source`, `status`)
VALUES
    ('三氯蔗糖', '代糖', 'g', 100, 0, 0, 5, 0, 0, 0, 0, '高倍甜味剂，甜味曲线接近蔗糖，过量时后苦明显', '课设演示相对评分', 1),
    ('麦芽糊精', '填充剂', 'g', 8, 0, 0, 0, 0, 0, 0, 0, '提供固形物与口感厚度，自身甜度极低', '课设演示相对评分', 1),
    ('氯化钾', '代盐', 'g', 0, 70, 0, 15, 0, 0, 0, 0, '可部分替代食盐，高替代率时出现金属苦味', '课设演示相对评分', 1),
    ('海带粉', '天然增鲜原料', 'g', 3, 45, 0, 8, 60, 0, 0, 3, '天然矿物质咸感与海洋鲜味', '课设演示相对评分', 1),
    ('水解植物蛋白', '增鲜原料', 'g', 3, 12, 2, 6, 75, 0, 0, 5, '复合鲜味与醇厚感，部分产品含大豆来源', '课设演示相对评分', 1),
    ('骨汤浓缩物', '天然增鲜原料', 'g', 2, 30, 1, 3, 70, 0, 0, 25, '提供肉汤底味与胶原蛋白厚实感', '课设演示相对评分', 1),
    ('番茄粉', '果蔬原料', 'g', 30, 4, 35, 3, 55, 0, 0, 3, '天然鲜、甜、酸复合风味', '课设演示相对评分', 1),
    ('柠檬酸', '酸味料', 'g', 0, 0, 98, 3, 0, 0, 0, 0, '纯正酸味，添加量易于精确控制', '课设演示相对评分', 1),
    ('乳酸', '酸味料', 'ml', 0, 0, 80, 2, 0, 0, 0, 0, '柔和圆润的酸味，无明显挥发性刺激', '课设演示相对评分', 1),
    ('花椒油', '香辛料油脂', 'ml', 0, 0, 0, 6, 2, 10, 92, 60, '麻感集中清晰，同时具备油脂载香能力', '课设演示相对评分', 1),
    ('青花椒', '香辛料', 'g', 1, 0, 1, 10, 1, 12, 95, 5, '麻感清锐，香气偏清新柑橘调', '课设演示相对评分', 1),
    ('辣椒油树脂', '辣味浓缩原料', 'g', 0, 0, 0, 10, 0, 100, 0, 20, '高浓缩辣源，极低用量即可显著提升辣度', '课设演示相对评分', 1),
    ('乳清粉', '乳制品原料', 'g', 45, 2, 1, 2, 10, 0, 0, 20, '提供乳脂香气与柔和的口感厚度', '课设演示相对评分', 1),
    ('猪油香精', '食用香精', 'ml', 0, 0, 0, 0, 0, 0, 0, 95, '提供特征脂香，几乎不增加体系油脂', '课设演示相对评分', 1),
    ('玉米淀粉', '填充剂', 'g', 2, 0, 0, 0, 0, 0, 0, 1, '吸水填充与质构支撑，减油时维持体系', '课设演示相对评分', 1),
    ('大豆分离蛋白', '蛋白原料', 'g', 0, 3, 0, 4, 45, 0, 0, 12, '提供蛋白质构与一定鲜味', '课设演示相对评分', 1),
    ('香菇抽提物', '天然增鲜原料', 'g', 3, 6, 0, 6, 85, 0, 0, 6, '高浓缩菌菇鲜味', '课设演示相对评分', 1),
    ('干贝素', '增鲜剂', 'g', 2, 8, 1, 3, 92, 0, 0, 4, '海鲜特征鲜味', '课设演示相对评分', 1),
    ('鸡骨白汤', '天然增鲜原料', 'g', 2, 25, 0, 3, 65, 0, 0, 35, '鸡骨熬制底汤风味与脂香', '课设演示相对评分', 1),
    ('苹果醋', '酸味料', 'ml', 12, 0, 75, 4, 2, 0, 0, 0, '柔和果酸并带有果香', '课设演示相对评分', 1),
    ('白胡椒', '香辛料', 'g', 1, 1, 0, 10, 3, 20, 0, 4, '提供辛香刺激与一定去腥作用', '课设演示相对评分', 1),
    ('生姜粉', '香辛料', 'g', 5, 1, 1, 8, 5, 18, 0, 3, '辛香与去腥作用', '课设演示相对评分', 1),
    ('大蒜粉', '香辛料', 'g', 6, 2, 1, 5, 12, 10, 0, 3, '提供蒜香与一定鲜味', '课设演示相对评分', 1),
    ('麦芽糖醇', '代糖', 'g', 75, 0, 0, 2, 0, 0, 0, 0, '甜度接近蔗糖，凉感低于赤藓糖醇', '课设演示相对评分', 1),
    ('罗汉果甜苷', '代糖', 'g', 90, 0, 0, 10, 0, 0, 0, 0, '天然高倍甜味剂，后味相对柔和', '课设演示相对评分', 1),
    ('谷朊粉', '蛋白原料', 'g', 1, 4, 0, 5, 25, 0, 0, 3, '提供蛋白质构支撑', '课设演示相对评分', 1);

-- 8.2 配料调配知识卡
--
-- 【编写规则 —— 新增知识卡前必读】
-- 本表的定位只有一个：**某个风味维度在某个研发场景下缺了，可以补什么料、补多少、为什么、有什么风险。**
-- 因此以下三条是硬约束，违反会让结果页出现「候选食材：判读口径提示」这类无法理解的输出：
--
--   1. `candidate_ingredient` 必须是「真实存在的配料」或「明确的复配体系」
--      （如「酵母抽提物」「赤藓糖醇+甜菊糖苷」）。
--      **不得填写**「判读口径提示」「补偿顺序」「工艺与感官重定位」这类抽象概念。
--   2. `dimension` 必须是与该候选配料**真正相关**的维度，
--      不能为了「让卡片有个归属」而随便挂一个维度。
--   3. **适用条件有三层：维度、研发目标、约束场景。**
--      如果一条知识只在「用户设了某类约束」时才成立（如过敏原替代方向），
--      必须把 `trigger_constraint` 设成对应的值（如 `ALLERGEN_ONLY`），
--      **不能图省事标成 `goal_type = 'GENERAL'`** —— 那样它会在所有涉及该维度的
--      分析里出现，用户会收到与自身场景无关的建议（已实际发生）。
--   4. 以下三类内容**不属于本表**，不要写进来：
--      · 判读口径与方法论 → 写入 `AnalysisEngine.buildExplanation()` 的解释文本；
--      · 工艺动作（加热、时间、反应） → 属第二阶段「工艺分析」的 `flavor_reaction` 表；
--      · 纯策略提示且无候选配料 → 属规则层，写入 `flavor_rule`。
--
-- 因上述原因停用的四条（status = 0，内容保留备查）：
--   UK-FAT-004 美拉德反应工艺增香补偿脂香（工艺类，第二阶段启用）
--   UK-FAT-008 控脂目标下的脂香下降属于预期代价（纯提示，无候选配料）
--   UK-GEN-001 主料替换后的整体风味重建顺序（方法论）
--   UK-GEN-006 单一维度基准值极低时的判读修正（方法论，且与引擎解释文本重复）
INSERT IGNORE INTO `flavor_knowledge`
    (`knowledge_code`, `title`, `dimension`, `goal_type`, `scene`, `direction`, `candidate_ingredient`, `candidate_category`,
     `amount_min`, `amount_max`, `amount_unit`, `ratio_note`, `mechanism`, `risk_note`, `allergen_tags`, `evidence_source`, `keywords`, `priority`, `status`)
VALUES
    ('UK-UMAMI-001', '酵母抽提物补充替换后的鲜味损失', 'umami', 'REPLACE', '主料由高鲜肉类替换为低脂肉类后鲜味明显下降', 'ADD', '酵母抽提物', '增鲜剂', 0.05, 0.30, '%', NULL,
     '富含呈味核苷酸与游离谷氨酸，可与食材中的鲜味物质产生协同效应，同时提供醇厚感与肉香底味', '用量超过 0.5% 易呈明显酵母味与咸感，需同步核算总钠', '酵母', '食品科学教材·呈味核苷酸协同效应；课设演示数据', '鲜味,增鲜,替换,肉香,醇厚', 10, 1),
    ('UK-UMAMI-002', '香菇粉提供天然菌菇鲜味', 'umami', 'GENERAL', '需要天然来源增鲜且避免使用化学增味剂', 'ADD', '香菇粉', '天然增鲜原料', 0.10, 0.50, '%', NULL,
     '含鸟苷酸等天然呈味核苷酸，与谷氨酸类物质协同增强鲜味，并带来菌菇香气', '用量过高会带来明显菌菇气味，与清淡型产品风格不符', '菌菇', '食品原料学·食用菌风味成分；课设演示数据', '鲜味,天然,菌菇,增鲜', 30, 1),
    ('UK-UMAMI-003', '味精直接补充鲜味', 'umami', 'GENERAL', '鲜味缺口明确且成本敏感', 'ADD', '味精', '增鲜剂', 0.05, 0.20, '%', NULL,
     '谷氨酸钠直接提供纯粹鲜味，起效快、成本低，是鲜味补偿的基准手段', '单一使用缺少醇厚感，用量过高口感发寡；标签需标注含味精', '无', 'GB 2760 食品添加剂使用标准；课设演示数据', '鲜味,味精,成本,直接', 40, 1),
    ('UK-UMAMI-004', '酱油补充咸鲜并注意同步减盐', 'umami', 'REPLACE', '主料替换后咸味与鲜味同时不足', 'ADD', '酱油', '复合调味料', 1.00, 3.00, '%', NULL,
     '同时提供咸味、鲜味与发酵香气，可一次补齐多个风味缺口', '酱油自带高钠，补充鲜味时须同步下调食盐用量，否则总钠超标', '大豆,小麦', '酿造酱油国家标准；课设演示数据', '鲜味,咸味,酱油,复合,减盐', 35, 1),
    ('UK-UMAMI-005', '鸡油以低用量提供脂香型增鲜', 'umami', 'REDUCE_FAT', '控脂后鲜味与脂香同时下降，但不宜直接恢复油脂用量', 'ADD', '鸡油', '油脂', 0.20, 0.80, '%', NULL,
     '动物脂在低用量下即可作为脂溶性香气载体，把鲜味物质带入口腔后段，放大鲜味感知', '仍属油脂，须计入控脂总量；过量会明显抬高脂香维度', '无', '食品风味化学·脂溶性香气载体；课设演示数据', '鲜味,脂香,控脂,增香', 20, 1),
    ('UK-UMAMI-006', '水解植物蛋白提升复合鲜味与醇厚感', 'umami', 'GENERAL', '需要提升整体醇厚感而非单纯鲜度', 'ADD', '水解植物蛋白', '增鲜原料', 0.10, 0.50, '%', NULL,
     '含多种呈味氨基酸与肽类，在提供鲜味的同时增加口感厚实度', '部分产品含麸质或大豆来源，须核对过敏原；用量过高会有酱味', '大豆,小麦', '食品配料手册·水解植物蛋白；课设演示数据', '鲜味,醇厚,复合', 45, 1),
    ('UK-UMAMI-007', '骨汤浓缩物重建肉汤底味', 'umami', 'REPLACE', '替换主料后失去原有的肉汤底味', 'ADD', '骨汤浓缩物', '天然增鲜原料', 1.00, 3.00, '%', NULL,
     '提供肉类特征香气与胶原蛋白带来的厚实口感，弥补替换后缺失的肉底', '含钠较高，需计入总钠核算；风味强度随批次波动', '无', '食品配料手册·肉类抽提物；课设演示数据', '鲜味,肉香,底味,替换', 40, 1),
    ('UK-UMAMI-008', '番茄粉提供天然鲜甜与酸感', 'umami', 'GENERAL', '需要天然来源的鲜味且产品风格偏清甜', 'ADD', '番茄粉', '果蔬原料', 0.30, 1.00, '%', NULL,
     '天然谷氨酸含量较高，同时带轻微酸感与甜感，可多维度微调而不显突兀', '会带来番茄特征色泽与风味，不适用于清淡色谱产品', '无', '食品原料学·果蔬呈味成分；课设演示数据', '鲜味,天然,番茄,酸甜', 60, 1),
    ('UK-UMAMI-009', '鸡骨白汤补充肉底鲜味', 'umami', 'REPLACE', '替换主料后需要重建禽类底汤风味', 'ADD', '鸡骨白汤', '天然增鲜原料', 0.50, 2.00, '%', NULL,
     '提供禽类特征鲜味与一定脂香，与低脂禽类主料风味匹配度高', '含脂香成分，控脂场景需计入总量', '无', '食品配料手册·禽类抽提物；课设演示数据', '鲜味,鸡,底汤,替换', 40, 1),
    ('UK-UMAMI-010', '干贝素提供海鲜特征鲜味', 'umami', 'GENERAL', '需要海鲜底味或与海鲜主料匹配的鲜味', 'ADD', '干贝素', '增鲜剂', 0.03, 0.15, '%', NULL,
     '以琥珀酸类物质为主，提供与谷氨酸不同的海鲜型鲜味，可与味精形成层次', '海鲜风味明显，不适用于纯肉味产品', '贝类', 'GB 2760 食品添加剂使用标准；课设演示数据', '鲜味,海鲜,干贝,层次', 50, 1),
    ('UK-UMAMI-011', '香菇抽提物高浓缩补鲜', 'umami', 'GENERAL', '需要高浓缩天然鲜味且控制配方体积', 'ADD', '香菇抽提物', '天然增鲜原料', 0.05, 0.30, '%', NULL,
     '相比香菇粉有效成分浓度更高，可在很小用量下补足鲜味，几乎不增加体系体积', '风味强度高，超量会明显偏菌菇调；价格高于普通增鲜剂', '菌菇', '食品原料学·食用菌抽提物；课设演示数据', '鲜味,浓缩,天然,菌菇', 45, 1),
    ('UK-SWEET-001', '赤藓糖醇替代蔗糖', 'sweet', 'REDUCE_SUGAR', '需要降低蔗糖用量并保持甜度水平', 'REPLACE', '赤藓糖醇', '代糖', 2.00, 6.00, '%', '替代蔗糖甜度的 60% 至 70%',
     '甜度约为蔗糖的 60% 至 70%，不参与人体代谢，但溶解时吸热产生清凉感', '高用量时凉感与砂砾感明显，与醇厚类产品风格冲突；过量可能引起肠胃不适', '无', 'GB 2760；糖醇类甜味剂应用资料；课设演示数据', '甜味,代糖,减糖,赤藓糖醇,凉感', 10, 1),
    ('UK-SWEET-002', '甜菊糖苷高倍甜味补充', 'sweet', 'REDUCE_SUGAR', '需要以极低用量获得高甜度', 'REPLACE', '甜菊糖苷', '代糖', 0.01, 0.05, '%', '甜度约为蔗糖的 200 至 300 倍',
     '高倍甜味剂，极低用量即可补足甜度，天然来源且不提供热量', '用量偏高时出现明显后苦与金属味，必须复配使用，不建议单独使用', '无', 'GB 2760；天然高倍甜味剂应用资料；课设演示数据', '甜味,代糖,减糖,甜菊糖苷,后苦', 15, 1),
    ('UK-SWEET-003', '代糖复配平衡后味', 'sweet', 'REDUCE_SUGAR', '单一代糖出现凉感、后苦或金属味', 'REPLACE', '赤藓糖醇+甜菊糖苷', '代糖复配体系', 0.50, 5.00, '%', '按甜度贡献：赤藓糖醇 60% 加甜菊糖苷 40%',
     '不同代糖的甜味起效与消退曲线互补：糖醇提供饱满的甜体感，高倍甜味剂补足峰值甜度，从而压低单一代糖的不良后味', '复配比例需按产品甜度目标逐步微调；务必做后味与口感复核', '无', '复配甜味剂感官评价资料；课设演示数据', '甜味,复配,代糖,后味,减糖', 5, 1),
    ('UK-SWEET-004', '三氯蔗糖高倍甜味方案', 'sweet', 'REDUCE_SUGAR', '需要接近蔗糖的甜味曲线且耐受热处理', 'REPLACE', '三氯蔗糖', '代糖', 0.005, 0.020, '%', '甜度约为蔗糖的 400 至 600 倍',
     '甜味曲线最接近蔗糖，热稳定性好，适合需经热处理的产品', '浓度偏高时后苦明显；属人工合成甜味剂，与清洁标签定位冲突', '无', 'GB 2760；高倍甜味剂应用资料；课设演示数据', '甜味,代糖,三氯蔗糖,热稳定', 30, 1),
    ('UK-SWEET-005', '麦芽糖醇提供接近蔗糖的甜感', 'sweet', 'REDUCE_SUGAR', '减糖后需要接近蔗糖的饱满甜感', 'REPLACE', '麦芽糖醇', '代糖', 2.00, 8.00, '%', '按等甜度折算，替代蔗糖的 80% 至 90%',
     '甜度接近蔗糖且凉感明显低于赤藓糖醇，能提供更接近蔗糖的饱满甜体感', '过量可能引起肠胃不适；吸湿性较强需注意产品储存稳定性', '无', 'GB 2760；糖醇类甜味剂应用资料；课设演示数据', '甜味,代糖,麦芽糖醇,饱满,减糖', 20, 1),
    ('UK-SWEET-006', '罗汉果甜苷天然高倍甜味方案', 'sweet', 'REDUCE_SUGAR', '需要天然来源高倍甜味剂且希望后味柔和', 'REPLACE', '罗汉果甜苷', '代糖', 0.01, 0.04, '%', '甜度约为蔗糖的 150 至 300 倍',
     '天然来源高倍甜味剂，后苦与金属味弱于甜菊糖苷，适合对后味敏感的产品', '成本高于其他高倍甜味剂；高用量仍可能出现轻微后苦', '无', 'GB 2760；天然高倍甜味剂应用资料；课设演示数据', '甜味,天然,罗汉果,减糖,后味', 25, 1),
    ('UK-SWEET-007', '洋葱粉提供天然甜香缓冲', 'sweet', 'REDUCE_SUGAR', '减糖后甜味下降但不希望使用代糖', 'ADD', '洋葱粉', '香辛料', 0.20, 0.80, '%', NULL,
     '含天然糖类与含硫香气前体，经加热产生甜香，可在不使用甜味剂的前提下补足甜感层次', '会引入蔬菜特征香气与轻微色泽变化；用量过高有辛辣尾巴', '无', '食品原料学·香辛料风味；课设演示数据', '甜味,天然,洋葱,减糖,甜香', 50, 1),
    ('UK-SWEET-008', '微量食盐提升甜味感知', 'sweet', 'REDUCE_SUGAR', '减糖后甜感单薄，需要放大既有甜味', 'ADD', '食盐', '基础调味料', 0.05, 0.15, '%', NULL,
     '低浓度钠离子可抑制苦味受体并提升甜味感知，在减糖场景下以极低成本放大剩余甜味', '总钠会上升，需与减钠目标统筹；过量反而压制甜味', '无', '食品感官科学·味觉交互作用；课设演示数据', '甜味,食盐,减糖,掩蔽,协同', 25, 1),
    ('UK-SWEET-009', '麦芽糊精补充减糖后的口感厚度', 'sweet', 'REDUCE_SUGAR', '减糖后口感变薄、水感明显', 'ADD', '麦芽糊精', '填充剂', 0.50, 2.00, '%', NULL,
     '提供固形物与黏度，弥补蔗糖减少后损失的体感厚度，本身甜度极低不干扰减糖目标', '过量会带来糊感与粉味；需计入碳水化合物标示', '无', '食品配料手册·麦芽糊精；课设演示数据', '甜味,口感,填充,减糖,厚度', 55, 1),
    ('UK-SALTY-001', '酵母抽提物部分替代食盐实现减钠', 'salty', 'REDUCE_FAT', '需要降低钠含量但保留咸味强度', 'REPLACE', '酵母抽提物', '增鲜剂', 0.10, 0.30, '%', '替代食盐用量的 10% 至 20%',
     '通过增强鲜味与醇厚感提升整体咸味感知，从而允许实际食盐用量下降', '本方案属感知补偿，真实钠下降幅度有限，须实测钠含量确认', '酵母', '食品工业·减钠技术资料；课设演示数据', '咸味,减钠,替代,酵母抽提物', 10, 1),
    ('UK-SALTY-002', '氯化钾部分替代食盐', 'salty', 'REDUCE_FAT', '需要实质性降低钠含量', 'REPLACE', '氯化钾', '代盐', 0.50, 1.50, '%', '替代食盐用量的 10% 至 25%',
     '钾盐提供与钠盐相似的咸味，可在保持咸度的同时实质降低钠摄入', '替代率超过 25% 会出现明显金属苦味；肾功能异常人群需注意钾摄入；使用前须确认法规允许', '无', 'GB 2760；低钠盐技术资料；课设演示数据', '咸味,减钠,氯化钾,代盐,苦味', 35, 1),
    ('UK-SALTY-003', '海带粉提供天然咸鲜', 'salty', 'GENERAL', '需要天然来源的减钠方案', 'ADD', '海带粉', '天然增鲜原料', 0.10, 0.40, '%', NULL,
     '天然含碘、钾等矿物质与呈味氨基酸，提供柔和咸感与海洋鲜味，可降低对食盐的依赖', '海带特征风味明显且色泽较深；碘含量需在标签中提示', '藻类', '食品原料学·海藻呈味成分；课设演示数据', '咸味,天然,海带,减钠,鲜味', 45, 1),
    ('UK-SALTY-004', '酸味增强咸味感知', 'salty', 'REDUCE_FAT', '减盐后咸味不足但不宜继续补盐', 'ADD', '柠檬汁', '果蔬原料', 0.10, 0.40, '%', NULL,
     '适度酸味可提升咸味感知灵敏度，使较低的食盐用量被感知为足够咸', '酸度提升会同步影响酸味维度与产品色泽；用量过高与甜味冲突', '无', '食品感官科学·味觉交互作用；课设演示数据', '咸味,酸味,减盐,协同', 50, 1),
    ('UK-SALTY-005', '酱油减量后同步补鲜', 'salty', 'REDUCE_FAT', '配方中酱油用量下调导致咸鲜不足', 'ADD', '味精', '增鲜剂', 0.05, 0.15, '%', NULL,
     '用鲜味补足酱油减少后缺失的风味强度，维持整体浓郁度', '无法补偿酱油特有的发酵香气；需同步核算替代后的总钠变化', '无', '食品调味技术资料；课设演示数据', '咸味,酱油,减量,补鲜', 55, 1),
    ('UK-SOUR-001', '柠檬汁提供清晰果酸', 'sour', 'GENERAL', '需要提升酸度且偏好天然果香', 'ADD', '柠檬汁', '果蔬原料', 0.50, 2.00, '%', NULL,
     '以柠檬酸为主的有机酸，酸味清晰直接，同时提供果香与清爽感', '加热后香气损失明显，适合后段添加；会带入轻微色泽', '无', '食品原料学·果蔬有机酸；课设演示数据', '酸味,柠檬,天然,果香', 20, 1),
    ('UK-SOUR-002', '白醋提供直接酸味', 'sour', 'GENERAL', '需要低成本且无附加风味的酸度提升', 'ADD', '白醋', '酸味料', 0.30, 1.50, '%', NULL,
     '醋酸提供刺激直接、起效快的酸味，挥发性带来冲鼻的嗅觉刺激', '乙酸气味明显，用量过高会掩盖其他香气；添加时机影响挥发损失', '无', '食品原料学·食醋；课设演示数据', '酸味,白醋,醋酸,成本', 30, 1),
    ('UK-SOUR-003', '乳酸提供柔和圆润的酸味', 'sour', 'GENERAL', '需要柔和不发冲的酸味风格', 'ADD', '乳酸', '酸味料', 0.05, 0.20, '%', NULL,
     '乳酸酸味柔和圆润，无明显挥发性刺激，适合奶香、酱香类产品', '酸度强度低于醋酸，等量替代时需上调用量', '无', 'GB 2760；食品酸味剂应用资料；课设演示数据', '酸味,乳酸,柔和,圆润', 40, 1),
    ('UK-SOUR-004', '柠檬酸快速补酸', 'sour', 'GENERAL', '需要精确可量化的酸度微调', 'ADD', '柠檬酸', '酸味料', 0.05, 0.15, '%', NULL,
     '酸味纯正、溶解性好、添加量易于精确控制，是酸度微调的基准原料', '起效快但缺乏层次，单独使用时显得单薄', '无', 'GB 2760；食品酸味剂应用资料；课设演示数据', '酸味,柠檬酸,精确,微调', 35, 1),
    ('UK-SOUR-005', '苹果醋提供柔和果酸', 'sour', 'GENERAL', '需要果香型酸味且不希望过强刺激', 'ADD', '苹果醋', '酸味料', 0.50, 2.00, '%', NULL,
     '有机酸与果香成分并存，酸感比白醋柔和，适合果味或清爽型产品', '颜色较深，浅色产品需评估色泽影响', '无', '食品原料学·食醋；课设演示数据', '酸味,苹果醋,果香,柔和', 45, 1),
    ('UK-SOUR-006', '减酸场景下的酸甜再平衡', 'sour', 'REDUCE_SUGAR', '减糖后酸味突兀、酸感被放大', 'ADD', '赤藓糖醇', '代糖', 1.00, 4.00, '%', '按剩余酸度补足甜度至原甜酸比',
     '蔗糖减少后失去对酸味的平衡作用，用代糖重建原配方的甜酸比例可有效缓解酸味突兀', '代糖后味可能进一步放大酸感，务必做整体口感复核', '无', '食品感官科学·甜酸平衡；课设演示数据', '酸味,减糖,平衡,甜酸比', 15, 1),
    ('UK-BITTER-001', '代糖后苦的复配缓解', 'bitter', 'REDUCE_SUGAR', '使用高倍甜味剂后出现明显后苦', 'REDUCE', '甜菊糖苷', '代糖', 0.01, 0.03, '%', '下调甜菊糖苷占比，由糖醇补足甜度',
     '高倍甜味剂的后苦与用量正相关，通过下调其占比并由糖醇补足甜度可降低苦味总量', '甜度总目标不变，仅调整来源结构；需确认糖醇用量未超口感阈值', '无', '复配甜味剂感官评价资料；课设演示数据', '苦味,代糖,后苦,复配,减糖', 10, 1),
    ('UK-BITTER-002', '香辛料过量致苦的回调', 'bitter', 'ADJUST_STIMULATION', '提高麻辣度后苦味维度同步异常上升', 'REDUCE', '花椒粉', '香辛料', NULL, NULL, '%', '按苦味维度偏移量下调香辛料总用量 5% 至 15%',
     '花椒、辣椒等在提升刺激度时其自身苦味成分同步增加，形成刺激度上升伴随苦味的耦合现象', '回调后需重新计算麻辣维度，避免刺激度达不到目标', '无', '香辛料风味成分分析；课设演示数据', '苦味,香辛料,花椒,回调,麻辣', 25, 1),
    ('UK-BITTER-003', '食盐掩蔽苦味', 'bitter', 'GENERAL', '苦味轻微超出目标范围需要低成本缓解', 'ADD', '食盐', '基础调味料', 0.05, 0.20, '%', NULL,
     '钠离子可抑制苦味受体的响应强度，在低浓度区间对苦味有明确掩蔽作用', '与减钠目标冲突，仅在苦味为次要矛盾时使用；过量会破坏整体平衡', '无', '食品感官科学·味觉掩蔽；课设演示数据', '苦味,掩蔽,食盐,抑制', 40, 1),
    ('UK-BITTER-004', '甜味复配掩蔽苦感', 'bitter', 'GENERAL', '苦味明显且产品允许使用甜味', 'ADD', '白砂糖', '甜味料', 0.20, 1.00, '%', NULL,
     '甜味与苦味存在相互抑制关系，适量甜味可显著降低苦味感知强度', '与减糖目标冲突；甜度提升会改变整体风味平衡', '无', '食品感官科学·味觉交互作用；课设演示数据', '苦味,掩蔽,甜味,平衡', 50, 1),
    ('UK-BITTER-005', '提升醇厚感弱化苦味凸显', 'bitter', 'GENERAL', '苦味本身未变但整体单薄使其凸显', 'ADD', '酵母抽提物', '增鲜剂', 0.10, 0.30, '%', NULL,
     '增加鲜味与醇厚感可提高风味的整体饱满度，使苦味在复合风味中被弱化', '属感知层面的改善，不改变苦味物质的绝对含量', '酵母', '食品风味化学·风味平衡；课设演示数据', '苦味,醇厚,弱化,鲜味', 60, 1),
    ('UK-SPICY-001', '辣度超标的主辣原料回调', 'spicy', 'ADJUST_STIMULATION', '目标降辣但当前辣度高于目标区间', 'REDUCE', '辣椒粉', '香辛料', NULL, NULL, '%', '按辣度偏移量下调辣味原料总用量 5% 至 20%',
     '直接减少辣味原料比例是最可控、最可复现的降辣手段，且不引入新的风味变量', '减辣同时削弱辣椒特征香气，如香气重要需改用低辣高香型辣椒品种', '无', '香辛料应用资料；课设演示数据', '辣度,降辣,辣椒粉,回调', 10, 1),
    ('UK-SPICY-002', '油脂用量缓冲辣感刺激', 'spicy', 'ADJUST_STIMULATION', '需要降低辣感刺激强度但保留辣椒香气', 'ADD', '菜籽油', '油脂', 0.50, 2.00, '%', NULL,
     '辣椒素为脂溶性物质，适量油脂可将其分散并延缓释放，降低瞬时刺激峰值', '与控脂目标冲突；油脂增加会同步抬高脂香维度', '无', '食品风味化学·辣椒素溶解特性；课设演示数据', '辣度,缓冲,油脂,辣椒素', 45, 1),
    ('UK-SPICY-003', '甜味缓冲辣感', 'spicy', 'ADJUST_STIMULATION', '辣度过高且产品允许轻微甜味', 'ADD', '白砂糖', '甜味料', 0.30, 1.50, '%', NULL,
     '甜味对辣感有明确抑制作用，可在不减少辣椒用量的前提下降低辣感刺激', '会改变产品风味定位；与减糖目标冲突', '无', '食品感官科学·味觉交互作用；课设演示数据', '辣度,缓冲,甜味,降辣', 55, 1),
    ('UK-SPICY-004', '高浓缩辣源定向提辣', 'spicy', 'ADJUST_STIMULATION', '需要大幅提升辣度但避免带入过多固体体积', 'ADD', '辣椒油树脂', '辣味浓缩原料', 0.01, 0.05, '%', NULL,
     '辣椒素高浓缩载体，极低用量即可显著提升辣度，不额外引入粉状体积与苦味', '用量精度要求高，超量极易失控；须做均质化处理防止局部过辣', '无', 'GB 2760；辣椒制品应用资料；课设演示数据', '辣度,增辣,浓缩,辣椒油树脂', 30, 1),
    ('UK-SPICY-005', '香气型与辣度型辣椒复配', 'spicy', 'ADJUST_STIMULATION', '单纯降辣会同时损失辣椒香气', 'REPLACE', '辣椒粉+辣椒油树脂', '香辛料复配体系', 0.01, 1.00, '%', '降低高辣品种用量，补充香气型品种并微量添加浓缩辣源',
     '把辣度与香气两个诉求拆开分别由不同原料承担，可在降辣的同时保住辣椒特征香气', '复配体系较复杂，需逐步调整并记录每版感官结果', '无', '香辛料复配应用资料；课设演示数据', '辣度,香气,复配,降辣,辣椒', 20, 1),
    ('UK-SPICY-006', '酸味介入平衡辣感', 'spicy', 'ADJUST_STIMULATION', '辣度偏高且产品风格偏清爽', 'ADD', '柠檬汁', '果蔬原料', 0.30, 1.00, '%', NULL,
     '酸味可转移对辣感的注意力并提升清爽度，常见于酸辣型产品的风味结构', '会同步提升酸味维度，需在酸辣之间重新寻找平衡点', '无', '食品调味技术资料；课设演示数据', '辣度,酸味,平衡,清爽', 60, 1),
    ('UK-SPICY-007', '白胡椒补充辛香刺激度', 'spicy', 'ADJUST_STIMULATION', '需要提升辛香刺激但不宜使用辣椒类原料', 'ADD', '白胡椒', '香辛料', 0.05, 0.30, '%', NULL,
     '胡椒碱提供非辣椒型的辛香刺激，适合清汤、白汁等不宜着色的产品', '刺激感收敛较快，与辣椒的持续辣感风格不同；过量有明显苦味', '无', '香辛料应用资料；课设演示数据', '辣度,辛香,白胡椒,清汤', 55, 1),
    ('UK-NUMBING-001', '花椒用量回调降低麻度', 'numbing', 'ADJUST_STIMULATION', '麻度超出目标区域偏好范围', 'REDUCE', '花椒粉', '香辛料', NULL, NULL, '%', '按麻度偏移量下调花椒用量 5% 至 20%',
     '麻感强度与花椒用量近似线性相关，直接回调是最可控的降麻方式', '降麻同时削弱花椒香气；麻度基准值低时比例会被放大，建议结合绝对差值判读', '无', '香辛料应用资料；课设演示数据', '麻度,降麻,花椒,回调', 10, 1),
    ('UK-NUMBING-002', '花椒油定向补麻', 'numbing', 'REGION_ADAPT', '目标区域偏好要求提升麻度', 'ADD', '花椒油', '香辛料油脂', 0.10, 0.50, '%', NULL,
     '花椒油麻感集中且香气清晰，可在不显著增加固体香辛料用量的前提下提升麻度', '属油脂类，需计入脂香与控脂核算；香气挥发性强，宜后段添加', '无', '香辛料油脂应用资料；课设演示数据', '麻度,增麻,花椒油,区域适配', 15, 1),
    ('UK-NUMBING-003', '麻辣比协同调整', 'numbing', 'REGION_ADAPT', '麻辣型产品的两个维度需按区域偏好同步调整', 'ADD', '花椒粉+辣椒粉', '香辛料复配体系', 0.05, 1.00, '%', '麻辣比按目标区域画像的麻度权重与辣度权重之比设定',
     '麻与辣在感知上相互强化，单独调高其中一个会同时抬高另一个的感知强度，因此应作为一组联动调整', '联动调整后需整体复核刺激度，避免超出目标人群接受区间', '无', '区域口味画像与麻辣协同研究；课设演示数据', '麻度,辣度,协同,区域适配,麻辣', 10, 1),
    ('UK-NUMBING-004', '花椒品种选择影响麻感风格', 'numbing', 'ADJUST_STIMULATION', '需要调整麻感的质感而非单纯强度', 'REPLACE', '青花椒', '香辛料', NULL, NULL, '%', '按等麻度强度替换，用量按品种麻度系数折算',
     '红花椒麻感厚重、香气浓郁偏木香；青花椒麻感清锐、香气清新偏柑橘调，替换品种可改变麻感风格而非强度', '不同品种麻度系数差异明显，等量替换会导致麻度偏移', '无', '花椒品种风味比较资料；课设演示数据', '麻度,花椒品种,青花椒,风格', 40, 1),
    ('UK-NUMBING-005', '麻感过长时用酸甜缓解', 'numbing', 'ADJUST_STIMULATION', '麻感持续时间过长影响后味体验', 'ADD', '柠檬汁', '果蔬原料', 0.20, 0.80, '%', NULL,
     '适度酸味与甜味可缩短麻感的持续感知时间，改善后味收尾', '会改变酸味与甜味维度，需重新核对整体风味结构', '无', '食品感官科学·后味调控；课设演示数据', '麻度,后味,酸甜,缓解', 65, 1),
    ('UK-FAT-001', '控脂场景下以低用量油脂增香', 'fat_aroma', 'REDUCE_FAT', '减油后脂香明显下降但不宜恢复原油脂用量', 'ADD', '鸡油', '油脂', 0.20, 0.80, '%', NULL,
     '脂香感知对用量并非线性：少量高香气油脂即可显著改善脂香维度，避免为追回脂香而恢复整体油脂用量', '仍属油脂，须严格计入控脂总量；动物脂可能影响产品定位与标签', '无', '食品风味化学·脂香感知阈值；课设演示数据', '脂香,控脂,增香,低用量,鸡油', 10, 1),
    ('UK-FAT-002', '发酵增香物补偿脂香缺失', 'fat_aroma', 'REDUCE_FAT', '控脂后脂香下降且需补充醇厚感', 'ADD', '酵母抽提物', '增鲜剂', 0.10, 0.30, '%', NULL,
     '酵母抽提物中的美拉德反应产物可提供类似肉脂香的醇厚风味，在不增加油脂的前提下改善脂香感知', '不能完全替代真实脂香；用量过高呈酵母味', '酵母', '食品风味化学·美拉德反应产物；课设演示数据', '脂香,控脂,酵母抽提物,醇厚', 20, 1),
    ('UK-FAT-003', '乳粉类原料提供乳脂香与厚实感', 'fat_aroma', 'REDUCE_FAT', '需要非油脂路线的脂香补偿', 'ADD', '乳清粉', '乳制品原料', 0.30, 1.00, '%', NULL,
     '乳脂球膜成分与乳糖可提供柔和的乳脂香气与口感厚度，改善低脂配方单薄的体感', '引入乳制品过敏原；乳制品风味与部分咸鲜型产品不匹配', '乳', '乳制品配料应用资料；课设演示数据', '脂香,乳脂,厚实,过敏原,控脂', 45, 1),
    ('UK-FAT-004', '美拉德反应工艺增香补偿脂香', 'fat_aroma', 'REDUCE_FAT', '配方层面已无空间增加脂类原料', 'ADD', '美拉德反应增香工艺', '工艺补偿方案', NULL, NULL, '%', '通过调整加热温度与时间促进风味前体反应',
     '在配方不增加油脂的前提下，通过工艺条件强化美拉德反应与脂质氧化降解产物，弥补脂香缺失', '属工艺调整，需重新确认产品色泽、质构与微生物指标；不适用于冷加工产品', '无', '食品加工工艺学·美拉德反应；课设演示数据', '脂香,工艺,美拉德,控脂,增香', 35, 0),
    ('UK-FAT-005', '主料替换导致脂香大幅损失的综合补偿', 'fat_aroma', 'REPLACE', '高脂主料替换为低脂主料后脂香出现断崖式下降', 'ADD', '鸡油+酵母抽提物', '脂香与鲜味协同体系', 0.10, 0.80, '%', '鸡油 0.2% 至 0.5% 与酵母抽提物 0.1% 至 0.3% 组合使用',
     '低脂主料同时损失脂香与部分鲜味，单一原料难以补齐；以少量动物脂补香气、以发酵增香物补醇厚，可形成接近原配方的整体感知', '组合方案变量较多，建议按单因素逐个验证后再叠加', '酵母', '食品风味化学·复合补偿；课设演示数据', '脂香,替换,综合补偿,鸡油,酵母抽提物', 5, 1),
    ('UK-FAT-006', '脂香过高时的减油与吸水填充', 'fat_aroma', 'GENERAL', '脂香明显超出目标区间', 'REDUCE', '菜籽油', '油脂', NULL, NULL, '%', '按脂香偏移量下调油脂用量 10% 至 25%',
     '逐步下调油脂用量并辅以吸水填充或淀粉类辅料，可在降低脂香的同时维持体系含水量与质构', '单纯减油易导致体系乳化破坏与口感干散，需同步调整辅料', '无', '食品配方设计基础；课设演示数据', '脂香,减油,质构,填充', 55, 1),
    ('UK-FAT-007', '低脂主料的脂香风味补充', 'fat_aroma', 'REPLACE', '低脂主料本身缺乏脂溶性香气前体', 'ADD', '猪油香精', '食用香精', 0.02, 0.10, '%', NULL,
     '香精提供特征脂香分子，极低用量即可在嗅觉层面补足脂香感知，几乎不增加体系油脂', '属食用香精，与清洁标签定位可能冲突；须符合 GB 30616 使用要求', '无', 'GB 30616 食品用香精；课设演示数据', '脂香,香精,低脂,鼻后感知', 50, 1),
    ('UK-FAT-008', '控脂目标下的脂香下降属于预期代价', 'fat_aroma', 'REDUCE_FAT', '控脂为核心目标且配方层面禁止增加任何油脂', 'NOTICE', '工艺与感官重定位', '策略提示', NULL, NULL, '%', '通过强化鲜味与质构改善，接受脂香维度的合理下降',
     '控脂与脂香在真实产品中存在客观冲突，应明确告知研发人员该维度下降是目标本身带来的代价，而不是配方缺陷', '属于策略性提示，不产生可执行的配方变更', '无', '项目规则设计说明；课设演示数据', '脂香,控脂,策略,提示', 30, 0),
    ('UK-FAT-009', '淀粉类辅料维持减油后的体系质构', 'fat_aroma', 'REDUCE_FAT', '减油后体系含水量与质构失衡', 'ADD', '玉米淀粉', '填充剂', 0.50, 2.00, '%', NULL,
     '淀粉吸水糊化后提供体系支撑，在油脂减少时保持产品的水分与质构稳定', '用量过高会产生粉感与糊口；加热条件变化会影响糊化效果', '无', '食品配方设计基础；课设演示数据', '脂香,质构,淀粉,减油,填充', 40, 1),
    ('UK-GEN-001', '主料替换后的整体风味重建顺序', 'umami', 'REPLACE', '主料替换引起多维度同时偏移，需要确定补偿优先级', 'NOTICE', '补偿顺序', '方法论', NULL, NULL, '%', '先补脂香与鲜味骨架，再调咸度，最后微调酸甜与刺激度',
     '主料替换会同时影响脂香、鲜味、咸度等多个维度；按骨架到咸度再到平衡与刺激度的顺序逐层补偿，可避免反复打乱已验证的维度', '一次性调整多个变量会失去归因能力，建议每轮只调整一个维度并记录结果', '无', '配方设计方法论；课设演示数据', '替换,方法论,顺序,多维度', 5, 0),
    ('UK-GEN-002', '区域适配：西南地区增麻辣', 'spicy', 'REGION_ADAPT', '同一配方投放西南地区需要加强麻辣', 'ADD', '花椒粉+辣椒粉', '香辛料复配体系', 0.05, 1.50, '%', '按西南画像的辣度权重 1.200、麻度权重 1.250 折算',
     '依据区域口味画像的权重对麻辣维度做等比例上调，使配方贴近目标区域偏好', '区域画像为示例数据，实际投放前需结合目标城市消费者测试校准', '无', '项目区域画像设计说明；课设演示数据', '区域适配,西南,麻辣,画像', 20, 1),
    ('UK-GEN-003', '区域适配：华东地区增甜降刺激', 'sweet', 'REGION_ADAPT', '同一配方投放华东地区需要提高甜度并降低刺激', 'ADD', '白砂糖', '甜味料', 0.10, 1.00, '%', '按华东画像的甜度权重 1.150 折算，同步下调香辛料 10% 至 20%',
     '华东画像对甜度偏好更高而对辣麻偏好更低，应同步上调甜味并下调刺激度，避免单维度调整造成失衡', '甜度上调幅度需考虑当地减糖趋势，不宜机械按权重折算', '无', '项目区域画像设计说明；课设演示数据', '区域适配,华东,甜度,降辣', 25, 1),
    ('UK-GEN-004', '区域适配：华南地区增鲜降油腻', 'umami', 'REGION_ADAPT', '同一配方投放华南地区需要突出鲜味并降低油腻感', 'ADD', '香菇粉', '天然增鲜原料', 0.10, 0.50, '%', '按华南画像的鲜味权重 1.150、脂香权重 0.850 折算',
     '华南画像对鲜味偏好更高而脂香偏好更低，应上调天然增鲜原料并适度下调油脂', '需同步核对减油后的质构变化', '菌菇', '项目区域画像设计说明；课设演示数据', '区域适配,华南,鲜味,控油', 25, 1),
    ('UK-GEN-005', '过敏原替代：含大豆原料的替换方向', 'umami', 'GENERAL', '配方或建议涉及大豆过敏原需要替换', 'REPLACE', '酵母抽提物', '增鲜剂', 0.10, 0.30, '%', '按等鲜味强度替换酱油或水解植物蛋白',
     '在需要规避大豆过敏原时，以不含大豆的酵母抽提物替代酱油类增鲜原料，可保留大部分鲜味与醇厚感', '无法复现酱油的发酵香气与色泽；需确认所用酵母抽提物的培养基不含大豆来源', '酵母', '过敏原管理规范；课设演示数据', '过敏原,大豆,替换,酱油', 15, 1),
    ('UK-GEN-006', '单一维度基准值极低时的判读修正', 'umami', 'GENERAL', '某维度基准得分极低导致比例变化被放大', 'NOTICE', '判读口径提示', '方法论', NULL, NULL, '%', '基准值低于阈值时改以绝对差值判读',
     '当维度基准得分极低时比例分母过小，会导致百分比被放大到数百甚至上千，而实际感知变化有限，应改用绝对差值判断是否需要补偿', '属于结果判读层面的提示，不产生配方变更；容易被误读为分析错误', '无', '项目引擎设计说明；课设演示数据', '判读,阈值,极低基准,方法论', 10, 0),
    ('UK-GEN-007', '减钠与减糖同时推进时的钠糖平衡', 'salty', 'REDUCE_SUGAR', '配方同时要求减糖与减钠，两个目标相互干扰', 'ADD', '海带粉', '天然增鲜原料', 0.10, 0.40, '%', '以天然咸鲜原料同时承担部分咸味与鲜味，减少对食盐与糖的依赖',
     '减糖会削弱甜味对咸味的柔和作用，减钠又会削弱咸味对甜味的放大作用，两个目标同时推进会显著降低整体风味强度，需要引入天然咸鲜原料在中间层做补偿', '海带风味与部分产品风格不匹配；需同步核对碘含量提示', '藻类', '食品感官科学·味觉交互与减钠减糖协同；课设演示数据', '减糖,减钠,平衡,天然,协同', 10, 1);

-- 8.3 配料搭配关系
INSERT IGNORE INTO `ingredient_pairing`
    (`pairing_code`, `primary_ingredient`, `paired_ingredient`, `relation_type`, `dimension`, `ratio_note`, `effect`, `risk_note`, `evidence_source`, `status`)
VALUES
    ('PR-SWEET-001', '赤藓糖醇', '甜菊糖苷', 'SYNERGY', 'sweet', '按甜度贡献：赤藓糖醇 60% 加甜菊糖苷 40%',
     '不同甜味剂的起效与消退曲线互补，糖醇提供饱满甜体感、高倍甜味剂补足峰值甜度，显著减轻单一代糖的凉感与后苦', '复配比例需逐步微调，务必做后味与整体口感复核', '复配甜味剂感官评价资料；课设演示数据', 1),
    ('PR-SWEET-002', '甜菊糖苷', '食盐', 'MASKING', 'bitter', '食盐占总配方 0.05% 至 0.15%',
     '微量钠离子抑制苦味受体，可减轻高倍甜味剂带来的后苦', '与减钠目标冲突，需统筹总钠核算', '食品感官科学·味觉掩蔽；课设演示数据', 1),
    ('PR-SWEET-003', '赤藓糖醇', '白砂糖', 'BALANCE', 'sweet', '按减糖目标比例复配，逐步提高糖醇占比',
     '糖醇与蔗糖复配可在降低总糖的同时保留蔗糖的饱满甜感，减轻纯代糖的凉感与后味', '总糖降低幅度需实测确认；糖醇占比过高凉感仍会显现', '糖醇应用资料；课设演示数据', 1),
    ('PR-SWEET-004', '三氯蔗糖', '赤藓糖醇', 'MASKING', 'bitter', '糖醇用量按甜度目标折算，三氯蔗糖控制在 0.02% 以内',
     '糖醇带来的甜体感可掩蔽三氯蔗糖的苦尾，改善后味干净度', '三氯蔗糖超量时掩蔽效果有限，仍会出现明显后苦', '高倍甜味剂应用资料；课设演示数据', 1),
    ('PR-SWEET-005', '麦芽糖醇', '甜菊糖苷', 'SYNERGY', 'sweet', '按甜度贡献：麦芽糖醇 70% 加甜菊糖苷 30%',
     '麦芽糖醇的甜体感更接近蔗糖且凉感低，与高倍甜味剂复配可获得饱满且后味较轻的甜味', '麦芽糖醇吸湿性强，需注意产品储存稳定性', '糖醇应用资料；课设演示数据', 1),
    ('PR-UMAMI-001', '酵母抽提物', '味精', 'SYNERGY', 'umami', '酵母抽提物 0.1% 至 0.3% 与味精 0.05% 至 0.1% 组合',
     '酵母抽提物中的呈味核苷酸与味精的谷氨酸产生经典鲜味协同，等效鲜味强度高于两者单独使用之和', '需核算两者叠加后的总钠', '食品科学教材·鲜味协同效应；课设演示数据', 1),
    ('PR-UMAMI-002', '香菇粉', '食盐', 'SYNERGY', 'umami', '香菇粉 0.1% 至 0.5%，食盐按减钠目标同步下调',
     '香菇中的鸟苷酸与钠离子协同增强鲜味感知，可在较低食盐用量下维持鲜度', '需同步验证减钠后的咸味是否仍达标', '食品原料学·呈味核苷酸；课设演示数据', 1),
    ('PR-UMAMI-003', '番茄粉', '酵母抽提物', 'SYNERGY', 'umami', '按鲜味目标调整两者比例',
     '番茄的天然谷氨酸与酵母抽提物的呈味核苷酸叠加，形成自然柔和的鲜味层次', '番茄风味会带入色泽与特征香，需评估与产品风格匹配度', '食品原料学·果蔬呈味成分；课设演示数据', 1),
    ('PR-UMAMI-004', '干贝素', '味精', 'SYNERGY', 'umami', '干贝素 0.03% 至 0.15% 与味精 0.05% 至 0.1% 组合',
     '琥珀酸类海鲜鲜味与谷氨酸类鲜味叠加，可形成更有层次的海鲜或复合肉味鲜感', '海鲜风味明显，不适用于纯肉味产品', 'GB 2760；课设演示数据', 1),
    ('PR-FAT-001', '鸡油', '酵母抽提物', 'SYNERGY', 'fat_aroma', '鸡油 0.2% 至 0.5% 与酵母抽提物 0.1% 至 0.3%',
     '少量动物脂作为脂溶性香气载体把鲜味物质带入口腔后段，同时发酵增香物补充醇厚感，协同改善脂香与鲜味两个维度', '组合变量较多，建议按单因素逐个验证后再叠加', '食品风味化学·复合补偿；课设演示数据', 1),
    ('PR-FAT-002', '乳清粉', '麦芽糊精', 'SYNERGY', 'fat_aroma', '按质构目标调整两者比例',
     '乳脂香与麦芽糊精提供的固形物共同改善低脂配方的口感厚度，减少水感与单薄感', '引入乳制品过敏原；麦芽糊精过量会产生粉味', '乳制品配料应用资料；课设演示数据', 1),
    ('PR-FAT-003', '菜籽油', '玉米淀粉', 'BALANCE', 'fat_aroma', '减油时按体系含水量补充淀粉，用量 0.5% 至 2.0%',
     '减油会破坏乳化体系并导致质构干散，淀粉吸水糊化可提供体系支撑，维持减油后的质构稳定', '淀粉过量产生糊口感；需同步调整加热工艺条件', '食品配方设计基础；课设演示数据', 1),
    ('PR-SPICY-001', '花椒粉', '辣椒粉', 'SYNERGY', 'numbing', '麻辣比按目标区域画像的麻度权重与辣度权重之比设定',
     '麻与辣在感知上相互强化，联动调整可显著提高刺激度的调节效率', '联动调整后需整体复核刺激度，避免超出目标人群接受区间', '区域口味画像与麻辣协同研究；课设演示数据', 1),
    ('PR-SPICY-002', '菜籽油', '辣椒粉', 'SYNERGY', 'spicy', '油脂按风味与控脂目标设定，过量油脂会持续延长辣感',
     '辣椒素为脂溶性，油脂帮助其均匀分散并延缓释放，降低瞬时刺激峰值、延长辣感持续时间', '与控脂目标冲突；油脂增加会同步抬高脂香维度', '食品风味化学·辣椒素溶解特性；课设演示数据', 1),
    ('PR-SPICY-003', '柠檬汁', '辣椒粉', 'BALANCE', 'spicy', '柠檬汁 0.3% 至 1.0%，按酸辣平衡目标微调',
     '酸味转移对辣感的注意力并提升清爽度，是酸辣型产品的典型风味结构', '会同步提升酸味维度，需重新寻找酸辣平衡点', '食品调味技术资料；课设演示数据', 1),
    ('PR-SPICY-004', '白砂糖', '辣椒粉', 'BALANCE', 'spicy', '糖 0.3% 至 1.5%，按辣度与产品定位调整',
     '甜味对辣感有明确抑制作用，可在不减少辣椒用量的前提下降低辣感刺激', '与减糖目标冲突；会改变产品的甜辣风格定位', '食品感官科学·味觉交互作用；课设演示数据', 1),
    ('PR-NUMBING-001', '花椒油', '菜籽油', 'BALANCE', 'numbing', '花椒油 0.1% 至 0.5%，与基础油脂合并计入总脂',
     '花椒油以油脂为载香体系，可提升麻度而不显著增加固体香辛料体积', '需计入脂香与控脂总量', '香辛料油脂应用资料；课设演示数据', 1),
    ('PR-NUMBING-002', '花椒粉', '白砂糖', 'MASKING', 'numbing', '糖 0.2% 至 1.0%，用于缩短麻感后味',
     '适度甜味可缩短麻感的持续感知时间，改善后味收尾体验', '会改变甜味维度与产品风格定位', '食品感官科学·后味调控；课设演示数据', 1),
    ('PR-SALTY-001', '氯化钾', '酵母抽提物', 'MASKING', 'salty', '氯化钾替代食盐 10% 至 25%，配酵母抽提物 0.1% 至 0.3%',
     '酵母抽提物可显著掩盖氯化钾的金属苦味，是低钠盐体系的常用组合', '替代率超过 25% 时掩蔽效果下降；需确认法规允许使用', '低钠盐技术资料；课设演示数据', 1),
    ('PR-SALTY-002', '白砂糖', '食盐', 'BALANCE', 'salty', '糖 0.2% 至 1.0% 与盐 0.05% 至 0.2% 在同一体系内互相提升',
     '甜咸相互提升：微量食盐可放大甜味感知，少量糖可柔化咸味的尖锐感', '与减糖减钠目标均可能冲突，需统筹核算', '食品感官科学·味觉交互作用；课设演示数据', 1),
    ('PR-SOUR-001', '白醋', '乳酸', 'SYNERGY', 'sour', '按目标酸感风格调整比例，风味型产品提高乳酸占比',
     '挥发性醋酸提供冲鼻的嗅觉刺激，不挥发的乳酸提供柔和酸体感，复配可获得更立体的酸味', '复配比例需按产品风格确定，过高的醋酸占比会掩盖香气', '食品酸味剂应用资料；课设演示数据', 1),
    ('PR-SOUR-002', '柠檬汁', '赤藓糖醇', 'BALANCE', 'sour', '按剩余酸度补足甜度至原配方甜酸比',
     '减糖后酸味被放大，用代糖重建甜酸比可有效缓解酸味突兀，同时不引入蔗糖', '代糖后味可能进一步放大酸感，需做整体口感复核', '食品感官科学·甜酸平衡；课设演示数据', 1);

-- ============================================================
-- 9. 升级与修正脚本（针对已存在的数据库）
--
-- 说明：
--   · **本节全部语句均可重复执行且不报错**，可随第 1 ~ 8 节一起整体执行；
--   · 已有数据库升级时执行本节，执行后请在文件开头附近的
--     「结构变更记录」中登记一笔；
--   · **9.1 / 9.2 默认注释掉**（属第二阶段功能，当前不要执行）；
--   · 9.3 用存储过程先判断字段是否存在再添加：全新安装时该字段已由
--     第 7 节建表创建，已有数据库则在此补上，两种情况都不会报 1060；
--   · 必须按 9.3 → 9.4 的顺序执行（先加字段，再改数据）。
-- ============================================================

-- 升级 9.1（第二阶段补充工艺参数时启用，当前阶段请勿执行）
-- ALTER TABLE `recipe`
--     ADD COLUMN `heat_method`  VARCHAR(20)   NULL COMMENT '加热方式：炒/煮/蒸/烤/油炸/冷拌' AFTER `process_note`,
--     ADD COLUMN `max_temp`     INT UNSIGNED  NULL COMMENT '工艺最高温度，摄氏度'              AFTER `heat_method`,
--     ADD COLUMN `hold_minutes` INT UNSIGNED  NULL COMMENT '加热持续时长，分钟'                AFTER `max_temp`,
--     ADD COLUMN `ph_value`     DECIMAL(4,2)  NULL COMMENT '体系 pH'                          AFTER `hold_minutes`;

-- 升级 9.2（第二阶段把交互知识接入数值层时启用，当前阶段请勿执行）
-- ALTER TABLE `ingredient_pairing`
--     ADD COLUMN `coefficient`           DECIMAL(6,3) NULL COMMENT '交互系数'                                  AFTER `ratio_note`,
--     ADD COLUMN `coefficient_direction` VARCHAR(20)  NULL COMMENT '作用方向：PRIMARY/PAIRED/BOTH'            AFTER `coefficient`,
--     ADD COLUMN `effective_min`         DECIMAL(8,3) NULL COMMENT '适用浓度下限'                              AFTER `coefficient_direction`,
--     ADD COLUMN `effective_max`         DECIMAL(8,3) NULL COMMENT '适用浓度上限'                              AFTER `effective_min`,
--     ADD COLUMN `evidence_level`        VARCHAR(20)  NULL COMMENT '依据等级：LITERATURE/EXPERIMENT/EXPERIENCE/DEMO' AFTER `effective_max`,
--     ADD COLUMN `computable`            TINYINT      NOT NULL DEFAULT 0 COMMENT '是否允许进入数值计算：1是，0否' AFTER `evidence_level`;

-- ------------------------------------------------------------
-- 升级 9.3：知识卡增加「附加触发条件」字段（可重复执行，不会报错）
--
-- 背景：知识卡的适用条件有三层 —— 风味维度、研发目标、约束场景。
-- 引擎原本只能匹配前两层，于是「过敏原替代方向」这类只在特定约束下才成立的卡片，
-- 会在任何涉及该维度的分析里冒出来：用户明明没填过敏原，却收到一条过敏原替代建议。
--
-- 为什么用存储过程判断、而不是直接写 ALTER TABLE：
--   第 7 节的 CREATE TABLE 里已经包含该字段。全新安装时字段本来就已存在，
--   此时直接 ALTER 会报 1060 Duplicate column name 并中断后续语句
--   （9.4 的 UPDATE 就跑不到了）。这里先用 information_schema 判断再执行，
--   使「全新安装」与「已有数据库升级」两种情况都能安全通过。
--
-- 该过程是通用的，第二阶段的 9.1 / 9.2 也可以改成调用它。
-- ------------------------------------------------------------
DROP PROCEDURE IF EXISTS `fl_add_column_if_absent`;
DELIMITER $$
CREATE PROCEDURE `fl_add_column_if_absent`(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl    TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = p_table
           AND COLUMN_NAME  = p_column
    ) THEN
        SET @fl_ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE fl_stmt FROM @fl_ddl;
        EXECUTE fl_stmt;
        DEALLOCATE PREPARE fl_stmt;
    END IF;
END$$
DELIMITER ;

CALL `fl_add_column_if_absent`('flavor_knowledge', 'trigger_constraint',
    '`trigger_constraint` VARCHAR(30) NOT NULL DEFAULT ''NONE'' COMMENT ''附加触发条件：NONE无、ALLERGEN_ONLY仅当分析设置了过敏原约束时启用'' AFTER `goal_type`');

DROP PROCEDURE IF EXISTS `fl_add_column_if_absent`;

-- ------------------------------------------------------------
-- 数据修正 9.4：停用四条定位不符的知识卡 ＋ 给过敏原替代卡加上触发条件
--               （可重复执行，**本次必须执行**，须在 9.3 之后执行）
--
-- 背景：这四条的 `candidate_ingredient` 填的不是真实配料，而是方法论或策略描述。
-- 规则引擎按「维度 + 目标」取到它们后，会把「判读口径提示」当成候选配料输出，
-- 结果页因此出现无法理解的建议卡片（如「候选食材：判读口径提示」）。
--
-- 注意：第 8.2 节的 INSERT 使用 INSERT IGNORE —— 对**已存在**的行不会更新，
-- 所以仅仅修改 INSERT 里的 status 对已有数据库无效，必须执行下面这条 UPDATE。
-- ------------------------------------------------------------
UPDATE `flavor_knowledge` SET `status` = 0
 WHERE `knowledge_code` IN ('UK-FAT-004', 'UK-FAT-008', 'UK-GEN-001', 'UK-GEN-006');

-- 「过敏原替代：含大豆原料的替换方向」的内容只在用户填写了过敏原清单时才有意义；
-- 不设这道条件，它会在任何鲜味相关的分析里出现（用户没填过敏原也收到过敏原建议）。
UPDATE `flavor_knowledge` SET `trigger_constraint` = 'ALLERGEN_ONLY'
 WHERE `knowledge_code` = 'UK-GEN-005';

-- ------------------------------------------------------------
-- 数据修正 9.5（可重复执行）：给目标模板的成分层动作补上默认减量倍率
--
-- 为什么需要这条：第 3 节的 INSERT 走 INSERT IGNORE，对**已存在**的行不更新，
-- 所以先前已建好 goal_target 的库，两条 COMPONENT 行的 magnitude 仍是 NULL，
-- 配方合成器就不知道该减多少。这条 UPDATE 把它补成默认值。
--
-- `AND magnitude IS NULL` 这道条件既保证可重复执行，也保证**不会覆盖手工调过的倍率**。
-- ------------------------------------------------------------
UPDATE `goal_target` SET `magnitude` = 0.500
 WHERE `goal_type` = 'REDUCE_SUGAR' AND `target_kind` = 'COMPONENT' AND `magnitude` IS NULL;

UPDATE `goal_target` SET `magnitude` = 0.600
 WHERE `goal_type` = 'REDUCE_FAT' AND `target_kind` = 'COMPONENT' AND `magnitude` IS NULL;

-- ------------------------------------------------------------
-- 数据修正 9.6（可重复执行）：修正三氯蔗糖越界的甜度属性
--
-- 八维属性是 **0–100 的相对强度评分**（建表注释里写明），但三氯蔗糖的 sweet 被填成了 500
-- —— 那是"甜度倍数"的口径，与其余食材（白砂糖 100、甜菊糖苷 95、赤藓糖醇 65）不一致。
-- 越界值会让加权平均被单一样食材拉爆：极少量三氯蔗糖就能把整份配方的甜度推到远超 100。
-- 修正是"顶格"而不是"按倍数换算"，因为在本字段的语义下，三氯蔗糖本就是最甜的。
-- ------------------------------------------------------------
UPDATE `ingredient` SET `sweet` = 100
 WHERE `name` = '三氯蔗糖' AND `sweet` > 100;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 建库完成后的检查语句（可在 Navicat 中单独执行）
-- ============================================================
-- SHOW TABLES;
-- SELECT COUNT(*) AS ingredient_count FROM ingredient;
-- SELECT COUNT(*) AS region_count FROM region_profile;
-- SELECT COUNT(*) AS rule_count FROM flavor_rule;
-- SELECT COUNT(*) AS knowledge_count FROM flavor_knowledge;
-- SELECT COUNT(*) AS pairing_count FROM ingredient_pairing;
-- SELECT dimension, COUNT(*) AS card_count FROM flavor_knowledge WHERE status = 1 GROUP BY dimension ORDER BY card_count DESC;
