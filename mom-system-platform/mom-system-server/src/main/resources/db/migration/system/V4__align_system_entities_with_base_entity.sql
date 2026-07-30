-- S15-C：System 自有业务 Entity 统一对齐 BaseEntity，并移除 System Mapper XML。
-- V1～V3 保持不可变；本迁移只做向前兼容扩展。
ALTER TABLE system_parameter
    ADD COLUMN deleted boolean NOT NULL DEFAULT false;

ALTER TABLE system_dictionary
    ADD COLUMN deleted boolean NOT NULL DEFAULT false;

ALTER TABLE system_dictionary_item
    ADD COLUMN deleted boolean NOT NULL DEFAULT false;

ALTER TABLE system_i18n_resource
    ADD COLUMN deleted boolean NOT NULL DEFAULT false;

ALTER TABLE system_i18n_message
    ADD COLUMN deleted boolean NOT NULL DEFAULT false;

-- Release 原来使用业务复合主键且没有 BaseEntity 字段。先暂时移除不可变行触发器，
-- 完成兼容回填后恢复；生产路径仍只允许 INSERT/SELECT。
DROP TRIGGER trg_system_i18n_release_immutable ON system_i18n_release;

ALTER TABLE system_i18n_release
    ADD COLUMN id varchar(19),
    ADD COLUMN created_by varchar(128),
    ADD COLUMN created_at timestamptz,
    ADD COLUMN updated_by varchar(128),
    ADD COLUMN updated_at timestamptz,
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN deleted boolean NOT NULL DEFAULT false;

-- 已有历史 Release 使用保留的 8e18 数字段生成迁移技术 ID；新写入继续由 MyBatis-Plus ASSIGN_ID 生成。
WITH numbered AS (
    SELECT ctid,
           row_number() OVER (ORDER BY resource_id, release_version, locale) AS row_no
      FROM system_i18n_release
)
UPDATE system_i18n_release release_row
   SET id = (8000000000000000000::numeric + numbered.row_no)::varchar(19),
       created_by = release_row.published_by,
       created_at = release_row.published_at,
       updated_by = release_row.published_by,
       updated_at = release_row.published_at
  FROM numbered
 WHERE release_row.ctid = numbered.ctid;

ALTER TABLE system_i18n_release
    ALTER COLUMN id SET NOT NULL,
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_by SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE system_i18n_release
    DROP CONSTRAINT pk_system_i18n_release,
    ADD CONSTRAINT pk_system_i18n_release PRIMARY KEY (id),
    ADD CONSTRAINT uk_system_i18n_release_version_locale
        UNIQUE (resource_id, release_version, locale),
    ADD CONSTRAINT ck_system_i18n_release_base_version CHECK (version >= 0);

CREATE TRIGGER trg_system_i18n_release_immutable
BEFORE UPDATE OR DELETE ON system_i18n_release
FOR EACH ROW EXECUTE FUNCTION reject_system_i18n_release_mutation();

COMMENT ON COLUMN system_parameter.deleted IS 'BaseEntity 逻辑删除标识；当前 Parameter 无删除 API，正常业务始终为 false';
COMMENT ON COLUMN system_dictionary.deleted IS 'BaseEntity 逻辑删除标识；当前 Dictionary 无删除 API，正常业务始终为 false';
COMMENT ON COLUMN system_dictionary_item.deleted IS 'BaseEntity 逻辑删除标识；当前 Dictionary Item 无删除 API，正常业务始终为 false';
COMMENT ON COLUMN system_i18n_resource.deleted IS 'BaseEntity 逻辑删除标识；当前 I18n Resource 无删除 API，正常业务始终为 false';
COMMENT ON COLUMN system_i18n_message.deleted IS 'BaseEntity 逻辑删除标识；当前 I18n Message 无删除 API，正常业务始终为 false';
COMMENT ON COLUMN system_i18n_release.id IS 'BaseEntity varchar(19) String 技术主键；新记录由 MyBatis-Plus ASSIGN_ID 生成';
COMMENT ON COLUMN system_i18n_release.created_by IS 'BaseEntity 创建 Actor；与 published_by 分别表达持久化审计和发布审计';
COMMENT ON COLUMN system_i18n_release.created_at IS 'BaseEntity UTC 创建时间；与 published_at 分别表达持久化审计和发布审计';
COMMENT ON COLUMN system_i18n_release.updated_by IS 'BaseEntity 最近修改 Actor；Release 不允许业务更新，初值等于创建 Actor';
COMMENT ON COLUMN system_i18n_release.updated_at IS 'BaseEntity 最近修改时间；Release 不允许业务更新，初值等于创建时间';
COMMENT ON COLUMN system_i18n_release.version IS 'BaseEntity 乐观锁版本；Release 不允许业务更新，正常值保持 0';
COMMENT ON COLUMN system_i18n_release.deleted IS 'BaseEntity 逻辑删除标识；Release 不允许删除，正常值始终为 false';
