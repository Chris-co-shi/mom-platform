-- Routing 是可在同一 Plant 的多个产品版本中复用的静态工艺顺序；不持有排程或执行事实。
CREATE TABLE mdm_routing (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    plant_id varchar(19) NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_mdm_routing PRIMARY KEY (id),
    CONSTRAINT uk_mdm_routing_code UNIQUE (code)
);

-- 查询模式：工厂内按 code/id 稳定分页；预期每工厂数百到数千条路线。
CREATE INDEX ix_mdm_routing_plant_code ON mdm_routing (plant_id, code, id);

CREATE TABLE mdm_routing_revision (
    id varchar(19) NOT NULL,
    routing_id varchar(19) NOT NULL,
    revision_no integer NOT NULL,
    status varchar(16) NOT NULL,
    lock_version bigint NOT NULL DEFAULT 0,
    released_at timestamptz,
    obsolete_at timestamptz,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_mdm_routing_revision PRIMARY KEY (id),
    CONSTRAINT uk_mdm_routing_revision_no UNIQUE (routing_id, revision_no),
    CONSTRAINT ck_mdm_routing_revision_positive CHECK (revision_no >= 1),
    CONSTRAINT ck_mdm_routing_revision_lock CHECK (lock_version >= 0),
    CONSTRAINT ck_mdm_routing_revision_status CHECK (status IN ('DRAFT','RELEASED','OBSOLETE')),
    CONSTRAINT ck_mdm_routing_revision_times CHECK (
        (status = 'DRAFT' AND released_at IS NULL AND obsolete_at IS NULL) OR
        (status = 'RELEASED' AND released_at IS NOT NULL AND obsolete_at IS NULL) OR
        (status = 'OBSOLETE' AND released_at IS NOT NULL AND obsolete_at IS NOT NULL)
    )
);

CREATE TABLE mdm_routing_step (
    id varchar(19) NOT NULL,
    revision_id varchar(19) NOT NULL,
    sequence_no integer NOT NULL,
    operation_code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    resource_type varchar(32) NOT NULL,
    resource_id varchar(19) NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    CONSTRAINT pk_mdm_routing_step PRIMARY KEY (id),
    CONSTRAINT uk_mdm_routing_step_sequence UNIQUE (revision_id, sequence_no),
    CONSTRAINT ck_mdm_routing_step_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_mdm_routing_step_resource CHECK (resource_type IN ('PRODUCTION_LINE','WORKSTATION'))
);

COMMENT ON TABLE mdm_routing IS '静态工艺路线身份；Plant 是资源一致性边界，不代表 MES 排程';
COMMENT ON TABLE mdm_routing_revision IS '工艺路线业务修订；lock_version 是独立技术锁';
COMMENT ON TABLE mdm_routing_step IS '有序工序及 MDM 资源目录引用；发布后不可改写';
