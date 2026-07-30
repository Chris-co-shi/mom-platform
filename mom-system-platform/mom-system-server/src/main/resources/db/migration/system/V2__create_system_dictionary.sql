CREATE TABLE system_dictionary (
    id varchar(19) PRIMARY KEY,
    dictionary_code varchar(128) NOT NULL,
    dictionary_name varchar(200) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    description varchar(1000),
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uk_system_dictionary_code UNIQUE (dictionary_code),
    CONSTRAINT ck_system_dictionary_code CHECK (
        dictionary_code ~ '^[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)+$'
        AND length(dictionary_code) >= 3
    ),
    CONSTRAINT ck_system_dictionary_name CHECK (length(btrim(dictionary_name)) > 0),
    CONSTRAINT ck_system_dictionary_version CHECK (version >= 0)
);

CREATE TABLE system_dictionary_item (
    id varchar(19) PRIMARY KEY,
    dictionary_id varchar(19) NOT NULL,
    item_code varchar(64) NOT NULL,
    item_label varchar(200) NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    description varchar(1000),
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_system_dictionary_item_dictionary
        FOREIGN KEY (dictionary_id) REFERENCES system_dictionary (id) ON DELETE RESTRICT,
    CONSTRAINT uk_system_dictionary_item_code UNIQUE (dictionary_id, item_code),
    CONSTRAINT ck_system_dictionary_item_code CHECK (item_code ~ '^[a-z][a-z0-9_-]{0,63}$'),
    CONSTRAINT ck_system_dictionary_item_label CHECK (length(btrim(item_label)) > 0),
    CONSTRAINT ck_system_dictionary_item_sort_order CHECK (sort_order BETWEEN 0 AND 1000000),
    CONSTRAINT ck_system_dictionary_item_version CHECK (version >= 0)
);

CREATE INDEX ix_system_dictionary_item_active
    ON system_dictionary_item (dictionary_id, enabled, sort_order, item_code);

COMMENT ON TABLE system_dictionary IS 'System 非权威、低频、低复杂度通用字典；禁止复制 IAM、MDM、WMS、EAM 权威对象或业务状态机';
COMMENT ON COLUMN system_dictionary.id IS 'System 内部 varchar(19) String 技术主键，不作为跨服务 Reference';
COMMENT ON COLUMN system_dictionary.dictionary_code IS '全局唯一小写点分段稳定 Reference，创建后不可 Rename';
COMMENT ON COLUMN system_dictionary.dictionary_name IS '单一 fallback 展示名称，不是业务 Reference 或多语言资源';
COMMENT ON COLUMN system_dictionary.enabled IS '禁用时 Active List 为空，但不级联修改 Item 状态';
COMMENT ON COLUMN system_dictionary.version IS 'MyBatis-Plus 乐观锁版本，更新与启停必须携带';
COMMENT ON COLUMN system_dictionary.description IS '可选用途说明；不得承载 Metadata JSON 或任意属性';
COMMENT ON COLUMN system_dictionary.created_by IS '由认证上下文和统一审计填充的创建 Actor';
COMMENT ON COLUMN system_dictionary.created_at IS 'UTC 创建时间点';
COMMENT ON COLUMN system_dictionary.updated_by IS '由认证上下文和统一审计填充的最近修改 Actor';
COMMENT ON COLUMN system_dictionary.updated_at IS 'UTC 最近修改时间点';

COMMENT ON TABLE system_dictionary_item IS '受限通用字典条目；只承载稳定 Code、单一 fallback Label、排序、启停、版本和审计';
COMMENT ON COLUMN system_dictionary_item.id IS 'System 内部 varchar(19) String 技术主键，不进入 Consumer 契约';
COMMENT ON COLUMN system_dictionary_item.dictionary_id IS '同 mom_system Schema 字典 FK；ON DELETE RESTRICT，禁止级联删除';
COMMENT ON COLUMN system_dictionary_item.item_code IS '字典内唯一小写稳定 Reference，创建后不可 Rename';
COMMENT ON COLUMN system_dictionary_item.item_label IS '单一 fallback 展示 Label；调用方不得持久化 Label 作为业务语义';
COMMENT ON COLUMN system_dictionary_item.sort_order IS 'Active List 固定升序排序值；相同值按 itemCode、id 排序';
COMMENT ON COLUMN system_dictionary_item.enabled IS '禁用后不进入 Active List，但兼容单项读取仍返回';
COMMENT ON COLUMN system_dictionary_item.version IS 'MyBatis-Plus 乐观锁版本，更新与启停必须携带';
COMMENT ON COLUMN system_dictionary_item.description IS '可选用途说明；不得承载 Tree、Alias、Metadata 或 Locale';
COMMENT ON COLUMN system_dictionary_item.created_by IS '由认证上下文和统一审计填充的创建 Actor';
COMMENT ON COLUMN system_dictionary_item.created_at IS 'UTC 创建时间点';
COMMENT ON COLUMN system_dictionary_item.updated_by IS '由认证上下文和统一审计填充的最近修改 Actor';
COMMENT ON COLUMN system_dictionary_item.updated_at IS 'UTC 最近修改时间点';
