-- =====================================================================
-- CRM 系统 · V2 种子数据（INF-DV-03 前置）
-- admin 初始密码：admin123（bcrypt；生产环境首次登录必须改密）
-- =====================================================================

-- 根组织
INSERT INTO sys_org (id, parent_id, name, leader_id, sort, created_by, updated_by)
VALUES (1, 0, '总部', NULL, 0, 0, 0);

-- 初始角色（数据范围三级）
INSERT INTO sys_role (id, code, name, data_scope, remark, created_by, updated_by) VALUES
(1, 'ADMIN',  '系统管理员', 'ALL',  '全量数据与系统管理', 0, 0),
(2, 'MANAGER','销售经理',   'TEAM', '本团队数据',         0, 0),
(3, 'SALES',  '业务员',     'SELF', '本人数据',           0, 0);

-- 基础权限点（SYS-DV-01 逐点接入后生效）
INSERT INTO sys_permission (id, parent_id, code, name, type, sort, created_by, updated_by) VALUES
(101, 0, 'customer:list',    '客户查询',   'API',    10, 0, 0),
(102, 0, 'customer:create',  '客户新增',   'API',    11, 0, 0),
(103, 0, 'customer:transfer','客户移交',   'API',    12, 0, 0),
(201, 0, 'order:confirm',    '订单确认',   'API',    20, 0, 0),
(202, 0, 'order:cancel',     '订单撤销',   'API',    21, 0, 0),
(301, 0, 'remit:confirm',    '汇款到账确认','API',   30, 0, 0),
(302, 0, 'remit:writeoff',   '汇款核销',   'API',    31, 0, 0),
(401, 0, 'system:manage',    '系统管理',   'MENU',   40, 0, 0);

-- ADMIN 拥有全部基础权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;

-- admin 账号（挂在根组织，绑 ADMIN 角色）
INSERT INTO sys_user (id, org_id, username, password, real_name, status, created_by, updated_by)
VALUES (1, 1, 'admin', '$2a$10$oUY1UNEsFsmrxxZj7Nm2nuV5Y70jVuPGzHmeQBCv8UURfxQv.w1..', '系统管理员', 'ACTIVE', 0, 0);

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 基础字典
INSERT INTO sys_dict (id, dict_type, code, value, sort, created_by, updated_by) VALUES
(1001, 'customer_level', 'VIP',    'VIP 客户',   1, 0, 0),
(1002, 'customer_level', 'IMPORTANT','重要客户', 2, 0, 0),
(1003, 'customer_level', 'NORMAL', '普通客户',   3, 0, 0),
(1101, 'lead_source',    'WEBSITE','官网咨询',   1, 0, 0),
(1102, 'lead_source',    'REFERRAL','客户转介绍',2, 0, 0),
(1103, 'lead_source',    'EXHIBITION','展会获客', 3, 0, 0),
(1104, 'lead_source',    'OTHER',  '其他渠道',   9, 0, 0),
(1201, 'ticket_type',    'CONSULT',  '售前咨询', 1, 0, 0),
(1202, 'ticket_type',    'AFTER_SALE','售后支持', 2, 0, 0),
(1203, 'ticket_type',    'COMPLAINT','投诉建议', 3, 0, 0);
