-- Location 是通用厂内位置，关联 WarehouseArea 后才是仓储位置；只更新 MDM Owner 页面文案。
UPDATE mdm_i18n_translation
SET message_text = replace(message_text, '库位', '位置'),
    version = version + 1, updated_by = 'MDM_MIGRATION', updated_at = CURRENT_TIMESTAMP
WHERE locale_code = 'zh-CN'
  AND message_id IN (
      '9730000000000001006', '9730000000000001007', '9730000000000001011',
      '9730000000000001017', '9730000000000001058'
  );
