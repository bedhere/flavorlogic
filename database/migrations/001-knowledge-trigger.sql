-- 食之有理：按用途拆分的 SQL 正本；执行顺序见 database/README.md。
SET NAMES utf8mb4;
USE `flavor_logic`;

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

UPDATE `flavor_knowledge` SET `status` = 0
 WHERE `knowledge_code` IN ('UK-FAT-004', 'UK-FAT-008', 'UK-GEN-001', 'UK-GEN-006');

UPDATE `flavor_knowledge` SET `trigger_constraint` = 'ALLERGEN_ONLY'
 WHERE `knowledge_code` = 'UK-GEN-005';

UPDATE `goal_target` SET `magnitude` = 0.500
 WHERE `goal_type` = 'REDUCE_SUGAR' AND `target_kind` = 'COMPONENT' AND `magnitude` IS NULL;

UPDATE `goal_target` SET `magnitude` = 0.600
 WHERE `goal_type` = 'REDUCE_FAT' AND `target_kind` = 'COMPONENT' AND `magnitude` IS NULL;

UPDATE `ingredient` SET `sweet` = 100
 WHERE `name` = '三氯蔗糖' AND `sweet` > 100;
