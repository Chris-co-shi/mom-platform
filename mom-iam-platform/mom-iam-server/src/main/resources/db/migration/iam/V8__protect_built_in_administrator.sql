-- P1.5 前置任务：为内置管理员增加稳定身份标志，不在迁移中创建账号或写入密码摘要。
ALTER TABLE iam_user
    ADD COLUMN system_account boolean NOT NULL DEFAULT false;

ALTER TABLE iam_user
    ADD CONSTRAINT ck_iam_user_system_account_identity CHECK (
        system_account = false
        OR (username = 'admin' AND user_type = 'INTERNAL')
    );

COMMENT ON COLUMN iam_user.system_account IS
    '是否为 IAM 内置系统账号；仅 Bootstrap 插入 admin 时可设为 true，普通管理接口不得提升该标志';

-- 数据库兜底保护固定系统身份。账号状态、锁定状态、密码和角色仍由受控业务事务管理。
CREATE OR REPLACE FUNCTION protect_iam_system_account_identity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.system_account THEN
        RAISE EXCEPTION 'IAM system account cannot be physically deleted'
            USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF OLD.system_account
           AND (
               NEW.username IS DISTINCT FROM OLD.username
               OR NEW.user_type IS DISTINCT FROM OLD.user_type
               OR NEW.system_account IS DISTINCT FROM OLD.system_account
           ) THEN
            RAISE EXCEPTION 'IAM system account identity is immutable'
                USING ERRCODE = '23514';
        END IF;

        IF NOT OLD.system_account AND NEW.system_account THEN
            RAISE EXCEPTION 'Ordinary IAM account cannot become a system account'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_iam_user_protect_system_account_identity
    BEFORE UPDATE OR DELETE ON iam_user
    FOR EACH ROW
    EXECUTE FUNCTION protect_iam_system_account_identity();
