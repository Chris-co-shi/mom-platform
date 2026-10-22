ALTER TABLE mdm_quality_specification
    DROP CONSTRAINT ck_mdm_quality_specification_usage;
ALTER TABLE mdm_quality_specification
    ADD CONSTRAINT ck_mdm_quality_specification_usage
    CHECK (inspection_usage IN ('INCOMING', 'FINISHED_GOODS'));

-- 旧修订的发布时范围版本无法从可变身份可靠反推，保持 NULL 待历史核对。
ALTER TABLE mdm_specification_revision
    ADD COLUMN plant_version bigint,
    ADD COLUMN material_revision_no integer,
    ADD COLUMN material_version bigint;
ALTER TABLE mdm_specification_revision
    ADD CONSTRAINT ck_mdm_specification_revision_scope_version CHECK (
        (plant_version IS NULL AND material_revision_no IS NULL AND material_version IS NULL) OR
        (plant_version IS NOT NULL AND material_revision_no IS NOT NULL AND material_version IS NOT NULL
         AND plant_version >= 0 AND material_revision_no >= 1 AND material_version >= 0)
    );

COMMENT ON COLUMN mdm_specification_revision.plant_version IS '该修订发布时的工厂技术版本；旧修订未知时为空';
COMMENT ON COLUMN mdm_specification_revision.material_revision_no IS '该修订发布时的物料业务修订号；旧修订未知时为空';
COMMENT ON COLUMN mdm_specification_revision.material_version IS '该修订发布时的物料技术版本；旧修订未知时为空';
