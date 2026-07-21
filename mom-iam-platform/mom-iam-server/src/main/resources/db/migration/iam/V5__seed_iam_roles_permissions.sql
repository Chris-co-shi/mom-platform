-- P1.5 S02 环境无关基础目录；不创建默认用户、默认密码或环境相关 Redirect URI。
INSERT INTO iam_role (
    id, code, name, applicable_user_type, status, built_in, description,
    created_at, created_by, updated_at, updated_by, version, deleted
) VALUES
('1000000000000000001', 'PLATFORM_ADMIN', '平台管理员', 'INTERNAL', 'ENABLED', true,
 '拥有 S02 初始化的全部 IAM Permission；至少一个有效实例约束由 S07 实现', now(), 'mom-iam-flyway', now(), 'mom-iam-flyway', 0, false),
('1000000000000000002', 'IAM_ADMIN', 'IAM 管理员', 'INTERNAL', 'ENABLED', true,
 '管理用户、角色、Factory Scope、Mobile Access 和 Session，不动态创建 Permission', now(), 'mom-iam-flyway', now(), 'mom-iam-flyway', 0, false),
('1000000000000000003', 'SECURITY_AUDITOR', '安全审计员', 'INTERNAL', 'ENABLED', true,
 '只读查看用户、角色、Permission、Session、安全审计和 Client', now(), 'mom-iam-flyway', now(), 'mom-iam-flyway', 0, false);

WITH permission_seed(code, name, risk_level, description) AS (
    VALUES
        ('iam:user:read', '查看用户', 'LOW', '读取用户摘要和状态，不返回密码摘要'),
        ('iam:user:create', '创建用户', 'HIGH', '创建内部、供应商或客户账号'),
        ('iam:user:update', '修改用户', 'MEDIUM', '修改展示信息和受控账号属性'),
        ('iam:user:enable', '启用用户', 'HIGH', '启用已禁用账号'),
        ('iam:user:disable', '禁用用户', 'HIGH', '禁用账号并为后续 Session 撤销提供权限依据'),
        ('iam:user:delete', '删除用户', 'HIGH', '逻辑删除用户并保留历史引用'),
        ('iam:user:unlock', '解锁用户', 'HIGH', '清除临时账号锁定'),
        ('iam:user:password-reset', '重置密码', 'HIGH', '发起受控密码重置并要求首次修改'),
        ('iam:user:party-rebind', '重新绑定外部主体', 'HIGH', '高风险重新绑定供应商或客户主体'),
        ('iam:user:role-assign', '分配用户角色', 'HIGH', '为用户分配同类型角色'),
        ('iam:user:role-unassign', '移除用户角色', 'HIGH', '移除用户角色分配'),
        ('iam:user:factory-scope-assign', '分配工厂范围', 'HIGH', '为用户分配 Factory Scope'),
        ('iam:user:factory-scope-remove', '移除工厂范围', 'HIGH', '移除用户 Factory Scope'),
        ('iam:user:mobile-access-manage', '管理移动访问', 'HIGH', '管理 INTERNAL 用户的 MOM Mobile Access'),
        ('iam:role:read', '查看角色', 'LOW', '读取角色摘要和 Permission 关系'),
        ('iam:role:create', '创建角色', 'MEDIUM', '创建自定义角色'),
        ('iam:role:update', '修改角色', 'MEDIUM', '修改自定义角色名称和说明'),
        ('iam:role:enable', '启用角色', 'HIGH', '启用角色'),
        ('iam:role:disable', '禁用角色', 'HIGH', '禁用角色并影响后续授权计算'),
        ('iam:role:permission-manage', '管理角色权限', 'HIGH', '维护角色与系统 Permission 关系'),
        ('iam:permission:read', '查看权限目录', 'LOW', '只读查看系统 Permission 目录'),
        ('iam:session:read', '查看会话', 'LOW', '读取用户授权 Session 摘要'),
        ('iam:session:revoke', '撤销会话', 'HIGH', '撤销单个用户授权 Session'),
        ('iam:session:revoke-all', '撤销全部会话', 'HIGH', '撤销指定用户全部 Session'),
        ('iam:audit:read', '查看安全审计', 'LOW', '读取 IAM 安全审计事件'),
        ('iam:client:read', '查看客户端', 'LOW', '读取 Client Policy 和 Registered Client 摘要'),
        ('iam:client:enable', '启用客户端', 'HIGH', '启用 MOM Client Policy 并触发安全检查'),
        ('iam:client:disable', '禁用客户端', 'HIGH', '禁用 Client 并触发相关 Session 撤销')
), numbered AS (
    SELECT '11' || lpad(row_number() OVER (ORDER BY code)::text, 17, '0') AS id, *
      FROM permission_seed
)
INSERT INTO iam_permission (
    id, code, name, domain_code, resource_code, action_code, risk_level, status,
    description, built_in, created_at, created_by, updated_at, updated_by, version, deleted
)
SELECT id, code, name, split_part(code, ':', 1), split_part(code, ':', 2),
       split_part(code, ':', 3), risk_level, 'ENABLED', description, true,
       now(), 'mom-iam-flyway', now(), 'mom-iam-flyway', 0, false
  FROM numbered;
