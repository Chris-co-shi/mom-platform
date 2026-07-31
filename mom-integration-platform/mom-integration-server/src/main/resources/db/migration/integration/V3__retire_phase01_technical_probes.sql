-- Phase 01 Integration 消息与 Seata 技术探针退出；历史 V1-V2 保持不可变。
DO $$
DECLARE
    candidate TEXT;
    row_count BIGINT;
BEGIN
    FOREACH candidate IN ARRAY ARRAY[
        'technical_message_receipt',
        'mom_inbox_event',
        'technical_seata_at_participant',
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

DROP TABLE IF EXISTS technical_seata_at_participant;
DROP TABLE IF EXISTS technical_message_receipt;
DROP TABLE IF EXISTS mom_inbox_event;
DROP TABLE IF EXISTS undo_log;
