-- =====================================================================
-- CRM 系统 · V13 批次7（R1 报价单管理）
--   1) sales_quote 报价单主表（状态机 + 乐观锁）
--      DRAFT → SUBMITTED → APPROVED / REJECTED
--      APPROVED → CONVERTED（转订单）/ DRAFT|SUBMITTED → VOID（作废）
--   2) sales_quote_item 报价明细子表（自由行 + 可选关联交易标的）
--   3) 权限点 1001~1003 + 三角色授权
--   4) 字典：报价单状态（1421~1426）
-- =====================================================================

-- ---------------- 报价单主表（CRM-R1） ----------------
CREATE TABLE sales_quote (
    id              BIGINT PRIMARY KEY,
    quote_no        VARCHAR(32)   NOT NULL UNIQUE,
    title           VARCHAR(128)  NOT NULL,
    opportunity_id  BIGINT        REFERENCES crm_opportunity (id),
    customer_id     BIGINT        NOT NULL REFERENCES crm_customer (id),
    owner_id        BIGINT        NOT NULL,
    status          VARCHAR(24)   NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CONVERTED','VOID')),
    discount_rate   NUMERIC(6,4)  NOT NULL DEFAULT 1,   -- 折扣率（1=不打折）
    tax_rate        NUMERIC(6,4)  NOT NULL DEFAULT 0,    -- 税率
    total_amount    NUMERIC(18,4) NOT NULL DEFAULT 0,    -- 明细合计（服务端计算）
    discount_amount NUMERIC(18,4) NOT NULL DEFAULT 0,    -- total × discount_rate
    tax_amount      NUMERIC(18,4) NOT NULL DEFAULT 0,    -- discount_amount × tax_rate
    final_amount    NUMERIC(18,4) NOT NULL DEFAULT 0,    -- discount_amount + tax_amount
    valid_until     DATE,                                   -- 有效期
    approved_at     TIMESTAMP,
    rejected_reason VARCHAR(500),
    remark          VARCHAR(500),
    version         BIGINT        NOT NULL DEFAULT 0,    -- 乐观锁
    created_by      BIGINT        NOT NULL DEFAULT 0,
    updated_by      BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_quote_customer ON sales_quote (customer_id, deleted);
CREATE INDEX idx_quote_owner    ON sales_quote (owner_id, deleted);
CREATE INDEX idx_quote_status   ON sales_quote (status, deleted);

-- ---------------- 报价明细子表（自由行，可选关联交易标的） ----------------
CREATE TABLE sales_quote_item (
    id          BIGINT PRIMARY KEY,
    quote_id    BIGINT        NOT NULL REFERENCES sales_quote (id),
    item_id     BIGINT        REFERENCES trade_item (id),
    name        VARCHAR(128)  NOT NULL,
    spec        VARCHAR(255),
    quantity    NUMERIC(18,4) NOT NULL DEFAULT 1,
    price       NUMERIC(18,4) NOT NULL DEFAULT 0,
    amount      NUMERIC(18,4) NOT NULL DEFAULT 0,        -- quantity × price（服务端计算）
    sort        INT           NOT NULL DEFAULT 0,
    created_by  BIGINT        NOT NULL DEFAULT 0,
    updated_by  BIGINT        NOT NULL DEFAULT 0,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_quote_item_quote ON sales_quote_item (quote_id, deleted);

-- ---------------- 权限点（10xx） ----------------
INSERT INTO sys_permission (id, parent_id, code, name, type, sort, created_by, updated_by) VALUES
(1001, 0, 'quote:list',    '报价单查询',              'API', 100, 0, 0),
(1002, 0, 'quote:manage',  '报价单创建/编辑/提交/作废', 'API', 101, 0, 0),
(1003, 0, 'quote:approve', '报价单审批',              'API', 102, 0, 0);

-- ADMIN 全量
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 1001 AND 1003;

-- MANAGER(2)：查询 + 创建编辑 + 审批
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(2, 1001), (2, 1002), (2, 1003);

-- SALES(3)：查询 + 创建编辑（无审批权）
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(3, 1001), (3, 1002);

-- ---------------- 字典：报价单状态（1421~1426） ----------------
INSERT INTO sys_dict (id, dict_type, code, value, sort, created_by, updated_by) VALUES
(1421, 'quote_status', 'DRAFT',     '草稿',     1, 0, 0),
(1422, 'quote_status', 'SUBMITTED', '待审批',   2, 0, 0),
(1423, 'quote_status', 'APPROVED',  '已审批',   3, 0, 0),
(1424, 'quote_status', 'REJECTED',  '已驳回',   4, 0, 0),
(1425, 'quote_status', 'CONVERTED', '已转订单', 5, 0, 0),
(1426, 'quote_status', 'VOID',      '已作废',   6, 0, 0);
