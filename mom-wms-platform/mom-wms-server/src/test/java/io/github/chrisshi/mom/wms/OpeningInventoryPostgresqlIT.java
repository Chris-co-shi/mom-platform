package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.mdm.api.WarehouseResourceReference;
import io.github.chrisshi.mom.wms.application.InventoryQueryService;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Entry;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Posting;
import io.github.chrisshi.mom.wms.application.OpeningInventoryApplication.LineInput;
import io.github.chrisshi.mom.wms.application.OpeningInventoryApplication;
import io.github.chrisshi.mom.wms.application.OpeningInventoryCommitService;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService.LocationSnapshot;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService.MaterialSnapshot;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import io.github.chrisshi.mom.wms.application.WarehouseStatus;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.wms.infrastructure.entity.OpeningDocumentLineEntity;
import io.github.chrisshi.mom.wms.infrastructure.query.OpeningInventoryQueryMapper;
import io.github.chrisshi.mom.wms.infrastructure.mapper.OpeningInventoryLineMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 使用 PostgreSQL 验证期初单、库存记账、幂等、回滚、并发及仓库启用状态。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MomWmsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.cloud.nacos.discovery.enabled=false", "mom.security.resource-server.enabled=false"})
class OpeningInventoryPostgresqlIT {
    @Container static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.7-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PG.getJdbcUrl() + "&currentSchema=mom_wms");
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
    }

    @Autowired OpeningInventoryCommitService commits;
    @Autowired OpeningInventoryApplication opening;
    @Autowired InventoryAccountingService accounting;
    @Autowired OpeningInventoryQueryMapper mapper;
    @Autowired OpeningInventoryLineMapper lineMapper;
    @Autowired InventoryQueryService queries;
    @Autowired IdGenerator ids;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE wms_opening_document_line,wms_opening_document,wms_warehouse_inventory_state," +
                "wms_inventory_transaction_entry,wms_inventory_transaction,wms_inventory_balance,wms_code_rule_sequence,wms_code_rule_issue CASCADE");
        jdbc.update("UPDATE wms_code_rule_revision SET status='ENABLED' WHERE rule_id='8300000000000000001' AND revision_no=1");
    }

    @Test void openingNumbersFollowPublishedRuleAndRollbackWithDocument() {
        String first = createDraft("2000000000000000101");
        assertThat(mapper.find(first).documentNo()).isEqualTo("OB-00000001");
        assertThatThrownBy(() -> createDraft("2000000000000000101"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(jdbc.queryForObject("SELECT last_value FROM wms_code_rule_sequence WHERE rule_id='8300000000000000001'", Long.class)).isEqualTo(1L);
        String second = createDraft("2000000000000000102");
        assertThat(mapper.find(second).documentNo()).isEqualTo("OB-00000002");
        assertThatThrownBy(() -> commits.create(first, new WarehouseResourceReference("WAREHOUSE",
                "2000000000000000103", "WH-3", "仓库三", "1000000000000000001",
                "1000000000000000001", "Asia/Shanghai", null, 0L, true, null), "operator-1"))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_warehouse_inventory_state WHERE warehouse_id='2000000000000000103'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT last_value FROM wms_code_rule_sequence WHERE rule_id='8300000000000000001'", Long.class)).isEqualTo(2L);
    }

    @Test void disabledRuleLeavesNoOpeningDraftOrWarehouseState() {
        jdbc.update("UPDATE wms_code_rule_revision SET status='DISABLED' WHERE rule_id='8300000000000000001'");
        assertThatThrownBy(() -> createDraft("2000000000000000104"))
                .isInstanceOfSatisfying(io.github.chrisshi.mom.coderule.CodeRuleFailure.class,
                        failure -> assertThat(failure.code()).isEqualTo("rule_not_enabled"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_opening_document", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_warehouse_inventory_state", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_code_rule_sequence", Integer.class)).isZero();
    }

    @Test void concurrentOpeningCreatesDistinctNumbers() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { start.await(); return createDraft("2000000000000000105"); });
            var two = pool.submit(() -> { start.await(); return createDraft("2000000000000000106"); });
            start.countDown();
            assertThat(mapper.find(one.get()).documentNo()).isIn("OB-00000001", "OB-00000002");
            assertThat(mapper.find(two.get()).documentNo()).isIn("OB-00000001", "OB-00000002");
        }
        assertThat(jdbc.queryForList("SELECT document_no FROM wms_opening_document ORDER BY document_no", String.class))
                .containsExactly("OB-00000001", "OB-00000002");
    }

    @Test void openingListCanReconcileByWarehouseAfterUnknownCreateResult() {
        String first = createDraft("2000000000000000107");
        createDraft("2000000000000000108");
        var filtered = opening.list(new io.github.chrisshi.mom.core.page.PageQuery<>(
                new OpeningInventoryApplication.ListFilter("2000000000000000107"), 1, 20));
        assertThat(filtered.total()).isEqualTo(1);
        assertThat(filtered.records()).extracting(OpeningInventoryApplication.Document::id).containsExactly(first);
    }

    /** 覆盖 MP 明细 insert/select 与受单据归属约束的编辑、删除；业务版本按操作精确推进。 */
    @Test void openingLineMomDataCrudPreservesDocumentVersionAndView() {
        String documentId = createDraft("2000000000000000111");
        var material = new MaterialSnapshot("1001", "MAT-1", "物料一", "REQUIRED", "kg");
        var location = new LocationSnapshot("3001", "LOC-3001");
        LineInput original = new LineInput("1001", "3001", "BATCH-1", "SN-NEW",
                QualityStatus.QUALIFIED, WarehouseStatus.NORMAL, new BigDecimal("2.50"));
        String lineId = ids.nextId();
        commits.addLine(opening.get(documentId).document(), material, location, original, lineId, "operator-1");

        var saved = lineMapper.selectById(lineId);
        assertThat(saved.getDocumentId()).isEqualTo(documentId);
        assertThat(saved.getQuantity()).isEqualByComparingTo("2.50");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(opening.get(documentId).lines()).hasSize(1);
        // DB numeric(24,6) returns scale 6: keep existing decimal-string API precision.\n        assertThat(opening.get(documentId).lines().getFirst().quantity()).isEqualTo("2.500000");
        assertThat(mapper.find(documentId).version()).isEqualTo(1);

        // MyBatis-Plus 的实体 update 默认会跳过 null，清空 SN 必须通过 wrapper.set(null)。
        LineInput changed = new LineInput("1001", "3001", "BATCH-1", null,
                QualityStatus.QUALIFIED, WarehouseStatus.NORMAL, new BigDecimal("3.75"));
        commits.editLine(opening.get(documentId).document(), lineId, material, location, changed, "operator-1");
        assertThat(lineMapper.selectById(lineId).getQuantity()).isEqualByComparingTo("3.75");
        assertThat(lineMapper.selectById(lineId).getSnCode()).isNull();
        assertThat(mapper.find(documentId).version()).isEqualTo(2);

        // 模拟 MDM 批次策略由 REQUIRED 调整为 NONE 后，刷新可信物料快照并清除旧批次。
        var untracked = new MaterialSnapshot("1001", "MAT-1", "物料一", "NONE", "kg");
        LineInput noBatch = new LineInput("1001", "3001", null, null,
                QualityStatus.QUALIFIED, WarehouseStatus.NORMAL, new BigDecimal("3.75"));
        commits.editLine(opening.get(documentId).document(), lineId, untracked, location, noBatch, "operator-1");
        assertThat(lineMapper.selectById(lineId).getBatchCode()).isNull();
        assertThat(mapper.find(documentId).version()).isEqualTo(3);

        commits.deleteLine(documentId, lineId, "operator-1");
        assertThat(lineMapper.selectById(lineId)).isNull();
        assertThat(opening.get(documentId).lines()).isEmpty();
        assertThat(mapper.find(documentId).version()).isEqualTo(4);
    }

    /** 父单据 FOR UPDATE + mom-data 条件删除必须在同事务清空明细和可重新建账的仓库状态。 */
    @Test void deletingDraftRemovesLinesAndInitializingStateAtomically() {
        String warehouseId = "2000000000000000112";
        String documentId = createDraft(warehouseId);
        add(documentId, "SN-TO-DELETE", "3001", "1.25");
        assertThat(opening.get(documentId).lines()).hasSize(1);

        commits.deleteDraft(documentId);
        assertThat(mapper.find(documentId)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_opening_document_line WHERE document_id=?",
                Integer.class, documentId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_warehouse_inventory_state WHERE warehouse_id=?",
                Integer.class, warehouseId)).isZero();
        // 已撤销的草稿不锁死仓库，下一个合法初始化仍可创建。
        assertThat(createDraft(warehouseId)).isNotEqualTo(documentId);
    }

    @Test void postsMultipleLinesOnceAndActivationIsSeparate() {
        String documentId = createDraft("2000000000000000001");
        add(documentId, "SN-A", "3001", "4.25");
        add(documentId, "SN-B", "3002", "2.75");
        assertThat(jdbc.queryForMap("SELECT created_by,updated_by FROM wms_opening_document WHERE id=?", documentId))
                .containsEntry("created_by", "operator-1").containsEntry("updated_by", "operator-1");
        assertThat(jdbc.queryForObject("SELECT updated_by FROM wms_warehouse_inventory_state WHERE warehouse_id=?",
                String.class, "2000000000000000001")).isEqualTo("operator-1");
        var document = mapper.find(documentId);
        var replayEntries = lineMapper.selectList(Wrappers.<OpeningDocumentLineEntity>lambdaQuery()
                .eq(OpeningDocumentLineEntity::getDocumentId, documentId)
                .orderByAsc(OpeningDocumentLineEntity::getLineNo))
                .stream().map(line -> new Entry(new Dimension(
                line.getMaterialId(), line.getMaterialCode(), line.getMaterialName(), line.getUomCode(), line.getBatchCode(),
                line.getSnCode(), line.getWarehouseId(), line.getWarehouseCode(), line.getLocationId(), line.getLocationCode(),
                line.getQualityStatus(), line.getWarehouseStatus()), line.getQuantity())).toList();
        assertThat(document.warehouseState()).isEqualTo("INITIALIZING");
        String transactionId = commits.post(documentId, document.version(), "operator-1");
        assertThat(commits.post(documentId, document.version(), "operator-1")).isEqualTo(transactionId);
        assertThat(accounting.post(new Posting("OPENING:" + documentId, "OPENING", "OPENING_DOCUMENT",
                documentId, "operator-1", replayEntries))).isEqualTo(transactionId);
        var alteredEntries = replayEntries.stream().map(entry -> new Entry(entry.dimension(),
                entry.delta().add(BigDecimal.ONE))).toList();
        assertThatThrownBy(() -> accounting.post(new Posting("OPENING:" + documentId, "OPENING",
                "OPENING_DOCUMENT", documentId, "operator-1", alteredEntries)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存请求键已用于不同内容");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction WHERE id=? AND transaction_type='OPENING' AND source_type='OPENING_DOCUMENT' AND source_id=? AND operator_id='operator-1'", Integer.class, transactionId, documentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction_entry WHERE transaction_id=?", Integer.class, transactionId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_balance", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM wms_opening_document WHERE id=?", String.class, documentId)).isEqualTo("POSTED");
        assertThat(jdbc.queryForMap("SELECT updated_by,posted_by FROM wms_opening_document WHERE id=?", documentId))
                .containsEntry("updated_by", "operator-1").containsEntry("posted_by", "operator-1");
        assertThat(jdbc.queryForObject("SELECT posted_at IS NOT NULL FROM wms_opening_document WHERE id=?", Boolean.class,
                documentId)).isTrue();
        assertThat(mapper.find(documentId).warehouseState()).isEqualTo("PENDING_ACTIVATION");
        assertThat(queries.details(new io.github.chrisshi.mom.core.page.PageQuery<>(
                new InventoryQueryService.DetailFilter("1001", "2000000000000000001", null,
                        null, null, null, null), 1, 10)).records()).hasSize(2);
        commits.activate("2000000000000000001", "operator-2");
        assertThat(jdbc.queryForMap("SELECT updated_by,activated_by FROM wms_warehouse_inventory_state WHERE warehouse_id=?",
                "2000000000000000001")).containsEntry("updated_by", "operator-2")
                .containsEntry("activated_by", "operator-2");
        assertThat(mapper.find(documentId).warehouseState()).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> commits.activate("2000000000000000001", "operator-2"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> createDraft("2000000000000000001")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> commits.deleteDraft(documentId)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> commits.addLine(opening.get(documentId).document(),
                new MaterialSnapshot("1001", "MAT-1", "物料一", "REQUIRED", "kg"),
                new LocationSnapshot("3001", "LOC-3001"),
                new LineInput("1001", "3001", "BATCH-1", "SN-LATE", QualityStatus.QUALIFIED,
                        WarehouseStatus.NORMAL, BigDecimal.ONE), ids.nextId(), "operator-3"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT posted_by FROM wms_opening_document WHERE id=?", String.class,
                documentId)).isEqualTo("operator-1");
    }

    @Test void duplicateSnAcrossLinesRollsBackWholeOpeningTransaction() {
        String documentId = createDraft("2000000000000000002");
        add(documentId, "SN-DUP", "3001", "1");
        add(documentId, "SN-DUP", "3002", "1");
        var document = mapper.find(documentId);
        assertThatThrownBy(() -> commits.post(documentId, document.version(), "operator-1"))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction_entry", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_balance", Integer.class)).isZero();
        assertThat(mapper.find(documentId).status()).isEqualTo("DRAFT");
        assertThat(mapper.find(documentId).warehouseState()).isEqualTo("INITIALIZING");
    }

    @Test void concurrentPostForSameDocumentCreatesOneTransaction() throws Exception {
        String documentId = createDraft("2000000000000000003");
        add(documentId, null, "3001", "3");
        long version = mapper.find(documentId).version();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { start.await(); return commits.post(documentId, version, "operator-1"); });
            var two = pool.submit(() -> { start.await(); return commits.post(documentId, version, "operator-1"); });
            start.countDown();
            assertThat(one.get()).isEqualTo(two.get());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM wms_inventory_balance", BigDecimal.class)).isEqualByComparingTo("3");
    }

    private String createDraft(String warehouseId) {
        String id = ids.nextId();
        commits.create(id, new WarehouseResourceReference("WAREHOUSE", warehouseId, "WH-1", "主仓",
                "1000000000000000001", "1000000000000000001", "Asia/Shanghai", null, 0L, true, null), "operator-1");
        return id;
    }

    private void add(String documentId, String sn, String locationId, String quantity) {
        var document = opening.get(documentId).document();
        commits.addLine(document, new MaterialSnapshot("1001", "MAT-1", "物料一", "REQUIRED", "kg"),
                new LocationSnapshot(locationId, "LOC-" + locationId),
                new LineInput("1001", locationId, "BATCH-1", sn, QualityStatus.QUALIFIED,
                        WarehouseStatus.NORMAL, new BigDecimal(quantity)), ids.nextId(), "operator-1");
    }
}
