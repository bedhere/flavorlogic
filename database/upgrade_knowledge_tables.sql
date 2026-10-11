-- 食之有理：旧版 15 张表升级为 21 张表（仅新增 6 张知识库表）
-- 执行前请先备份现有 flavor_logic 数据库。
-- 不会修改原有 15 张表中的数据。
SET NAMES utf8mb4;
USE `flavor_logic`;

-- 10. 外部资料采集、审核与证据版本（本地优先，Yuxi 可选）
--
-- 说明：
--   · MySQL 保存来源、抓取任务、审核状态和结构化证据；原始文件由应用配置的
--     文件目录保存，Yuxi 只作为后续可选的文档检索服务，不直接成为主数据库。
--   · source_document 的内容进入审核队列后，只有 APPROVED 才允许被检索；
--     只有经过审核的 evidence_claim / ingredient_attribute_version 才能进入规则或数值层。
--   · 所有表均可重复执行创建。自动采集由 Java 白名单任务显式触发，Yuxi 凭据仍未配置。
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `source_site` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '来源站点编号',
    `site_name`           VARCHAR(120) NOT NULL COMMENT '站点名称',
    `base_url`            VARCHAR(500) NOT NULL COMMENT '站点或 API 根地址',
    `robots_url`          VARCHAR(500) NULL COMMENT 'robots.txt 地址',
    `terms_url`           VARCHAR(500) NULL COMMENT '服务条款地址',
    `license_type`        VARCHAR(80) NULL COMMENT '许可类型或使用条款摘要',
    `source_type`         VARCHAR(30) NOT NULL DEFAULT 'WEB' COMMENT '来源类型：API、PAPER、STANDARD、WEB、DATASET',
    `crawl_allowed`       TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许自动获取：1允许，0仅人工导入',
    `request_interval_ms` INT UNSIGNED NOT NULL DEFAULT 2000 COMMENT '请求最小间隔毫秒',
    `status`              TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    `notes`               VARCHAR(500) NULL COMMENT '审核与使用备注',
    `created_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_source_site_base_url` (`base_url`),
    KEY `idx_source_site_status_type` (`status`, `source_type`),
    CONSTRAINT `chk_source_site_crawl_allowed` CHECK (`crawl_allowed` IN (0, 1)),
    CONSTRAINT `chk_source_site_status` CHECK (`status` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外部资料来源站点表';

CREATE TABLE IF NOT EXISTS `crawl_job` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '抓取任务编号',
    `site_id`           BIGINT UNSIGNED NOT NULL COMMENT '来源站点编号',
    `seed_url`          VARCHAR(500) NULL COMMENT '本次任务起始地址',
    `status`            VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING、RUNNING、COMPLETED、FAILED、CANCELLED',
    `started_at`        DATETIME NULL,
    `finished_at`       DATETIME NULL,
    `fetched_count`     INT UNSIGNED NOT NULL DEFAULT 0,
    `success_count`     INT UNSIGNED NOT NULL DEFAULT 0,
    `failed_count`      INT UNSIGNED NOT NULL DEFAULT 0,
    `error_message`     VARCHAR(500) NULL,
    `created_by`        BIGINT UNSIGNED NULL COMMENT '创建任务的管理员',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_crawl_job_site_status` (`site_id`, `status`),
    KEY `idx_crawl_job_created_at` (`created_at`),
    CONSTRAINT `fk_crawl_job_site` FOREIGN KEY (`site_id`) REFERENCES `source_site` (`id`)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT `fk_crawl_job_user` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外部资料抓取任务表';

CREATE TABLE IF NOT EXISTS `source_document` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '来源文档编号',
    `site_id`           BIGINT UNSIGNED NULL COMMENT '来源站点编号',
    `crawl_job_id`      BIGINT UNSIGNED NULL COMMENT '抓取任务编号',
    `url`               VARCHAR(500) NOT NULL COMMENT '原文地址',
    `title`             VARCHAR(300) NOT NULL COMMENT '文档标题',
    `summary`           VARCHAR(1000) NULL COMMENT '摘要',
    `content_text`      LONGTEXT NOT NULL COMMENT '清洗后的纯文本或 Markdown',
    `content_hash`      CHAR(64) NOT NULL COMMENT '清洗正文 SHA-256 指纹',
    `source_type`       VARCHAR(30) NOT NULL DEFAULT 'WEB' COMMENT 'API、PAPER、STANDARD、WEB、DATASET',
    `license_type`      VARCHAR(80) NULL COMMENT '许可类型',
    `evidence_level`    VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED' COMMENT 'UNVERIFIED、LITERATURE、STANDARD、EXPERIMENT、EXPERIENCE、DEMO',
    `published_at`     DATETIME NULL COMMENT '原文发布时间',
    `fetched_at`       DATETIME NULL COMMENT '获取时间',
    `review_status`     VARCHAR(30) NOT NULL DEFAULT 'REVIEW_PENDING' COMMENT 'REVIEW_PENDING、APPROVED、REJECTED、ARCHIVED',
    `reviewer_id`       BIGINT UNSIGNED NULL COMMENT '审核管理员',
    `review_note`       VARCHAR(1000) NULL COMMENT '审核意见',
    `file_path`         VARCHAR(1000) NULL COMMENT '原始或规范化文件路径',
    `yuxi_kb_id`        VARCHAR(100) NULL COMMENT '可选 Yuxi 知识库编号',
    `yuxi_file_id`      VARCHAR(100) NULL COMMENT '可选 Yuxi 文件编号',
    `yuxi_status`       VARCHAR(30) NULL COMMENT 'uploaded、parsed、indexed、error',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_source_document_url` (`url`),
    UNIQUE KEY `uk_source_document_hash` (`content_hash`),
    KEY `idx_source_document_review` (`review_status`, `evidence_level`),
    KEY `idx_source_document_site` (`site_id`, `fetched_at`),
    FULLTEXT KEY `ft_source_document_text` (`title`, `summary`, `content_text`) WITH PARSER ngram,
    CONSTRAINT `fk_source_document_site` FOREIGN KEY (`site_id`) REFERENCES `source_site` (`id`)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT `fk_source_document_job` FOREIGN KEY (`crawl_job_id`) REFERENCES `crawl_job` (`id`)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT `fk_source_document_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `sys_user` (`id`)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外部来源文档与审核表';

CREATE TABLE IF NOT EXISTS `evidence_claim` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '证据声明编号',
    `document_id`     BIGINT UNSIGNED NOT NULL COMMENT '来源文档编号',
    `ingredient_id`   BIGINT UNSIGNED NULL COMMENT '关联食材，可为空',
    `dimension`       VARCHAR(30) NULL COMMENT '关联风味维度',
    `claim_type`      VARCHAR(30) NOT NULL DEFAULT 'KNOWLEDGE' COMMENT 'KNOWLEDGE、MEASUREMENT、LEGAL、PAIRING',
    `claim_text`      VARCHAR(2000) NOT NULL COMMENT '结构化结论',
    `quote_text`      VARCHAR(3000) NULL COMMENT '原文短摘录',
    `amount_min`      DECIMAL(10,4) NULL,
    `amount_max`      DECIMAL(10,4) NULL,
    `amount_unit`     VARCHAR(30) NULL,
    `method`          VARCHAR(255) NULL COMMENT '实验、检测或推导方法',
    `evidence_level`  VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED',
    `review_status`   VARCHAR(30) NOT NULL DEFAULT 'REVIEW_PENDING',
    `reviewer_id`     BIGINT UNSIGNED NULL,
    `review_note`     VARCHAR(1000) NULL,
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_evidence_claim_document` (`document_id`),
    KEY `idx_evidence_claim_ingredient_dimension` (`ingredient_id`, `dimension`, `review_status`),
    CONSTRAINT `fk_evidence_claim_document` FOREIGN KEY (`document_id`) REFERENCES `source_document` (`id`)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT `fk_evidence_claim_ingredient` FOREIGN KEY (`ingredient_id`) REFERENCES `ingredient` (`id`)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT `fk_evidence_claim_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `sys_user` (`id`)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审核后的结构化证据声明表';

CREATE TABLE IF NOT EXISTS `ingredient_attribute_version` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '属性版本编号',
    `ingredient_id`     BIGINT UNSIGNED NOT NULL COMMENT '食材编号',
    `dimension`         VARCHAR(30) NOT NULL COMMENT '风味维度',
    `value`             DECIMAL(12,4) NOT NULL COMMENT '原始或归一化数值',
    `value_unit`        VARCHAR(30) NULL COMMENT '数值单位',
    `basis`             VARCHAR(50) NULL COMMENT '鲜重、干重、溶液浓度等口径',
    `concentration`     VARCHAR(100) NULL COMMENT '测试浓度或配方浓度',
    `temperature`       VARCHAR(50) NULL COMMENT '测试温度',
    `measurement_method` VARCHAR(255) NULL COMMENT '检测或感官评价方法',
    `uncertainty`       VARCHAR(100) NULL COMMENT '误差或不确定性',
    `source_document_id` BIGINT UNSIGNED NULL COMMENT '来源文档',
    `evidence_level`    VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED',
    `review_status`     VARCHAR(30) NOT NULL DEFAULT 'REVIEW_PENDING',
    `version_no`        INT UNSIGNED NOT NULL DEFAULT 1,
    `reviewer_id`       BIGINT UNSIGNED NULL,
    `review_note`       VARCHAR(1000) NULL,
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_attribute_version` (`ingredient_id`, `dimension`, `version_no`),
    KEY `idx_attribute_version_review` (`ingredient_id`, `dimension`, `review_status`),
    CONSTRAINT `fk_attribute_version_ingredient` FOREIGN KEY (`ingredient_id`) REFERENCES `ingredient` (`id`)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT `fk_attribute_version_document` FOREIGN KEY (`source_document_id`) REFERENCES `source_document` (`id`)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT `fk_attribute_version_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `sys_user` (`id`)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='可追溯的食材属性版本表';

CREATE TABLE IF NOT EXISTS `rule_evidence` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '规则证据关联编号',
    `rule_id`               BIGINT UNSIGNED NULL COMMENT '风味规则编号',
    `knowledge_id`          BIGINT UNSIGNED NULL COMMENT '知识卡编号',
    `evidence_claim_id`     BIGINT UNSIGNED NOT NULL COMMENT '证据声明编号',
    `relation_type`         VARCHAR(30) NOT NULL DEFAULT 'SUPPORTS' COMMENT 'SUPPORTS、LIMITS、CONTEXT_ONLY',
    `created_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_evidence_link` (`rule_id`, `knowledge_id`, `evidence_claim_id`),
    KEY `idx_rule_evidence_claim` (`evidence_claim_id`),
    CONSTRAINT `fk_rule_evidence_rule` FOREIGN KEY (`rule_id`) REFERENCES `flavor_rule` (`id`)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT `fk_rule_evidence_knowledge` FOREIGN KEY (`knowledge_id`) REFERENCES `flavor_knowledge` (`id`)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT `fk_rule_evidence_claim` FOREIGN KEY (`evidence_claim_id`) REFERENCES `evidence_claim` (`id`)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='规则与证据声明关联表';

SET FOREIGN_KEY_CHECKS = 1;
