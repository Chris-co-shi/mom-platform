-- Mini Auth V1 平台管理员初始化数据。
-- 仅用于开发/初始化环境：默认账号 admin，默认密码 Admin@123456。
-- password_hash 使用 Spring Security DelegatingPasswordEncoder 可识别的 {bcrypt} 格式。
-- 正式环境不得继续使用该默认密码；后续具备密码修改能力后应立即修改。

INSERT INTO auth_user (
    id,
    username,
    password_hash,
    display_name,
    enabled,
    created_at,
    created_by,
    updated_at,
    updated_by,
    version,
    deleted
) VALUES (
    '1000000000000000001',
    'admin',
    '{bcrypt}$2y$12$sYX/yTESd4KDo9SO/EC4Resz.hgaJTq2JfT/wa0DdTbzZRLS4u5Ba',
    '平台管理员',
    true,
    CURRENT_TIMESTAMP,
    'SYSTEM_BOOTSTRAP',
    CURRENT_TIMESTAMP,
    'SYSTEM_BOOTSTRAP',
    0,
    false
);

INSERT INTO auth_role (
    id,
    code,
    name,
    description,
    enabled,
    created_at,
    created_by,
    updated_at,
    updated_by,
    version,
    deleted
) VALUES (
    '1000000000000000002',
    'PLATFORM_ADMIN',
    '平台管理员',
    'Mini Auth V1 内置平台管理员角色；具体 Permission 由后续 Flyway 脚本显式授予，不通过角色编码绕过权限判断',
    true,
    CURRENT_TIMESTAMP,
    'SYSTEM_BOOTSTRAP',
    CURRENT_TIMESTAMP,
    'SYSTEM_BOOTSTRAP',
    0,
    false
);

INSERT INTO auth_user_role (
    id,
    user_id,
    role_id,
    created_at,
    created_by
) VALUES (
    '1000000000000000003',
    '1000000000000000001',
    '1000000000000000002',
    CURRENT_TIMESTAMP,
    'SYSTEM_BOOTSTRAP'
);
