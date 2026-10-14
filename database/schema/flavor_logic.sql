-- 食之有理：按用途拆分的 SQL 正本；执行顺序见 database/README.md。
SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS `flavor_logic` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `flavor_logic`;
SET FOREIGN_KEY_CHECKS=0;

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
    `claim_type`      VARCHAR(30) NOT NULL DEFAULT 'KNOWLEDGE' COMMENT 'KNOWLEDGE、MEASUREMENT、LEGAL、PAIRING、PRODUCT_LABEL',
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

SET FOREIGN_KEY_CHECKS=1;
