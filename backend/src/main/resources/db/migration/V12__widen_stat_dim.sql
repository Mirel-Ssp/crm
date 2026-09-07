-- =====================================================================
-- CRM 系统 · V12：business_stat.stat_dim 放宽 VARCHAR(4) → VARCHAR(8)
-- 背景：MONTH(5)/QUARTER(7) 超长，聚合重建插入报
--       "value too long for type character varying(4)"（50000）。
-- =====================================================================
ALTER TABLE business_stat ALTER COLUMN stat_dim TYPE VARCHAR(8);
