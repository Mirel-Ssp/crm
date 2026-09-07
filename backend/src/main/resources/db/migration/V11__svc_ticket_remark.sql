-- =====================================================================
-- CRM 系统 · V11：service_ticket 补 remark 列
-- 背景：V1 预建表无 remark，批次5 实体新增该字段（工单处理备注/解决说明），
--       导致工单任何查询 BadSqlGrammar: column "remark" does not exist（50000）。
-- =====================================================================
ALTER TABLE service_ticket ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
