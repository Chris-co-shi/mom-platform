COMMENT ON TABLE mdm_i18n_message_definition IS 'MDM 自有动态国际化消息定义；稳定身份为 namespace + message_key';
COMMENT ON COLUMN mdm_i18n_message_definition.id IS '应用侧 ASSIGN_ID 生成的 varchar(19) 技术主键';
COMMENT ON COLUMN mdm_i18n_message_definition.version IS 'MyBatis-Plus 乐观锁版本';

COMMENT ON TABLE mdm_i18n_translation IS 'MDM 自有动态国际化译文；locale_code 为平台 Locale 的逻辑引用';
COMMENT ON COLUMN mdm_i18n_translation.id IS '应用侧 ASSIGN_ID 生成的 varchar(19) 技术主键';
COMMENT ON COLUMN mdm_i18n_translation.message_id IS '同 Schema mdm_i18n_message_definition 技术主键引用，无跨 Schema 外键';
COMMENT ON COLUMN mdm_i18n_translation.version IS 'MyBatis-Plus 乐观锁版本';
