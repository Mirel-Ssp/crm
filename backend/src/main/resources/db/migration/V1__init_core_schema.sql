-- =====================================================================
-- CRM 系统 · V1 核心库表（INF-DV-04）
-- 依据：需求基线 v1.0 §6 数据模型；INF-DS-02 §4 公共字段与强约束
-- 约定：id 雪花算法由应用侧生成；金额一律 NUMERIC(18,4)+CHECK>=0；
--       公共审计字段 created_by/updated_by/created_at/updated_at/deleted；
--       状态字段 VARCHAR+CHECK 显式枚举（状态机自文档化）
-- =====================================================================

-- pg_trgm：GIN  trigram 索引（crm_customer.name 模糊搜索）
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ========================= CRM-M 系统管理 =========================

-- 组织架构（树形）
CREATE TABLE sys_org (
    id            BIGINT PRIMARY KEY,
    parent_id     BIGINT       NOT NULL DEFAULT 0,          -- 0=根节点
    name          VARCHAR(64)  NOT NULL,
    leader_id     BIGINT,                                    -- 团队负责人（数据范围判定用）
    sort          INT          NOT NULL DEFAULT 0,
    created_by    BIGINT       NOT NULL DEFAULT 0,
    updated_by    BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_sys_org_parent ON sys_org (parent_id);

-- 用户（密码 bcrypt；需求 §5 安全）
CREATE TABLE sys_user (
    id            BIGINT PRIMARY KEY,
    org_id        BIGINT       NOT NULL REFERENCES sys_org (id),
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password      VARCHAR(100) NOT NULL,                     -- bcrypt
    real_name     VARCHAR(64)  NOT NULL,
    email         VARCHAR(128),
    phone         VARCHAR(32),
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','LOCKED','DISABLED')),
    last_login_at TIMESTAMP,
    created_by    BIGINT       NOT NULL DEFAULT 0,
    updated_by    BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_sys_user_org ON sys_user (org_id);

-- 角色（数据范围三级：SELF 本人 / TEAM 本团队 / ALL 全部）
CREATE TABLE sys_role (
    id         BIGINT PRIMARY KEY,
    code       VARCHAR(32)  NOT NULL UNIQUE,
    name       VARCHAR(64)  NOT NULL,
    data_scope VARCHAR(8)   NOT NULL DEFAULT 'SELF' CHECK (data_scope IN ('SELF','TEAM','ALL')),
    remark     VARCHAR(255),
    created_by BIGINT       NOT NULL DEFAULT 0,
    updated_by BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    SMALLINT     NOT NULL DEFAULT 0
);

-- 权限点（菜单/按钮/API 三型，树形）
CREATE TABLE sys_permission (
    id         BIGINT PRIMARY KEY,
    parent_id  BIGINT       NOT NULL DEFAULT 0,
    code       VARCHAR(64)  NOT NULL UNIQUE,               -- 如 customer:list / order:confirm
    name       VARCHAR(64)  NOT NULL,
    type       VARCHAR(8)   NOT NULL CHECK (type IN ('MENU','BUTTON','API')),
    path       VARCHAR(128),                                -- 菜单路由 / API 模式
    sort       INT          NOT NULL DEFAULT 0,
    created_by BIGINT       NOT NULL DEFAULT 0,
    updated_by BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    SMALLINT     NOT NULL DEFAULT 0
);

-- 关联表
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL REFERENCES sys_user (id),
    role_id BIGINT NOT NULL REFERENCES sys_role (id),
    PRIMARY KEY (user_id, role_id)
);
CREATE TABLE sys_role_permission (
    role_id       BIGINT NOT NULL REFERENCES sys_role (id),
    permission_id BIGINT NOT NULL REFERENCES sys_permission (id),
    PRIMARY KEY (role_id, permission_id)
);

-- 数据字典（客户等级/行业/来源/工单类型等枚举统一管理）
CREATE TABLE sys_dict (
    id         BIGINT PRIMARY KEY,
    dict_type  VARCHAR(32)  NOT NULL,                       -- customer_level / lead_source / ticket_type ...
    code       VARCHAR(32)  NOT NULL,
    value      VARCHAR(64)  NOT NULL,
    sort       INT          NOT NULL DEFAULT 0,
    created_by BIGINT       NOT NULL DEFAULT 0,
    updated_by BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    SMALLINT     NOT NULL DEFAULT 0,
    UNIQUE (dict_type, code)
);

-- 操作审计日志（SYS-DV-04 接入 @AuditLog AOP；旁路写入不阻断业务）
CREATE TABLE sys_audit_log (
    id         BIGINT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    action     VARCHAR(64)  NOT NULL,                       -- 如 order:confirm / customer:delete
    target_type VARCHAR(32),
    target_id  BIGINT,
    detail     JSONB,
    ip         VARCHAR(64),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_user_time ON sys_audit_log (user_id, created_at DESC);

-- ========================= CRM-C 客户与联系人 =========================

CREATE TABLE crm_customer (
    id            BIGINT PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,                     -- C0102 重名校验
    level         VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',    -- 字典 customer_level
    industry      VARCHAR(32),
    source        VARCHAR(32),
    region        VARCHAR(64),
    address       VARCHAR(255),
    owner_id      BIGINT       NOT NULL REFERENCES sys_user (id),   -- 负责人（数据范围过滤键）
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','COOPERATING','INACTIVE')),
    custom_fields JSONB,                                     -- 需求 §6：自定义字段统一 JSONB
    remark        VARCHAR(500),
    created_by    BIGINT       NOT NULL DEFAULT 0,
    updated_by    BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT     NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_customer_name ON crm_customer (name) WHERE deleted = 0;
CREATE INDEX idx_customer_owner ON crm_customer (owner_id, status);
CREATE INDEX idx_customer_name_trgm ON crm_customer USING gin (name gin_trgm_ops);  -- 名称模糊搜索
CREATE INDEX idx_customer_custom ON crm_customer USING gin (custom_fields);         -- JSONB 条件查询

CREATE TABLE crm_contact (
    id          BIGINT PRIMARY KEY,
    customer_id BIGINT       NOT NULL REFERENCES crm_customer (id),
    name        VARCHAR(64)  NOT NULL,
    position    VARCHAR(64),
    phone       VARCHAR(32),
    email       VARCHAR(128),
    wechat      VARCHAR(64),
    is_primary  SMALLINT     NOT NULL DEFAULT 0,             -- 每客户唯一主联系人（应用侧保证）
    remark      VARCHAR(255),
    created_by  BIGINT       NOT NULL DEFAULT 0,
    updated_by  BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_contact_customer ON crm_contact (customer_id);

-- ========================= CRM-L 线索 =========================

CREATE TABLE crm_lead (
    id                   BIGINT PRIMARY KEY,
    company_name         VARCHAR(128) NOT NULL,
    contact_name         VARCHAR(64)  NOT NULL,
    contact_phone        VARCHAR(32)  NOT NULL,
    source               VARCHAR(32),
    owner_id             BIGINT REFERENCES sys_user (id),    -- NULL=公共池（LEAD-2 领取/分配）
    status               VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING','CLAIMED','ASSIGNED','CONVERTED','INVALID')),
    converted_customer_id BIGINT REFERENCES crm_customer (id),-- LEAD-3 转客户
    converted_at         TIMESTAMP,
    remark               VARCHAR(500),
    created_by           BIGINT       NOT NULL DEFAULT 0,
    updated_by           BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_lead_owner_status ON crm_lead (owner_id, status);
CREATE INDEX idx_lead_phone ON crm_lead (contact_phone);      -- 防重复录入检索

-- ========================= CRM-O 商机 =========================

CREATE TABLE crm_opportunity (
    id            BIGINT PRIMARY KEY,
    customer_id   BIGINT       NOT NULL REFERENCES crm_customer (id),
    name          VARCHAR(128) NOT NULL,
    stage         SMALLINT     NOT NULL DEFAULT 1 CHECK (stage BETWEEN 1 AND 7),  -- 漏斗七阶段
    amount        NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK (amount >= 0),
    expected_date DATE,                                         -- 预计成交日（漏斗加权预测）
    owner_id      BIGINT       NOT NULL REFERENCES sys_user (id),
    status        VARCHAR(8)   NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','WON','LOST')),
    win_reason    VARCHAR(255),
    lose_reason   VARCHAR(255),
    created_by    BIGINT       NOT NULL DEFAULT 0,
    updated_by    BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_opp_owner_stage ON crm_opportunity (owner_id, stage);
CREATE INDEX idx_opp_customer ON crm_opportunity (customer_id);

-- ========================= CRM-F 跟进（SFA） =========================

CREATE TABLE crm_followup (
    id               BIGINT PRIMARY KEY,
    rel_type         VARCHAR(16)  NOT NULL CHECK (rel_type IN ('CUSTOMER','LEAD','OPPORTUNITY')),
    rel_id           BIGINT       NOT NULL,                    -- 三类对象统一跟进流（FUP-1）
    content          VARCHAR(1000) NOT NULL,
    method           VARCHAR(16)  NOT NULL DEFAULT 'PHONE',    -- PHONE/VISIT/WECHAT/EMAIL/OTHER
    next_followup_at TIMESTAMP,                                 -- 下次跟进时间（待办提醒来源）
    status           VARCHAR(8)   NOT NULL DEFAULT 'DONE' CHECK (status IN ('DONE','TODO')),
    owner_id         BIGINT       NOT NULL REFERENCES sys_user (id),
    created_by       BIGINT       NOT NULL DEFAULT 0,
    updated_by       BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_followup_rel ON crm_followup (rel_type, rel_id, created_at DESC);
CREATE INDEX idx_followup_todo ON crm_followup (owner_id, status, next_followup_at);  -- WSP 待办查询

-- ========================= CRM-R 报价/合同/订单/回款 =========================

CREATE TABLE rms_quotation (
    id             BIGINT PRIMARY KEY,
    opportunity_id BIGINT        NOT NULL REFERENCES crm_opportunity (id),
    customer_id    BIGINT        NOT NULL REFERENCES crm_customer (id),
    no             VARCHAR(32)   NOT NULL UNIQUE,
    items          JSONB         NOT NULL,                    -- [{name,spec,qty,price}]
    total_amount   NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    status         VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SENT','ACCEPTED','REJECTED')),
    valid_until    DATE,
    created_by     BIGINT        NOT NULL DEFAULT 0,
    updated_by     BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT      NOT NULL DEFAULT 0
);

CREATE TABLE rms_contract (
    id             BIGINT PRIMARY KEY,
    customer_id    BIGINT        NOT NULL REFERENCES crm_customer (id),
    opportunity_id BIGINT        REFERENCES crm_opportunity (id),
    no             VARCHAR(32)   NOT NULL UNIQUE,
    amount         NUMERIC(18,4) NOT NULL CHECK (amount >= 0),
    sign_date      DATE          NOT NULL,
    start_date     DATE,
    end_date       DATE,
    file_key       VARCHAR(255),                               -- ObjectStorageService 存储 key
    status         VARCHAR(16)   NOT NULL DEFAULT 'EXECUTING' CHECK (status IN ('EXECUTING','FINISHED','TERMINATED')),
    created_by     BIGINT        NOT NULL DEFAULT 0,
    updated_by     BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_contract_customer ON rms_contract (customer_id);

-- 合同执行订单
CREATE TABLE rms_order (
    id          BIGINT PRIMARY KEY,
    contract_id BIGINT        NOT NULL REFERENCES rms_contract (id),
    no          VARCHAR(32)   NOT NULL UNIQUE,
    amount      NUMERIC(18,4) NOT NULL CHECK (amount >= 0),
    status      VARCHAR(16)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','DELIVERED','ACCEPTED','CLOSED')),
    remark      VARCHAR(255),
    created_by  BIGINT        NOT NULL DEFAULT 0,
    updated_by  BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT      NOT NULL DEFAULT 0
);

-- 回款计划与记录（CRM-R4：金额/进度跟踪）
CREATE TABLE rms_payment (
    id          BIGINT PRIMARY KEY,
    contract_id BIGINT        NOT NULL REFERENCES rms_contract (id),
    period      INT           NOT NULL DEFAULT 1,             -- 期次
    plan_date   DATE          NOT NULL,
    plan_amount NUMERIC(18,4) NOT NULL CHECK (plan_amount >= 0),
    actual_date DATE,
    actual_amount NUMERIC(18,4),
    status      VARCHAR(16)   NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED','RECEIVED','OVERDUE')),
    created_by  BIGINT        NOT NULL DEFAULT 0,
    updated_by  BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT      NOT NULL DEFAULT 0,
    UNIQUE (contract_id, period)
);

-- ========================= CRM-S 工单 =========================

CREATE TABLE service_ticket (
    id             BIGINT PRIMARY KEY,
    no             VARCHAR(32)  NOT NULL UNIQUE,
    customer_id    BIGINT       NOT NULL REFERENCES crm_customer (id),
    contact_id     BIGINT       REFERENCES crm_contact (id),
    type           VARCHAR(16)  NOT NULL,                     -- 字典 ticket_type
    priority       VARCHAR(8)   NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
    title          VARCHAR(128) NOT NULL,
    content        VARCHAR(1000) NOT NULL,
    assignee_id    BIGINT       REFERENCES sys_user (id),
    status         VARCHAR(16)  NOT NULL DEFAULT 'OPEN'
                   CHECK (status IN ('OPEN','PROCESSING','RESOLVED','CLOSED')),
    sla_due_at     TIMESTAMP,                                  -- SLA 到期（CRM-S3 超时预警）
    resolved_at    TIMESTAMP,
    satisfaction   SMALLINT CHECK (satisfaction BETWEEN 1 AND 5),   -- CRM-S5 回访满意度
    created_by     BIGINT       NOT NULL DEFAULT 0,
    updated_by     BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_ticket_assignee_status ON service_ticket (assignee_id, status);
CREATE INDEX idx_ticket_sla ON service_ticket (status, sla_due_at);

-- ========================= TB 交易标的 =========================

CREATE TABLE trade_item (
    id          BIGINT PRIMARY KEY,
    code        VARCHAR(32)   NOT NULL UNIQUE,
    name        VARCHAR(128)  NOT NULL,
    category    VARCHAR(32)   NOT NULL,                      -- 字典 item_category
    market      VARCHAR(32),                                  -- 所属市场/交易所
    reference_price NUMERIC(18,4) NOT NULL DEFAULT 0,          -- 参考价格
    risk_level  VARCHAR(8)    NOT NULL DEFAULT 'MEDIUM' CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
    status      VARCHAR(16)   NOT NULL DEFAULT 'DRAFT'
                CHECK (status IN ('DRAFT','LISTED','DELISTED')),   -- TB-1/2 上架/下架
    attributes  JSONB,                                        -- 标的扩展属性
    listed_at   TIMESTAMP,
    delisted_at TIMESTAMP,
    remark      VARCHAR(500),
    created_by  BIGINT        NOT NULL DEFAULT 0,
    updated_by  BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_item_status ON trade_item (status);
CREATE INDEX idx_item_name_trgm ON trade_item USING gin (name gin_trgm_ops);

-- ========================= OD 交易订单（状态机 + 乐观锁） =========================

-- 状态机（OD-1）：
-- PENDING_CONFIRM -> CONFIRMED -> PARTIAL_DEALT -> FULL_DEALT
--                              \-> PARTIAL_CANCELLED -> CANCELLED
-- CONFIRMED/PARTIAL_* 亦可 -> CANCELLED（整单撤销）；终态：FULL_DEALT/CANCELLED
CREATE TABLE trade_order (
    id           BIGINT PRIMARY KEY,
    order_no     VARCHAR(32)   NOT NULL UNIQUE,
    item_id      BIGINT        NOT NULL REFERENCES trade_item (id),
    customer_id  BIGINT        NOT NULL REFERENCES crm_customer (id),
    owner_id     BIGINT        NOT NULL REFERENCES sys_user (id),  -- 经手业务员
    direction    VARCHAR(4)    NOT NULL CHECK (direction IN ('BUY','SELL')),
    quantity     NUMERIC(18,4) NOT NULL CHECK (quantity > 0),
    price        NUMERIC(18,4) NOT NULL CHECK (price >= 0),
    deal_quantity  NUMERIC(18,4) NOT NULL DEFAULT 0,          -- 已成交数量（部分成交累计）
    amount       NUMERIC(18,4) NOT NULL,                     -- quantity*price，服务端计算
    fee_rate     NUMERIC(9,6)  NOT NULL DEFAULT 0,           -- 手续费率（万分比存小数）
    fee_amount   NUMERIC(18,4) NOT NULL DEFAULT 0,           -- 服务端计算，前端只读
    total_amount NUMERIC(18,4) NOT NULL DEFAULT 0,           -- amount + fee_amount
    status       VARCHAR(24)   NOT NULL DEFAULT 'PENDING_CONFIRM'
                 CHECK (status IN ('PENDING_CONFIRM','CONFIRMED','PARTIAL_DEALT',
                                   'FULL_DEALT','PARTIAL_CANCELLED','CANCELLED')),
    version      BIGINT        NOT NULL DEFAULT 0,           -- 乐观锁（并发防重）
    confirmed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancel_reason VARCHAR(255),
    remark       VARCHAR(500),
    created_by   BIGINT        NOT NULL DEFAULT 0,
    updated_by   BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT      NOT NULL DEFAULT 0,
    CHECK (amount = quantity * price)                         -- 防止前端篡改金额（OD-1）
);
CREATE INDEX idx_order_owner_status ON trade_order (owner_id, status);
CREATE INDEX idx_order_customer ON trade_order (customer_id, created_at DESC);
CREATE INDEX idx_order_item ON trade_order (item_id);
CREATE INDEX idx_order_status_time ON trade_order (status, created_at DESC);   -- WSP 动态流

-- 订单状态流转日志（状态机可追溯）
CREATE TABLE trade_order_log (
    id         BIGINT PRIMARY KEY,
    order_id   BIGINT      NOT NULL REFERENCES trade_order (id),
    from_status VARCHAR(24),
    to_status  VARCHAR(24) NOT NULL,
    operator_id BIGINT     NOT NULL,
    reason     VARCHAR(255),
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_orderlog_order ON trade_order_log (order_id, created_at);

-- ========================= RM 汇款与核销（金融级一致性） =========================

-- 汇款登记（RM-1 上传凭证走 ObjectStorageService）
CREATE TABLE trade_remittance (
    id                BIGINT PRIMARY KEY,
    remit_no          VARCHAR(32)   NOT NULL UNIQUE,         -- 银行流水/汇款单号
    customer_id       BIGINT        NOT NULL REFERENCES crm_customer (id),
    owner_id          BIGINT        NOT NULL REFERENCES sys_user (id),
    amount            NUMERIC(18,4) NOT NULL CHECK (amount > 0),
    currency          VARCHAR(8)    NOT NULL DEFAULT 'CNY',
    remitted_at       TIMESTAMP     NOT NULL,                -- 汇款时间
    voucher_key       VARCHAR(255),                          -- 凭证文件 key
    status            VARCHAR(24)   NOT NULL DEFAULT 'PENDING_CONFIRM'
                      CHECK (status IN ('PENDING_CONFIRM','CONFIRMED',
                                        'PARTIALLY_WRITTEN_OFF','WRITTEN_OFF','REJECTED')),
    written_off_amount NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK (written_off_amount >= 0),
    confirmer_id      BIGINT,                                -- 财务确认人（RM-2）
    confirmed_at      TIMESTAMP,
    reject_reason     VARCHAR(255),
    remark            VARCHAR(500),
    created_by        BIGINT        NOT NULL DEFAULT 0,
    updated_by        BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_remit_customer_status ON trade_remittance (customer_id, status);
CREATE INDEX idx_remit_owner ON trade_remittance (owner_id, created_at DESC);

-- 核销明细（RM-3：一笔汇款核销多张订单；B0301 超核校验在服务端）
CREATE TABLE trade_order_settlement (
    id            BIGINT PRIMARY KEY,
    remittance_id BIGINT        NOT NULL REFERENCES trade_remittance (id),
    order_id      BIGINT        NOT NULL REFERENCES trade_order (id),
    amount        NUMERIC(18,4) NOT NULL CHECK (amount > 0),
    operator_id   BIGINT        NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_settle_remittance ON trade_order_settlement (remittance_id);
CREATE INDEX idx_settle_order ON trade_order_settlement (order_id);

-- ========================= CS 通知与待办提醒 =========================

CREATE TABLE notify_message (
    id         BIGINT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,                        -- 接收人（不设 FK，允许离席用户）
    type       VARCHAR(16)  NOT NULL,                        -- FOLLOWUP_DUE/ORDER_EVENT/TICKET_SLA/SYSTEM
    title      VARCHAR(128) NOT NULL,
    content    VARCHAR(500),
    rel_type   VARCHAR(16),
    rel_id     BIGINT,
    read_at    TIMESTAMP,                                    -- NULL=未读
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notify_user_unread ON notify_message (user_id, read_at, created_at DESC);
