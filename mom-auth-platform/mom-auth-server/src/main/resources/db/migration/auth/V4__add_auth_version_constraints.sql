-- 为已发布的 Mini Auth 核心表补齐乐观锁版本非负约束。
-- 不修改 V1 历史 Migration，约束名称遵循 ck_<table>_<semantic> 规范。

ALTER TABLE auth_user
    ADD CONSTRAINT ck_auth_user_version_non_negative CHECK (version >= 0);

ALTER TABLE auth_role
    ADD CONSTRAINT ck_auth_role_version_non_negative CHECK (version >= 0);

ALTER TABLE auth_permission
    ADD CONSTRAINT ck_auth_permission_version_non_negative CHECK (version >= 0);
