-- =====================================================================
-- CRM 系统 · V3 种子（SYS-DV-01/02 权限点扩展 + LEAD 线索权限）
-- 补齐 MANAGER/SALES 角色授权（V2 中两角色无任何权限点，
-- 权限点校验上线后会导致业务员全线 403）
-- =====================================================================

-- 新增权限点（lead 段 5xx、system 段 40x）
INSERT INTO sys_permission (id, parent_id, code, name, type, sort, created_by, updated_by) VALUES
(402, 0, 'system:user',       '成员管理',     'API', 41, 0, 0),
(403, 0, 'system:role',       '角色与权限管理','API', 42, 0, 0),
(404, 0, 'system:org',        '组织管理',     'API', 43, 0, 0),
(405, 0, 'system:dict',       '字典管理',     'API', 44, 0, 0),
(501, 0, 'lead:list',         '线索查询',     'API', 50, 0, 0),
(502, 0, 'lead:create',       '线索新增/编辑','API', 51, 0, 0),
(503, 0, 'lead:claim',        '线索领取',     'API', 52, 0, 0),
(504, 0, 'lead:assign',       '线索分配',     'API', 53, 0, 0),
(505, 0, 'lead:convert',      '线索转客户',   'API', 54, 0, 0),
(506, 0, 'lead:invalidate',   '线索作废',     'API', 55, 0, 0);

-- ADMIN 补齐新增权限点（幂等：仅补缺口）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id FROM sys_permission p
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = 1 AND rp.permission_id = p.id);

-- MANAGER（销售经理）：客户全流程 + 线索全流程（含分配）
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(2, 101), (2, 102), (2, 103),
(2, 501), (2, 502), (2, 503), (2, 504), (2, 505), (2, 506);

-- SALES（业务员）：客户查询/新增 + 线索除分配外全流程
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(3, 101), (3, 102),
(3, 501), (3, 502), (3, 503), (3, 505), (3, 506);
