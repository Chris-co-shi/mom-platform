-- S15-F 清理 V5 未覆盖的 Phase 01 Outbox/Seata 基础表；V101 保证已到 V100 的环境可顺序升级。
-- 非空技术表拒绝自动删除，避免把潜在环境数据静默丢弃。
DO $$
DECLARE
    candidate TEXT;
    row_count BIGINT;
BEGIN
    FOREACH candidate IN ARRAY ARRAY['mom_outbox_event', 'undo_log'] LOOP
        IF to_regclass(current_schema() || '.' || candidate) IS NOT NULL THEN
            EXECUTE format('SELECT count(*) FROM %I', candidate) INTO row_count;
            IF row_count > 0 THEN
                RAISE EXCEPTION 'cannot retire Phase 01 technical table %, rows=%', candidate, row_count;
            END IF;
        END IF;
    END LOOP;
END;
$$;

DROP TABLE IF EXISTS mom_outbox_event;
DROP TABLE IF EXISTS undo_log;
