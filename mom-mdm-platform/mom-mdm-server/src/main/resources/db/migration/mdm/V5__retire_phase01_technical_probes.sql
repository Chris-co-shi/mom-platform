-- Phase 01 技术探针退出。历史 V1-V4 保持不可变；本迁移拒绝静默删除任何非空技术表。
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
