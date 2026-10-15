-- 存量 Material 没有用途和批次语义；保留 UNSPECIFIED 供治理识别，禁止将其误判为 NONE。
ALTER TABLE mdm_material_category
    ADD COLUMN default_batch_tracking_policy varchar(16),
    ADD CONSTRAINT ck_mdm_material_category_batch_policy
        CHECK (default_batch_tracking_policy IS NULL OR default_batch_tracking_policy IN ('NONE', 'REQUIRED'));

ALTER TABLE mdm_material
    ADD COLUMN material_type varchar(24) NOT NULL DEFAULT 'UNSPECIFIED',
    ADD COLUMN batch_tracking_policy varchar(16) NOT NULL DEFAULT 'UNSPECIFIED',
    ADD COLUMN revision_no integer NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_mdm_material_type
        CHECK (material_type IN ('UNSPECIFIED', 'RAW_MATERIAL', 'SEMI_FINISHED', 'FINISHED_PRODUCT')),
    ADD CONSTRAINT ck_mdm_material_batch_policy
        CHECK (batch_tracking_policy IN ('UNSPECIFIED', 'NONE', 'REQUIRED')),
    ADD CONSTRAINT ck_mdm_material_revision_positive CHECK (revision_no >= 1);

-- 历史回填只发生于本 Migration；新 SQL 写入必须显式提供物料用途与批次策略。
ALTER TABLE mdm_material ALTER COLUMN material_type DROP DEFAULT;
ALTER TABLE mdm_material ALTER COLUMN batch_tracking_policy DROP DEFAULT;

COMMENT ON COLUMN mdm_material_category.default_batch_tracking_policy IS '可空创建建议；Material 创建时复制最终策略，分类变化不回写';
COMMENT ON COLUMN mdm_material.material_type IS '物料用途；UNSPECIFIED 只表示迁移前存量待治理记录';
COMMENT ON COLUMN mdm_material.batch_tracking_policy IS '物料独立批次追踪策略；不存储实际 Lot';
COMMENT ON COLUMN mdm_material.revision_no IS '业务修订号，从 1 开始；与技术乐观锁 version 分离';
COMMENT ON COLUMN mdm_material.base_uom_id IS '创建后不可经普通业务接口修改的基础计量单位 ID';
