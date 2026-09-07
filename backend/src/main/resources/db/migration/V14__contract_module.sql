-- =====================================================================
-- CRM 系统 · V14 批次7（R3 合同管理）
--   1) sales_contract 合同表（签署状态机 + 到期预警 + 乐观锁）
--      UNSIGNED → SIGNED → EXECUTING → EXPIRED（到期由调度器推进）
--      UNSIGNED/SIGNED/EXECUTING → TERMINATED（提前终止）
--   2) 权限点 1101~1102 + 三角色授权
--   3) 字典：合同状态（1431~1435）
-- =====================================================================

-- ---------------- 合同主表（CRM-R3） ----------------
CREATE TABLE sales_contract (
    id               BIGINT PRIMARY KEY,
    contract_no      VARCHAR(32)   NOT NULL UNIQUE,
    title            VARCHAR(128)  NOT NULL,
    customer_id      BIGINT        NOT NULL REFERENCES crm_customer (id),
    opportunity_id   BIGINT        REFERENCES crm_opportunity (id),
    order_id         BIGINT        REFERENCES trade_order (id),
    quote_id         BIGINT        REFERENCES sales_quote (id),
    owner_id         BIGINT        NOT NULL,
    amount           NUMERIC(18,4) NOT NULL DEFAULT 0,
    sign_status      VARCHAR(24)   NOT NULL DEFAULT 'UNSIGNED'
        CHECK (sign_status IN ('UNSIGNED','SIGNED','EXECUTING','EXPIRED','TERMINATED')),
    start_date       DATE,
    end_date         DATE,
    reminder_days    INT           NOT NULL DEFAULT 30,   -- 到期预警提前天数
    attachment_url   VARCHAR(500),                        -- 附件（预留 MinIO 落地）
    signed_at        TIMESTAMP,
    terminated_at    TIMESTAMP,
    terminate_reason VARCHAR(500),
    remark           VARCHAR(500),
    version          BIGINT        NOT NULL DEFAULT 0,    -- 乐观锁
    created_by       BIGINT        NOT NULL DEFAULT 0,
    updated_by       BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT      NOT NULL DEFAULT 0
);
-- 索引名加 sales_ 前缀：PG 索引名 schema 内唯一，遗留表 rms_contract 已占用 idx_contract_customer
CREATE INDEX idx_sales_contract_customer ON sales_contract (customer_id, deleted);
CREATE INDEX idx_sales_contract_owner    ON sales_contract (owner_id, deleted);
CREATE INDEX idx_sales_contract_status   ON sales_contract (sign_status, deleted);
CREATE INDEX idx_sales_contract_end_date ON sales_contract (end_date) WHERE deleted = 0;

-- ---------------- 权限点（11xx） ----------------
INSERT INTO sys_permission (id, parent_id, code, name, type, sort, created_by, updated_by) VALUES
(1101, 0, 'contract:list',   '合同查询',                'API', 110, 0, 0),
(1102, 0, 'contract:manage', '合同创建/编辑/签署/终止', 'API', 111, 0, 0);

-- ADMIN 全量
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 1101 AND 1102;

-- MANAGER(2)：查询 + 管理
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(2, 1101), (2, 1102);

-- SALES(3)：查询 + 管理
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(3, 1101), (3, 1102);

-- ---------------- 字典：合同状态（1431~1435） ----------------
INSERT INTO sys_dict (id, dict_type, code, value, sort, created_by, updated_by) VALUES
(1431, 'contract_status', 'UNSIGNED',   '未签署', 1, 0, 0),
(1432, 'contract_status', 'SIGNED',     '已签署', 2, 0, 0),
(1433, 'contract_status', 'EXECUTING',  '履行中', 3, 0, 0),
(1434, 'contract_status', 'EXPIRED',    '已到期', 4, 0, 0),
(1435, 'contract_status', 'TERMINATED', '已终止', 5, 0, 0);
