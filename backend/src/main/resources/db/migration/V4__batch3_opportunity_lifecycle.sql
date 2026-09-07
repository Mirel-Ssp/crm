-- =====================================================================
-- CRM 系统 · V4 批次3（OPP + FUP补全 + RPT + CUS补全）
-- 依据：PRD CRM-O1~O5 / CRM-F1~F2 / CRM-C1~C6；WBS OPP-DV-01~03、FUP-DV-02、
--       RPT-DV-01/02、CUS-DV-03/04；V2 报告遗留问题4（自助改密）
-- 说明：crm_opportunity / crm_followup(status) / crm_customer.custom_fields
--       均已在 V1 建表，本版本仅做增量列、留痕表、权限与字典种子
-- =====================================================================

-- ---------------- 权限点（6xx 商机 / 7xx 报表与合并） ----------------
INSERT INTO sys_permission (id, parent_id, code, name, type, sort, created_by, updated_by) VALUES
(601, 0, 'opp:list',        '商机查询',      'API', 60, 0, 0),
(602, 0, 'opp:create',      '商机新增/编辑', 'API', 61, 0, 0),
(603, 0, 'opp:stage',       '商机阶段流转',  'API', 62, 0, 0),
(604, 0, 'opp:win',         '商机成交',      'API', 63, 0, 0),
(605, 0, 'opp:lose',        '商机丢单',      'API', 64, 0, 0),
(606, 0, 'opp:delete',      '商机删除',      'API', 65, 0, 0),
(701, 0, 'report:view',     '报表查看/导出', 'API', 70, 0, 0),
(702, 0, 'customer:merge',  '客户合并',      'API', 71, 0, 0);

-- MANAGER(2)/SALES(3)：商机与报表；合并为客户管理动作仅 MANAGER（ADMIN 走全量授权）
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(2, 601), (2, 602), (2, 603), (2, 604), (2, 605), (2, 606), (2, 701), (2, 702),
(3, 601), (3, 602), (3, 603), (3, 604), (3, 605), (3, 606), (3, 701);

-- ---------------- 商机币种（CRM-O1：金额支持币种） ----------------
ALTER TABLE crm_opportunity ADD COLUMN currency VARCHAR(8) NOT NULL DEFAULT 'CNY';

-- ---------------- 客户生命周期（CRM-C4：潜在→跟进中→成交→流失→休眠） ----------------
ALTER TABLE crm_customer
    ADD COLUMN lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'POTENTIAL'
    CHECK (lifecycle_status IN ('POTENTIAL','FOLLOWING','WON','LOST','DORMANT'));
CREATE INDEX idx_customer_lifecycle ON crm_customer (lifecycle_status);

-- 生命周期/等级变更留痕（推进可追溯；商机成交联动 WON 亦经此表）
CREATE TABLE crm_customer_trace (
    id          BIGINT PRIMARY KEY,
    customer_id BIGINT      NOT NULL REFERENCES crm_customer (id),
    field       VARCHAR(32) NOT NULL,                        -- lifecycle_status / level
    from_value  VARCHAR(32),
    to_value    VARCHAR(32) NOT NULL,
    operator_id BIGINT      NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_customer_trace ON crm_customer_trace (customer_id, created_at DESC);

-- 商机阶段流转留痕（CRM-O2：推进/回退均留痕）
CREATE TABLE crm_opportunity_trace (
    id          BIGINT PRIMARY KEY,
    opp_id      BIGINT     NOT NULL REFERENCES crm_opportunity (id),
    from_stage  SMALLINT,
    to_stage    SMALLINT   NOT NULL,
    operator_id BIGINT     NOT NULL,
    reason      VARCHAR(255),
    created_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_opp_trace ON crm_opportunity_trace (opp_id, created_at DESC);

-- ---------------- 首登强制改密标记（V2 遗留问题4） ----------------
ALTER TABLE sys_user ADD COLUMN must_change_password SMALLINT NOT NULL DEFAULT 0;

-- ---------------- 商机阶段字典（与 V1 CHECK(1..7) 对应：6=成交 7=丢单终态） ----------------
INSERT INTO sys_dict (id, dict_type, code, value, sort, created_by, updated_by) VALUES
(1301, 'opportunity_stage', '1', '初步接触', 1, 0, 0),
(1302, 'opportunity_stage', '2', '需求确认', 2, 0, 0),
(1303, 'opportunity_stage', '3', '方案报价', 3, 0, 0),
(1304, 'opportunity_stage', '4', '商务谈判', 4, 0, 0),
(1305, 'opportunity_stage', '5', '成交准备', 5, 0, 0),
(1306, 'opportunity_stage', '6', '已成交',   6, 0, 0),
(1307, 'opportunity_stage', '7', '已丢单',   7, 0, 0);

-- 丢单原因字典（CRM-O4 统计导出用）
INSERT INTO sys_dict (id, dict_type, code, value, sort, created_by, updated_by) VALUES
(1401, 'lose_reason', 'PRICE',     '价格因素',   1, 0, 0),
(1402, 'lose_reason', 'BUDGET',    '预算不足',   2, 0, 0),
(1403, 'lose_reason', 'COMPETITOR','竞品夺单',   3, 0, 0),
(1404, 'lose_reason', 'TIMING',    '时机不成熟', 4, 0, 0),
(1405, 'lose_reason', 'OTHER',     '其他原因',   9, 0, 0);
