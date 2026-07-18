-- 将已发布的 P01-S04 技术表从数据库自增 BIGINT 主键迁移为应用侧生成的字符串 ID。
-- 迁移保留已有主键值的十进制文本形式；后续新记录由 MyBatis-Plus ASSIGN_ID 在写入前生成。
ALTER TABLE technical_data_probe
    ALTER COLUMN id DROP IDENTITY IF EXISTS;

ALTER TABLE technical_data_probe
    ALTER COLUMN id TYPE VARCHAR(19) USING id::VARCHAR(19);

ALTER TABLE technical_data_probe
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN technical_data_probe.id IS '应用侧生成的 varchar(19) 字符串技术主键，避免前端 64 位整数精度丢失';
COMMENT ON COLUMN technical_data_probe.deleted IS '逻辑删除标识：false 有效，true 已删除';
