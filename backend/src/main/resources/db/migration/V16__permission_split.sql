-- ==============================================================
-- V16 权限拆分：将"维护性操作"从"查询权限"中解耦，上收至经理/管理员
-- 影响范围：stat 重建、合同签署、工作流任务中心空菜单
-- ==============================================================

BEGIN;

-- ---------- 新增三个权限码（ID 起自 1301，避开既有段 101-1202）----------
INSERT INTO sys_permission (id, parent_id, code, name, type, path, sort, created_by, updated_by, created_at, updated_at)
VALUES
  (1301, 0, 'stat:manage',   '统计重建/重算（维护）', 'API', 131, 0, 0, 0, NOW(), NOW()),
  (1302, 0, 'contract:create', '合同草稿创建/编辑',    'API', 111, 0, 0, 0, NOW(), NOW()),
  (1303, 0, 'contract:sign',  '合同签署/履行/终止',     'API', 112, 0, 0, 0, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ---------- 授权：ADMIN（role_id=1）全量 ----------
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
  (1, 1301), (1, 1302), (1, 1303)
ON CONFLICT DO NOTHING;

-- ---------- 授权：MANAGER（role_id=2）----------
-- 经理原有 stat:report / contract:manage / contract:list 保持不变；新增维护与签署权限
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
  (2, 1301),          -- 统计重建（经理可触发）
  (2, 1302), (2, 1303) -- 合同草稿 + 签署
ON CONFLICT DO NOTHING;

-- ---------- SALES（role_id=3）：精简权限 ----------
-- ① 移除 wf:task:list（销售员审批中心恒空，R1）
DELETE FROM sys_role_permission WHERE role_id = 3 AND permission_id = 1201;

-- ② 移除 contract:manage（销售员不再拥有"全流程签署/终止"权限，R3）
DELETE FROM sys_role_permission WHERE role_id = 3 AND permission_id = 1102;

-- ③ 给销售员下放 contract:create（可建/编辑 UNSIGNED 草稿）
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (3, 1302)
ON CONFLICT DO NOTHING;

-- 保留给 SALES 的：
--   stat:report (906)    — 查报表（只读）
--   contract:list (1101) — 查合同
--   wf:manage (1202) 仍未授予销售员，不受影响

COMMIT;
