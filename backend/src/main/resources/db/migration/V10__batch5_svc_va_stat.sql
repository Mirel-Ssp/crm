-- =====================================================================
-- CRM 系统 · V10 批次5（SVC 服务工单 / VA 价值评分 / STAT 多维统计）
-- service_ticket 已在 V1 预建，本版新增：
--   1) customer_score 客户价值评分表（每客户每日一条，PRD 实体 CustomerScore）
--   2) va_rule 五维权重与沉默阈值配置（键值型，管理员可调）
--   3) business_stat 交易聚合预计算表（日/月/季/年，PRD 实体 BusinessStat）
--   4) crm_followup.rel_type 放宽加入 TICKET（回访任务复用统一跟进流）
--   5) service_ticket 补查询索引
--   6) 权限点 901~906 + 三角色授权
--   7) 字典：工单类型（1415~1418）
-- =====================================================================

-- ---------------- VA-1 客户价值评分（每日一条历史，取 max(calc_date) 为当前值） ----------------
CREATE TABLE customer_score (
    id             BIGINT PRIMARY KEY,
    customer_id    BIGINT       NOT NULL REFERENCES crm_customer (id),
    score          NUMERIC(6,2) NOT NULL CHECK (score >= 0 AND score <= 100),
    tier           VARCHAR(16)  NOT NULL CHECK (tier IN ('HIGH_VALUE','POTENTIAL','TO_ACTIVATE','AT_RISK')),
    dim_freq       NUMERIC(6,2) NOT NULL DEFAULT 0,  -- 交易频率分项（30%）
    dim_amount     NUMERIC(6,2) NOT NULL DEFAULT 0,  -- 累计交易额分项（30%）
    dim_active     NUMERIC(6,2) NOT NULL DEFAULT 0,  -- 最近活跃分项（20%）
    dim_remittance NUMERIC(6,2) NOT NULL DEFAULT 0,  -- 汇款及时性分项（10%）
    dim_followup   NUMERIC(6,2) NOT NULL DEFAULT 0,  -- 跟进响应度分项（10%）
    manual         SMALLINT     NOT NULL DEFAULT 0,  -- 1=已手动调整，自动重算跳过
    calc_date      DATE         NOT NULL,
    created_by     BIGINT       NOT NULL DEFAULT 0,
    updated_by     BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (customer_id, calc_date)
);
CREATE INDEX idx_cscore_customer ON customer_score (customer_id, calc_date DESC);
CREATE INDEX idx_cscore_tier     ON customer_score (tier, calc_date);

-- ---------------- VA 权重/阈值配置（VA-1 权重可配 + VA-5 沉默阈值） ----------------
CREATE TABLE va_rule (
    id         BIGINT PRIMARY KEY,
    rule_key   VARCHAR(32)  NOT NULL UNIQUE,
    rule_value NUMERIC(8,4) NOT NULL,
    remark     VARCHAR(255),
    updated_by BIGINT       NOT NULL DEFAULT 0,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO va_rule (id, rule_key, rule_value, remark, updated_by) VALUES
(1, 'WEIGHT_FREQ',     0.30, '交易频率权重（VA-1）',            0),
(2, 'WEIGHT_AMOUNT',   0.30, '累计交易额权重（VA-1）',          0),
(3, 'WEIGHT_ACTIVE',   0.20, '最近活跃权重（VA-1）',            0),
(4, 'WEIGHT_REMIT',    0.10, '汇款及时性权重（VA-1）',          0),
(5, 'WEIGHT_FOLLOWUP', 0.10, '跟进响应度权重（VA-1）',          0),
(6, 'SILENT_DAYS',     30,   '沉默客户阈值：超 N 天无成交且无跟进（VA-5）', 0);

-- ---------------- ST 聚合预计算表（ST-1 业务总量；owner_id/item_id=0 为全量汇总行） ----------------
CREATE TABLE business_stat (
    id             BIGINT PRIMARY KEY,
    stat_dim       VARCHAR(4)    NOT NULL CHECK (stat_dim IN ('DAY','MONTH','QUARTER','YEAR')),
    stat_date      DATE          NOT NULL,              -- 周期代表日（日=当日/月=月初/季=季初/年=年初）
    owner_id       BIGINT        NOT NULL DEFAULT 0,    -- 0=全部业务员；>0=按业务员
    item_id        BIGINT        NOT NULL DEFAULT 0,    -- 0=全部标的；>0=按标的
    order_count    BIGINT        NOT NULL DEFAULT 0,    -- 交易笔数
    order_amount   NUMERIC(18,4) NOT NULL DEFAULT 0,    -- 交易金额（全部状态订单）
    customer_count BIGINT        NOT NULL DEFAULT 0,    -- 活跃客户数（有成交去重）
    remit_amount   NUMERIC(18,4) NOT NULL DEFAULT 0,    -- 已确认到账金额
    arrive_rate    NUMERIC(6,4)  NOT NULL DEFAULT 0,    -- 到账率 = remit_amount/order_amount
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (stat_dim, stat_date, owner_id, item_id)
);
CREATE INDEX idx_bstat_dim_date  ON business_stat (stat_dim, stat_date);
CREATE INDEX idx_bstat_owner     ON business_stat (owner_id, stat_dim, stat_date);
CREATE INDEX idx_bstat_item      ON business_stat (item_id, stat_dim, stat_date);

-- ---------------- 回访任务复用统一跟进流（CRM-S2）：rel_type 放宽加入 TICKET ----------------
ALTER TABLE crm_followup DROP CONSTRAINT crm_followup_rel_type_check;
ALTER TABLE crm_followup ADD CONSTRAINT crm_followup_rel_type_check
    CHECK (rel_type IN ('CUSTOMER','LEAD','OPPORTUNITY','TICKET'));

-- ---------------- service_ticket 查询索引（CRM-S1） ----------------
CREATE INDEX idx_ticket_status   ON service_ticket (status, deleted);
CREATE INDEX idx_ticket_assignee ON service_ticket (assignee_id, status, deleted);
CREATE INDEX idx_ticket_customer ON service_ticket (customer_id, deleted);

-- ---------------- 权限点（9xx） ----------------
INSERT INTO sys_permission (id, parent_id, code, name, type, sort, created_by, updated_by) VALUES
(901, 0, 'svc:ticket:list',        '工单查询',          'API', 90, 0, 0),
(902, 0, 'svc:ticket:manage',      '工单创建/流转',     'API', 91, 0, 0),
(903, 0, 'svc:satisfaction',       '回访与满意度统计',  'API', 92, 0, 0),
(904, 0, 'va:score:list',          '价值评分查询',      'API', 93, 0, 0),
(905, 0, 'va:score:manage',        '评分重算/调整/唤醒', 'API', 94, 0, 0),
(906, 0, 'stat:report',            '多维统计与导出',    'API', 95, 0, 0);

-- ADMIN 全量
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 901 AND 906;

-- MANAGER(2)：全业务权限（评分重算/唤醒分配/工单指派/统计导出）
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(2, 901), (2, 902), (2, 903), (2, 904), (2, 905), (2, 906);

-- SALES(3)：工单查询/创建与处理、回访、评分查询、统计
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(3, 901), (3, 902), (3, 903), (3, 904), (3, 906);

-- ---------------- 字典：工单类型（1415~1418） ----------------
INSERT INTO sys_dict (id, dict_type, code, value, sort, created_by, updated_by) VALUES
(1415, 'ticket_type', 'CONSULT',   '咨询',   1, 0, 0),
(1416, 'ticket_type', 'COMPLAINT', '投诉',   2, 0, 0),
(1417, 'ticket_type', 'AFTER_SALE','售后',   3, 0, 0),
(1418, 'ticket_type', 'OTHER',     '其他',   4, 0, 0);
