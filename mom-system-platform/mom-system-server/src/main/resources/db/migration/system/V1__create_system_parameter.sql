-- Schema 由 Flyway create-schemas/default-schema 以 mom_system 独立管理。
CREATE TABLE system_parameter (
    id varchar(19) PRIMARY KEY,
    scope_type varchar(20) NOT NULL,
    scope_code varchar(64) NOT NULL,
    parameter_key varchar(128) NOT NULL,
    value_type varchar(20) NOT NULL,
    parameter_value varchar(16384) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    description varchar(1000),
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uk_system_parameter_scope_key UNIQUE (scope_type, scope_code, parameter_key),
    CONSTRAINT ck_system_parameter_scope_type CHECK (scope_type IN ('GLOBAL', 'APPLICATION')),
    CONSTRAINT ck_system_parameter_scope_code CHECK (
        (scope_type = 'GLOBAL' AND scope_code = '')
        OR (scope_type = 'APPLICATION' AND scope_code ~ '^[a-z][a-z0-9-]{1,63}$')
    ),
    CONSTRAINT ck_system_parameter_key CHECK (
        parameter_key ~ '^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$'
    ),
    CONSTRAINT ck_system_parameter_value_type CHECK (
        value_type IN ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'JSON')
    ),
    CONSTRAINT ck_system_parameter_value_nonempty CHECK (length(parameter_value) > 0),
    CONSTRAINT ck_system_parameter_version CHECK (version >= 0)
);

CREATE INDEX ix_system_parameter_resolution
    ON system_parameter (parameter_key, enabled, scope_type, scope_code);

COMMENT ON TABLE system_parameter IS 'System Platform 类型化非敏感参数唯一写入权威；禁止保存 Secret、Credential、IAM 配置或权限事实';
COMMENT ON COLUMN system_parameter.id IS '应用侧 ASSIGN_ID 生成的 varchar(19) String 技术主键';
COMMENT ON COLUMN system_parameter.scope_type IS '仅允许 GLOBAL 或 APPLICATION';
COMMENT ON COLUMN system_parameter.scope_code IS 'GLOBAL 使用规范空字符串；APPLICATION 使用独立小写 kebab-case applicationCode，不等同 IAM clientId';
COMMENT ON COLUMN system_parameter.parameter_key IS '规范小写分段键；应用层拒绝明显 Secret/Credential 词段';
COMMENT ON COLUMN system_parameter.value_type IS '仅允许 STRING、INTEGER、DECIMAL、BOOLEAN、JSON';
COMMENT ON COLUMN system_parameter.parameter_value IS '经类型校验后的非空规范字符串；不得保存 Secret、脚本或表达式';
COMMENT ON COLUMN system_parameter.enabled IS '启停状态；禁用 APPLICATION 时有效值解析回退 GLOBAL';
COMMENT ON COLUMN system_parameter.version IS 'MyBatis-Plus 乐观锁版本，更新和启停都必须携带';
COMMENT ON COLUMN system_parameter.description IS '可选非敏感用途说明';
COMMENT ON COLUMN system_parameter.created_by IS '由认证上下文和统一审计填充的创建 Actor';
COMMENT ON COLUMN system_parameter.created_at IS 'UTC 创建时间点';
COMMENT ON COLUMN system_parameter.updated_by IS '由认证上下文和统一审计填充的最近修改 Actor';
COMMENT ON COLUMN system_parameter.updated_at IS 'UTC 最近修改时间点';
