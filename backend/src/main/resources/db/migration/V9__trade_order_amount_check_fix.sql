-- V9：修复 V1 trade_order 金额 CHECK 的精度缺陷
-- 原约束 trade_order_check：CHECK (amount = quantity * price)：quantity/price 均为 NUMERIC(18,4)，
-- 乘积精度可达 8 位小数，而 amount 列仅 4 位，服务端四舍五入写入后会被误拒。
-- 服务端统一 round(quantity*price, 4) 计价（OD-1），本版将约束两侧对齐。
ALTER TABLE trade_order DROP CONSTRAINT trade_order_check;
ALTER TABLE trade_order ADD CONSTRAINT trade_order_check
    CHECK (amount = round(quantity * price, 4));
