-- =====================================================================
-- CRM 系统 · V8 批次4 交易板块（TRD / ORD / REM / RTP / WSP）
-- 交易五表（trade_item/trade_order/trade_order_log/trade_remittance/
-- trade_order_settlement/notify_message）已在 V1 预建，本版补齐：
--   1) trade_item 手续费率/最小交易量列（TB-1）
--   2) 调价留痕表（TB-2）
--   3) 交易规则表（TB-3：审批阈值/单笔限额）
--   4) 交易权限点 801~808 + 三角色授权（含 V2 遗留的 201/202/301/302 下放）
--   5) 字典：标的类型 / 汇款方式
-- =====================================================================

-- ---------------- TB-1 标的补列：手续费率 / 最小交易量 ----------------
ALTER TABLE trade_item ADD COLUMN fee_rate     NUMERIC(9,6)   NOT NULL DEFAULT 0;
ALTER TABLE trade_item ADD COLUMN min_quantity NUMERIC(18,4)  NOT NULL DEFAULT 1;

-- ---------------- TB-2 价格变更留痕 ----------------
CREATE TABLE trade_item_price_log (
    id         BIGINT PRIMARY KEY,
    item_id    BIGINT        NOT NULL REFERENCES trade_item (id),
    old_price  NUMERIC(18,4),
    new_price  NUMERIC(18,4) NOT NULL,
    operator_id BIGINT       NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_itempricelog_item ON trade_item_price_log (item_id, created_at DESC);

-- ---------------- TB-3 交易规则（键值型，管理员可调） ----------------
CREATE TABLE trade_rule (
    id         BIGINT PRIMARY KEY,
    rule_key   VARCHAR(32)   NOT NULL UNIQUE,
    rule_value NUMERIC(18,2) NOT NULL,
    remark     VARCHAR(255),
    updated_by BIGINT        NOT NULL DEFAULT 0,
    updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO trade_rule (id, rule_key, rule_value, remark, updated_by) VALUES
(1, 'APPROVAL_THRESHOLD', 100000,  '订单总额超过该金额需经理审批（TB-3/OD-1）', 0),
(2, 'MAX_ORDER_AMOUNT',   1000000, '单笔订单限额，超过拒绝创建（TB-3）',        0);

-- ---------------- 交易权限点（8xx） ----------------
INSERT INTO sys_permission (id, parent_id, code, name, type, sort, created_by, updated_by) VALUES
(801, 0, 'trade:item:list',    '标的/规则查询', 'API', 80, 0, 0),
(802, 0, 'trade:item:manage',  '标的/规则管理', 'API', 81, 0, 0),
(803, 0, 'trade:order:list',   '订单查询',      'API', 82, 0, 0),
(804, 0, 'trade:order:create', '订单创建',      'API', 83, 0, 0),
(805, 0, 'trade:remit:list',   '汇款查询',      'API', 84, 0, 0),
(806, 0, 'trade:remit:create', '汇款登记',      'API', 85, 0, 0),
(807, 0, 'trade:feed',         '动态流/通知',   'API', 86, 0, 0),
(808, 0, 'trade:workbench',    '交易工作台',    'API', 87, 0, 0);

-- ADMIN 全量（与 V2 模式一致）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 801 AND 808;

-- MANAGER(2)：交易全流程业务权限 + V2 预留的审批/取消/到账确认/核销
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(2, 801), (2, 803), (2, 804), (2, 805), (2, 806), (2, 807), (2, 808),
(2, 201), (2, 202), (2, 301), (2, 302);

-- SALES(3)：标的/订单/汇款查询与创建、动态流、工作台 + 本人订单取消
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(3, 801), (3, 803), (3, 804), (3, 805), (3, 806), (3, 807), (3, 808),
(3, 202);

-- ---------------- 字典：标的类型 / 汇款方式（1401~1405 已被 V4 lose_reason 占用） ----------------
INSERT INTO sys_dict (id, dict_type, code, value, sort, created_by, updated_by) VALUES
(1406, 'item_category', 'POINT',   '积分',     1, 0, 0),
(1407, 'item_category', 'VOUCHER', '兑换券',   2, 0, 0),
(1408, 'item_category', 'DIGITAL', '数字藏品', 3, 0, 0),
(1409, 'item_category', 'OTHER',   '其他',     4, 0, 0),
(1411, 'trade_remit_method', 'BANK',   '银行转账', 1, 0, 0),
(1412, 'trade_remit_method', 'WECHAT', '微信',     2, 0, 0),
(1413, 'trade_remit_method', 'ALIPAY', '支付宝',   3, 0, 0),
(1414, 'trade_remit_method', 'CASH',   '现金',     4, 0, 0);
