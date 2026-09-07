-- 迁移核验：表数量、Flyway 历史、关键约束抽查
SELECT 'TABLES' AS item, COUNT(*)::TEXT AS val FROM information_schema.tables WHERE table_schema = 'public'
UNION ALL
SELECT 'FLYWAY', string_agg(version || ':' || success::TEXT, ', ' ORDER BY installed_rank) FROM flyway_schema_history
UNION ALL
SELECT 'ORDER_CHECK', conname FROM pg_constraint WHERE conname = 'trade_order_amount_check'
UNION ALL
SELECT 'TRGM_IDX', indexname FROM pg_indexes WHERE indexname = 'idx_customer_name_trgm'
UNION ALL
SELECT 'JSONB_GIN', indexname FROM pg_indexes WHERE indexname = 'idx_customer_custom';
