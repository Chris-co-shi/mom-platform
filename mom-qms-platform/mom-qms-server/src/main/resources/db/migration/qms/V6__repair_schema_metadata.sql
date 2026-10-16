-- 只更正历史约束名称；RENAME 保留约束对象、定义、验证状态及已有数据。
-- ALTER TABLE 需要短暂表锁，发布时应避开长事务；失败由 Flyway 本地事务回滚。
ALTER TABLE qms_specification_revision
    RENAME CONSTRAINT uk_qms_specification_revision TO uk_qms_specification_revision_no;
ALTER TABLE qms_i18n_message_definition
    RENAME CONSTRAINT pk_qms_i18n_message TO pk_qms_i18n_message_definition;
ALTER TABLE qms_i18n_message_definition
    RENAME CONSTRAINT uk_qms_i18n_message_key TO uk_qms_i18n_message_definition_key;
ALTER TABLE qms_i18n_message_definition
    RENAME CONSTRAINT ck_qms_i18n_message_key TO ck_qms_i18n_message_definition_key;
ALTER TABLE qms_i18n_message_definition
    RENAME CONSTRAINT ck_qms_i18n_message_version TO ck_qms_i18n_message_definition_version;

-- 保留 V4 对历史质量定义表的只读退役说明，仅补充列语义。
COMMENT ON COLUMN qms_inspection_characteristic.id IS '已退役检验特性的历史技术 ID，现权威位于 MDM';
COMMENT ON COLUMN qms_inspection_characteristic.default_uom_id IS '历史默认计量单位的 MDM ID 快照引用';
COMMENT ON COLUMN qms_inspection_characteristic.version IS '历史检验特性的技术乐观锁版本';
COMMENT ON COLUMN qms_inspection_method.id IS '已退役检验方法的历史技术 ID，现权威位于 MDM';
COMMENT ON COLUMN qms_inspection_method.version IS '历史检验方法的技术乐观锁版本';
COMMENT ON COLUMN qms_quality_specification.id IS '已退役质量标准身份的历史技术 ID';
COMMENT ON COLUMN qms_quality_specification.plant_id IS '历史标准适用的 MDM 工厂 ID 快照引用';
COMMENT ON COLUMN qms_quality_specification.material_id IS '历史标准适用的 MDM 物料 ID 快照引用';
COMMENT ON COLUMN qms_specification_revision.id IS '已退役质量标准业务修订的历史技术 ID';
COMMENT ON COLUMN qms_specification_revision.specification_id IS '所属历史质量标准身份 ID';
COMMENT ON COLUMN qms_specification_revision.status IS '历史标准修订状态：DRAFT、RELEASED 或 OBSOLETE';
COMMENT ON COLUMN qms_specification_item.id IS '已退役质量标准条目的历史技术 ID';
COMMENT ON COLUMN qms_specification_item.revision_id IS '所属历史质量标准业务修订 ID';
COMMENT ON COLUMN qms_specification_item.characteristic_id IS '历史检验特性 ID 快照引用';
COMMENT ON COLUMN qms_specification_item.method_id IS '历史检验方法 ID 快照引用';
COMMENT ON COLUMN qms_specification_item.uom_id IS '历史检验限值对应的 MDM 计量单位 ID 快照引用';
COMMENT ON TABLE qms_i18n_message_definition IS 'QMS 所有的国际化消息定义，保留停用和逻辑删除历史';
COMMENT ON COLUMN qms_i18n_message_definition.id IS 'QMS 消息定义的稳定技术 ID';
COMMENT ON COLUMN qms_i18n_message_definition.version IS '消息定义的技术乐观锁版本';
COMMENT ON TABLE qms_i18n_translation IS 'QMS 消息定义对应的本地化译文及其审计历史';
COMMENT ON COLUMN qms_i18n_translation.id IS 'QMS 译文的稳定技术 ID';
COMMENT ON COLUMN qms_i18n_translation.message_id IS '所属 QMS 消息定义 ID，无物理外键';
COMMENT ON COLUMN qms_i18n_translation.version IS '译文的技术乐观锁版本';
