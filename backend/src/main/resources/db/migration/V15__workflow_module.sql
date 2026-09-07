-- =====================================================================
-- CRM 系统 · V15 批次7（F4 工作流自动化：Flowable BPMN 引擎）
--   Flowable 8.0.0 ACT_ 引擎表由引擎自管（database-schema-update=true），
--   本迁移只补 CRM 侧权限点与部署说明：
--   1) 权限点 1201~1202 + 三角色授权
--   2) BPMN 流程定义 quote-approval.bpmn20.xml 随应用启动自动部署
--      （classpath:/processes/，报价单审批：提交→MANAGER/ADMIN 审批→网关回写）
-- =====================================================================

INSERT INTO sys_permission (id, parent_id, code, name, type, sort, created_by, updated_by) VALUES
(1201, 0, 'wf:task:list',  '工作流任务中心', 'API', 120, 0, 0),
(1202, 0, 'wf:manage',      '工作流任务处理', 'API', 121, 0, 0);

-- ADMIN 全量
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 1201 AND 1202;

-- MANAGER(2)：查看 + 处理（报价审批任务候选组）
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(2, 1201), (2, 1202);

-- SALES(3)：查看（本人被指派的任务可见可办）
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(3, 1201);
