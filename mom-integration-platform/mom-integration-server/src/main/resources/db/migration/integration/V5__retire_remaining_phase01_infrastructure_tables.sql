-- S15-F：Outbox/Seata 运行时依赖退出后，清理 V4 保留但已无调用方的 Inbox 与 Undo 表。
-- 非空表拒绝自动删除，避免静默丢失环境数据。
DO $$
DECLARE
    candidate TEXT;
    row_count BIGINT;
BEGIN
    FOREACH candidate IN ARRAY ARRAY['mom_inbox_event', 'undo_log'] LOOP
        IF to_regclass(current_schema() || '.' || candidate) IS NOT NULL THEN
            EXECUTE format('SELECT count(*) FROM %I', candidate) INTO row_count;
            IF row_count > 0 THEN
                RAISE EXCEPTION 'cannot retire unused infrastructure table %, rows=%', candidate, row_count;
            END IF;
        END IF;
    END LOOP;
END;
$$;

DROP TABLE IF EXISTS mom_inbox_event;
DROP TABLE IF EXISTS undo_log;
