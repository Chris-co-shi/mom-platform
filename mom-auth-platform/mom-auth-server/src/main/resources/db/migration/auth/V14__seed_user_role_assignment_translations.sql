-- 用户角色管理页面使用 Auth Owner 动态国际化词条。
INSERT INTO auth_i18n_message_definition
    (id, message_key, description, enabled, version, deleted, created_by, created_at, updated_by, updated_at)
VALUES
    ('9470000000000000001', 'iam.users.assignroles', '打开用户角色分配', true, 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP),
    ('9470000000000000002', 'iam.users.roleassignment', '用户角色分配对话框标题', true, 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP),
    ('9470000000000000003', 'iam.users.roledisabledhint', '提示停用角色必须从关系中移除', true, 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP),
    ('9470000000000000004', 'iam.users.roleselectioncount', '已选择角色数量与上限', true, 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP),
    ('9470000000000000005', 'iam.users.noroles', '角色目录为空提示', true, 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP),
    ('9470000000000000006', 'iam.users.roleassignmenthint', '角色变化在下次登录签发权限快照时生效', true, 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP);

INSERT INTO auth_i18n_translation
    (id, message_id, locale_code, message_text, version, deleted, created_by, created_at, updated_by, updated_at)
VALUES
    ('9480000000000000001', '9470000000000000001', 'zh-CN', '分配角色', 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP),
    ('9480000000000000002', '9470000000000000001', 'en-US', 'Assign roles', 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP),
    ('9480000000000000003', '9470000000000000002', 'zh-CN', '管理用户角色', 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP),
    ('9480000000000000004', '9470000000000000002', 'en-US', 'Manage user roles', 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP),
    ('9480000000000000005', '9470000000000000003', 'zh-CN', '已停用角色不能继续分配；请将其从当前关系中移除。', 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP),
    ('9480000000000000006', '9470000000000000003', 'en-US', 'Disabled roles cannot remain assigned. Remove them from the current selection.', 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP),
    ('9480000000000000007', '9470000000000000004', 'zh-CN', '已选择 {0} / 200 个角色', 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP),
    ('9480000000000000008', '9470000000000000004', 'en-US', '{0} of 200 roles selected', 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP),
    ('9480000000000000009', '9470000000000000005', 'zh-CN', '暂无可分配的角色。', 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP),
    ('9480000000000000010', '9470000000000000005', 'en-US', 'No roles are available to assign.', 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP),
    ('9480000000000000011', '9470000000000000006', 'zh-CN', '角色关系变化不会刷新已签发的登录权限；请让用户重新登录后生效。', 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP),
    ('9480000000000000012', '9470000000000000006', 'en-US', 'Role changes do not update existing token snapshots. Ask the user to sign in again for them to take effect.', 0, false, 'AUTH_MIGRATION', CURRENT_TIMESTAMP, 'AUTH_MIGRATION', CURRENT_TIMESTAMP);
