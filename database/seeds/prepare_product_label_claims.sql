-- 从已保存的 Open Food Facts API 产品记录生成待审核的标签字段声明。
-- 仅准备草稿，不批准文档或声明，不更新 ingredient / 风味规则。
-- 可重复执行：同一来源文档、同一字段和值不会重复生成。

SET NAMES utf8mb4;
USE `flavor_logic`;

INSERT INTO `evidence_claim` (
    `document_id`, `ingredient_id`, `dimension`, `claim_type`, `claim_text`,
    `quote_text`, `amount_min`, `amount_max`, `amount_unit`, `method`,
    `evidence_level`, `review_status`
)
SELECT
    d.`id`, NULL, NULL, 'PRODUCT_LABEL',
    CONCAT(d.`title`, '：Open Food Facts API 中“', m.`metric_name`, '”字段为 ',
           JSON_UNQUOTE(JSON_EXTRACT(d.`content_text`, CONCAT('$.product.nutriments.', m.`field_name`))),
           ' g/100g。仅代表该条码产品，待对照包装标签核验。'),
    CONCAT('product.nutriments.', m.`field_name`, ' = ',
           JSON_UNQUOTE(JSON_EXTRACT(d.`content_text`, CONCAT('$.product.nutriments.', m.`field_name`)))),
    CAST(JSON_UNQUOTE(JSON_EXTRACT(d.`content_text`, CONCAT('$.product.nutriments.', m.`field_name`))) AS DECIMAL(10,4)),
    CAST(JSON_UNQUOTE(JSON_EXTRACT(d.`content_text`, CONCAT('$.product.nutriments.', m.`field_name`))) AS DECIMAL(10,4)),
    'g/100g', NULL, 'UNVERIFIED', 'REVIEW_PENDING'
FROM `source_document` d
JOIN `source_site` s ON s.`id` = d.`site_id`
CROSS JOIN (
    SELECT 'sugars_100g' AS `field_name`, '糖' AS `metric_name`
    UNION ALL SELECT 'fat_100g', '脂肪'
    UNION ALL SELECT 'salt_100g', '盐'
) m
WHERE s.`site_name` = 'Open Food Facts'
  AND d.`review_status` = 'APPROVED'
  AND JSON_VALID(d.`content_text`) = 1
  AND JSON_EXTRACT(d.`content_text`, CONCAT('$.product.nutriments.', m.`field_name`)) IS NOT NULL
  AND JSON_UNQUOTE(JSON_EXTRACT(d.`content_text`, CONCAT('$.product.nutriments.', m.`field_name`)))
      REGEXP '^[0-9]+([.][0-9]+)?$'
  AND CAST(JSON_UNQUOTE(JSON_EXTRACT(d.`content_text`, CONCAT('$.product.nutriments.', m.`field_name`))) AS DECIMAL(10,4)) BETWEEN 0 AND 100
  AND NOT EXISTS (
      SELECT 1 FROM `evidence_claim` c
      WHERE c.`document_id` = d.`id`
        AND c.`claim_type` = 'PRODUCT_LABEL'
        AND c.`quote_text` = CONCAT('product.nutriments.', m.`field_name`, ' = ',
            JSON_UNQUOTE(JSON_EXTRACT(d.`content_text`, CONCAT('$.product.nutriments.', m.`field_name`))))
  );
