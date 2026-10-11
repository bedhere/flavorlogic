-- 食之有理：首批外部数据源候选登记
--
-- 说明：这些记录默认 crawl_allowed=0，不会自动访问网络。
-- 启用前必须由管理员确认当前服务条款、许可、robots 和字段映射。
-- 本文件只负责登记来源，不导入任何外部数据。

INSERT INTO source_site
    (site_name, base_url, robots_url, terms_url, license_type, source_type,
     crawl_allowed, request_interval_ms, status, notes)
VALUES
    ('USDA FoodData Central',
     'https://fdc.nal.usda.gov/',
     'https://fdc.nal.usda.gov/robots.txt',
     'https://fdc.nal.usda.gov/api-guide/',
     'USDA 数据使用条款；使用前复核',
     'API', 0, 2000, 1,
     '用于营养成分候选数据。API 字段需映射到每 100g 或每份口径，不能直接覆盖风味评分。'),
    ('Open Food Facts',
     'https://world.openfoodfacts.org/',
     'https://world.openfoodfacts.org/robots.txt',
     'https://world.openfoodfacts.org/terms-of-use',
     'ODbL；需保留来源与许可信息',
     'DATASET', 0, 3000, 1,
     '用于包装食品标签和营养字段候选数据。数据质量需要逐条审核，不作为科学证据的唯一来源。'),
    ('PubMed / NCBI',
     'https://pubmed.ncbi.nlm.nih.gov/',
     'https://pubmed.ncbi.nlm.nih.gov/robots.txt',
     'https://www.ncbi.nlm.nih.gov/home/about/policies/',
     '按文章版权和 NCBI 使用政策处理',
     'PAPER', 0, 5000, 1,
     '用于检索文献元数据和摘要。全文只有在许可明确时保存，优先保存 PMID、摘要、引用和证据等级。')
ON DUPLICATE KEY UPDATE
    site_name = VALUES(site_name),
    robots_url = VALUES(robots_url),
    terms_url = VALUES(terms_url),
    license_type = VALUES(license_type),
    source_type = VALUES(source_type),
    request_interval_ms = VALUES(request_interval_ms),
    status = VALUES(status),
    notes = VALUES(notes);
