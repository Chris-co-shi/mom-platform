-- S15-F 最终退出 Phase 01 MDM 技术表；V101 同时覆盖测试资源 V100 的受控重建。
-- 非空技术表拒绝自动删除，避免把潜在环境数据静默丢弃。
DO $$
DECLARE
    candidate TEXT;
    row_count BIGINT;
BEGIN
    FOREACH candidate IN ARRAY ARRAY[
        'technical_data_probe',
        'mom_outbox_event',
        'technical_seata_at_coordinator',
        'undo_log'
    ] LOOP
        IF to_regclass(current_schema() || '.' || candidate) IS NOT NULL THEN
            EXECUTE format('SELECT count(*) FROM %I', candidate) INTO row_count;
            IF row_count > 0 THEN
                RAISE EXCEPTION 'cannot retire Phase 01 technical table %, rows=%', candidate, row_count;
            END IF;
        END IF;
    END LOOP;
END;
$$;

DROP TABLE IF EXISTS technical_seata_at_coordinator;
DROP TABLE IF EXISTS mom_outbox_event;
DROP TABLE IF EXISTS technical_data_probe;
DROP TABLE IF EXISTS undo_log;
