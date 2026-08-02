COMMENT ON COLUMN system_i18n_release.version IS
    'V4 历史兼容列；不可变 Release 不使用乐观更新，值保持非负默认值 0';
COMMENT ON COLUMN system_i18n_release.deleted IS
    'V4 历史兼容列；不可变 Release 禁止删除，不作为逻辑删除能力使用且值保持 false';
