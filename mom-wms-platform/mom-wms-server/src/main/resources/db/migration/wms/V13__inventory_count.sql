-- 盘点任务为可变业务单；盘点行为完成后保留，不做逻辑删除。
CREATE TABLE wms_inventory_count_task (
    id varchar(19) NOT NULL,
    task_no varchar(32) NOT NULL,
    request_key varchar(128) NOT NULL,
    warehouse_id varchar(19) NOT NULL,
    warehouse_code varchar(64) NOT NULL,
    location_ids varchar(19)[] NOT NULL DEFAULT '{}',
    material_ids varchar(19)[] NOT NULL DEFAULT '{}',
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    transaction_id varchar(19),
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    started_by varchar(128),
    started_at timestamptz,
    confirmed_by varchar(128),
    confirmed_at timestamptz,
    completed_by varchar(128),
    completed_at timestamptz,
    cancelled_by varchar(128),
    cancelled_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_wms_inventory_count_task PRIMARY KEY (id),
    CONSTRAINT uk_wms_inventory_count_task_no UNIQUE (task_no),
    CONSTRAINT uk_wms_inventory_count_task_request UNIQUE (request_key),
    CONSTRAINT uk_wms_inventory_count_task_transaction UNIQUE (transaction_id),
    CONSTRAINT ck_wms_inventory_count_task_status CHECK (status IN ('DRAFT','COUNTING','CONFIRMED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_wms_inventory_count_task_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_wms_inventory_count_task_lifecycle CHECK
      ((status <> 'DRAFT' OR started_at IS NULL) AND
       (status NOT IN ('COUNTING','CONFIRMED','COMPLETED') OR started_at IS NOT NULL) AND
       (status NOT IN ('CONFIRMED','COMPLETED') OR confirmed_at IS NOT NULL) AND
       (status <> 'COMPLETED' OR completed_at IS NOT NULL) AND
       (status <> 'CANCELLED' OR cancelled_at IS NOT NULL))
);
-- 单仓最多一条持久锁；完成/取消时物理删除锁记录，任务与明细仍保留。
CREATE TABLE wms_inventory_count_scope_lock (
    warehouse_id varchar(19) NOT NULL,
    task_id varchar(19) NOT NULL,
    acquired_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_wms_inventory_count_scope_lock PRIMARY KEY (warehouse_id),
    CONSTRAINT uk_wms_inventory_count_scope_lock_task UNIQUE (task_id)
);
-- 账面快照与实盘事实按完整维度唯一；NULL 实盘表示尚未盘到。
CREATE TABLE wms_inventory_count_line (
    id varchar(19) NOT NULL,
    task_id varchar(19) NOT NULL,
    material_id varchar(19) NOT NULL,
    material_code varchar(64) NOT NULL,
    material_name varchar(200) NOT NULL,
    uom_code varchar(64) NOT NULL,
    batch_code varchar(128),
    sn_code varchar(128),
    warehouse_id varchar(19) NOT NULL,
    warehouse_code varchar(64) NOT NULL,
    location_id varchar(19) NOT NULL,
    location_code varchar(64) NOT NULL,
    quality_status varchar(16) NOT NULL,
    warehouse_status varchar(16) NOT NULL,
    book_quantity numeric(24,6) NOT NULL,
    counted_quantity numeric(24,6),
    snapshot_by varchar(128) NOT NULL,
    snapshot_at timestamptz NOT NULL DEFAULT now(),
    counted_by varchar(128),
    counted_at timestamptz,
    CONSTRAINT pk_wms_inventory_count_line PRIMARY KEY (id),
    CONSTRAINT uk_wms_inventory_count_line_dimension UNIQUE NULLS NOT DISTINCT
      (task_id,material_id,batch_code,sn_code,location_id,quality_status,warehouse_status),
    CONSTRAINT ck_wms_inventory_count_line_quality CHECK (quality_status IN ('PENDING','QUALIFIED','RESTRICTED','REJECTED')),
    CONSTRAINT ck_wms_inventory_count_line_warehouse_status CHECK (warehouse_status IN ('NORMAL','FROZEN')),
    CONSTRAINT ck_wms_inventory_count_line_quantities CHECK (book_quantity >= 0 AND (counted_quantity IS NULL OR counted_quantity >= 0)),
    CONSTRAINT ck_wms_inventory_count_line_count_actor CHECK
      ((counted_quantity IS NULL AND counted_by IS NULL AND counted_at IS NULL) OR
       (counted_quantity IS NOT NULL AND counted_by IS NOT NULL AND counted_at IS NOT NULL))
);
-- 快照维度始终不可修改；确认后实盘事实也不可修改或删除。
CREATE FUNCTION wms_inventory_count_line_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE task_status varchar(16);
DECLARE owner_task_id varchar(19);
BEGIN
    IF TG_OP='DELETE' THEN owner_task_id := OLD.task_id;
    ELSE owner_task_id := NEW.task_id;
    END IF;
    SELECT status INTO task_status FROM wms_inventory_count_task
     WHERE id=owner_task_id;
    IF TG_OP='INSERT' THEN
        IF NOT ((task_status='DRAFT' AND NEW.counted_quantity IS NULL)
             OR (task_status='COUNTING' AND NEW.book_quantity=0 AND NEW.counted_quantity IS NOT NULL)) THEN
            RAISE EXCEPTION 'inventory count line insertion is not allowed in this task state';
        END IF;
        RETURN NEW;
    END IF;
    IF task_status <> 'COUNTING' OR TG_OP='DELETE' THEN
        RAISE EXCEPTION 'inventory count line is immutable in this task state';
    END IF;
    IF (to_jsonb(NEW)-'counted_quantity'-'counted_by'-'counted_at')
       IS DISTINCT FROM (to_jsonb(OLD)-'counted_quantity'-'counted_by'-'counted_at') THEN
        RAISE EXCEPTION 'inventory count book snapshot is immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER wms_count_line_guard BEFORE INSERT OR UPDATE OR DELETE ON wms_inventory_count_line
    FOR EACH ROW EXECUTE FUNCTION wms_inventory_count_line_guard();
-- 列表按仓库与创建时间读取；详情按任务读取所有快照行。
CREATE INDEX ix_wms_inventory_count_task_warehouse_created ON wms_inventory_count_task(warehouse_id,created_at DESC,id DESC);
CREATE INDEX ix_wms_inventory_count_line_task ON wms_inventory_count_line(task_id,id);
-- 盘点开始按单仓读取全部余额（含零余额）；既有非零余额部分索引不能覆盖此查询。
-- 余额规模预计 10^3～10^5 个完整维度，仓库前缀可避免跨仓库全表扫描。
CREATE INDEX ix_wms_inventory_balance_count_warehouse ON wms_inventory_balance(warehouse_id,id);
COMMENT ON TABLE wms_inventory_count_task IS '单仓停账盘点任务；完成和取消后保留业务生命周期审计';
COMMENT ON TABLE wms_inventory_count_scope_lock IS '整仓盘点独占锁；与任务状态及库存调整同一事务管理';
COMMENT ON TABLE wms_inventory_count_line IS '盘点开始时账面快照及人工实盘数量；确认后不可变';
COMMENT ON COLUMN wms_inventory_count_task.id IS '盘点任务技术主键';
COMMENT ON COLUMN wms_inventory_count_task.task_no IS 'WMS 编码规则生成的盘点业务编号';
COMMENT ON COLUMN wms_inventory_count_task.request_key IS '创建盘点草稿的客户端稳定幂等请求键';
COMMENT ON COLUMN wms_inventory_count_task.warehouse_id IS 'MDM 仓库稳定 ID';
COMMENT ON COLUMN wms_inventory_count_task.warehouse_code IS '创建时仓库编码快照';
COMMENT ON COLUMN wms_inventory_count_task.location_ids IS '限定库位 ID 集合；空数组表示整仓全部库位';
COMMENT ON COLUMN wms_inventory_count_task.material_ids IS '限定物料 ID 集合；空数组表示全部物料';
COMMENT ON COLUMN wms_inventory_count_task.status IS 'DRAFT、COUNTING、CONFIRMED、COMPLETED 或 CANCELLED';
COMMENT ON COLUMN wms_inventory_count_task.transaction_id IS '整单库存调整产生的唯一交易 ID';
COMMENT ON COLUMN wms_inventory_count_task.created_by IS '创建任务的可信操作人';
COMMENT ON COLUMN wms_inventory_count_task.created_at IS '任务创建数据库时间';
COMMENT ON COLUMN wms_inventory_count_task.updated_by IS '最后编辑草稿范围的可信操作人';
COMMENT ON COLUMN wms_inventory_count_task.updated_at IS '最后一次任务状态或范围更新时间';
COMMENT ON COLUMN wms_inventory_count_task.started_by IS '开始盘点并生成快照的可信操作人';
COMMENT ON COLUMN wms_inventory_count_task.started_at IS '锁定整仓并生成快照的数据库时间';
COMMENT ON COLUMN wms_inventory_count_task.confirmed_by IS '确认差异的可信操作人';
COMMENT ON COLUMN wms_inventory_count_task.confirmed_at IS '差异确认数据库时间';
COMMENT ON COLUMN wms_inventory_count_task.completed_by IS '执行整单库存调整的可信操作人';
COMMENT ON COLUMN wms_inventory_count_task.completed_at IS '调整完成并释放整仓锁的数据库时间';
COMMENT ON COLUMN wms_inventory_count_task.cancelled_by IS '取消盘点的可信操作人';
COMMENT ON COLUMN wms_inventory_count_task.cancelled_at IS '取消并释放整仓锁的数据库时间';
COMMENT ON COLUMN wms_inventory_count_task.version IS '任务状态和范围更新版本';
COMMENT ON COLUMN wms_inventory_count_scope_lock.warehouse_id IS '被盘点独占的 MDM 仓库稳定 ID';
COMMENT ON COLUMN wms_inventory_count_scope_lock.task_id IS '当前持有整仓锁的盘点任务 ID';
COMMENT ON COLUMN wms_inventory_count_scope_lock.acquired_at IS '整仓锁写入数据库时间';
COMMENT ON COLUMN wms_inventory_count_line.id IS '盘点明细技术主键';
COMMENT ON COLUMN wms_inventory_count_line.task_id IS '所属盘点任务 ID';
COMMENT ON COLUMN wms_inventory_count_line.material_id IS '账面或账外实物对应 MDM 物料稳定 ID';
COMMENT ON COLUMN wms_inventory_count_line.material_code IS '快照时物料编码';
COMMENT ON COLUMN wms_inventory_count_line.material_name IS '快照时物料名称';
COMMENT ON COLUMN wms_inventory_count_line.uom_code IS '盘点数量使用的物料库存基础单位';
COMMENT ON COLUMN wms_inventory_count_line.batch_code IS '真实已有批次身份；空值表示无批次';
COMMENT ON COLUMN wms_inventory_count_line.sn_code IS '物理 SN 标签；可对应不同仓储状态明细';
COMMENT ON COLUMN wms_inventory_count_line.warehouse_id IS '盘点仓库稳定 ID';
COMMENT ON COLUMN wms_inventory_count_line.warehouse_code IS '盘点仓库编码快照';
COMMENT ON COLUMN wms_inventory_count_line.location_id IS '盘点库位稳定 ID';
COMMENT ON COLUMN wms_inventory_count_line.location_code IS '盘点库位编码快照';
COMMENT ON COLUMN wms_inventory_count_line.quality_status IS '实物明确归属的质量状态';
COMMENT ON COLUMN wms_inventory_count_line.warehouse_status IS '实物明确归属的仓储状态';
COMMENT ON COLUMN wms_inventory_count_line.book_quantity IS '开始盘点时固化的账面数量';
COMMENT ON COLUMN wms_inventory_count_line.counted_quantity IS '人工实盘数量；NULL 表示未盘，零表示实盘为零';
COMMENT ON COLUMN wms_inventory_count_line.snapshot_by IS '生成账面快照或录入账外行的操作人';
COMMENT ON COLUMN wms_inventory_count_line.snapshot_at IS '生成账面快照或录入账外行的数据库时间';
COMMENT ON COLUMN wms_inventory_count_line.counted_by IS '最后录入实盘数量的可信操作人';
COMMENT ON COLUMN wms_inventory_count_line.counted_at IS '最后录入实盘数量的数据库时间';

-- 盘点业务编号沿用现有 WMS 规则与事务型流水。
INSERT INTO wms_code_rule(id,rule_code,usage_scenario,description,version,created_at,created_by,updated_at,updated_by)
VALUES ('8300000000000000013','wms.inventory.count','库存盘点任务编号','创建草稿时分配正式编号',0,now(),'WMS_MIGRATION',now(),'WMS_MIGRATION');
INSERT INTO wms_code_rule_revision(id,rule_id,revision_no,status,definition,created_at,created_by,updated_at,updated_by)
VALUES ('8300000000000000014','8300000000000000013',1,'ENABLED',
        '{"schemaVersion":1,"segments":[{"id":"prefix","type":"LITERAL","params":{"value":"IC-"}},{"id":"serial","type":"SEQUENCE","params":{"width":8,"step":1}}]}'::jsonb,
        now(),'WMS_MIGRATION',now(),'WMS_MIGRATION');
