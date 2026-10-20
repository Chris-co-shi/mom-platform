package io.github.chrisshi.mom.wms;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 在真实 PostgreSQL 上验证 WMS 全新建库及历史版本增量升级。 */
@Testcontainers(disabledWithoutDocker = true)
class WmsFlywayReleasePostgresqlIT {
    @Container static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.7-alpine");

    /** 新建库应用全链，并验证 V11 的最终列注释和 Flyway History。 */
    @Test
    void freshSchemaMigratesAndValidates() throws SQLException {
        Flyway flyway = flyway("mom_wms_fresh", null);
        flyway.migrate();
        flyway.validate();
        try (Connection connection = connection("mom_wms_fresh")) {
            assertThat(number(connection, "SELECT count(*) FROM flyway_schema_history "
                    + "WHERE success AND version IS NOT NULL")).isEqualTo(21);
            assertThat(value(connection, "SELECT col_description('wms_code_rule'::regclass, "
                    + "(SELECT attnum FROM pg_attribute WHERE attrelid='wms_code_rule'::regclass "
                    + "AND attname='version'))")).isNotBlank();
        }
    }

    /** 从 V5.3 的已有余额和交易事实升级，证明 V5.4 的 SN 索引和后续迁移保留数据。 */
    @Test
    void oldInventorySchemaUpgradesWithoutChangingFacts() throws SQLException {
        flyway("mom_wms_old", "5.3").migrate();
        try (Connection connection = connection("mom_wms_old")) {
            connection.createStatement().executeUpdate("""
                    INSERT INTO wms_inventory_balance
                    (id,material_id,material_code,material_name,uom_code,sn_code,warehouse_id,warehouse_code,
                     location_id,location_code,quality_status,warehouse_status,quantity)
                    VALUES ('9000000000000000001','9000000000000000002','M-1','历史物料','PCS','SN-OLD',
                            '9000000000000000003','WH-1','9000000000000000004','L-1','QUALIFIED','NORMAL',1)
                    """);
            connection.createStatement().executeUpdate("""
                    INSERT INTO wms_inventory_transaction(id,request_key,request_hash)
                    VALUES ('9000000000000000005','HISTORY-1',repeat('a',64))
                    """);
            connection.createStatement().executeUpdate("""
                    INSERT INTO wms_inventory_transaction_entry(id,transaction_id,line_no,balance_id,delta_quantity)
                    VALUES ('9000000000000000006','9000000000000000005',1,'9000000000000000001',1)
                    """);
            connection.createStatement().executeUpdate("""
                    INSERT INTO wms_warehouse_inventory_state(id,warehouse_id,warehouse_code,state)
                    VALUES ('9000000000000000010','9000000000000000003','WH-1','ACTIVE')
                    """);
            connection.createStatement().executeUpdate("""
                    INSERT INTO wms_opening_document(id,document_no,warehouse_id,warehouse_code,status,created_by)
                    VALUES ('9000000000000000011','OB-OLD','9000000000000000003','WH-1','POSTED','original-actor')
                    """);
        }
        Flyway latest = flyway("mom_wms_old", null);
        latest.migrate();
        latest.validate();
        try (Connection connection = connection("mom_wms_old")) {
            assertThat(number(connection, "SELECT quantity FROM wms_inventory_balance WHERE id='9000000000000000001'"))
                    .isEqualTo(1);
            assertThat(value(connection, "SELECT created_at::text FROM wms_inventory_balance WHERE id='9000000000000000001'"))
                    .isNull();
            assertThat(value(connection, "SELECT posted_by FROM wms_opening_document WHERE id='9000000000000000011'"))
                    .isNull();
            assertThat(value(connection, "SELECT updated_by FROM wms_warehouse_inventory_state WHERE id='9000000000000000010'"))
                    .isNull();
            assertThat(number(connection, "SELECT count(*) FROM wms_inventory_transaction_entry "
                    + "WHERE transaction_id='9000000000000000005' AND delta_quantity=1")).isEqualTo(1);
            assertThatThrownBy(() -> connection.createStatement().executeUpdate("""
                    INSERT INTO wms_inventory_balance
                    (id,material_id,material_code,material_name,uom_code,sn_code,warehouse_id,warehouse_code,
                     location_id,location_code,quality_status,warehouse_status,quantity)
                    VALUES ('9000000000000000007','9000000000000000002','M-1','历史物料','PCS','SN-OLD',
                            '9000000000000000003','WH-1','9000000000000000008','L-2','QUALIFIED','NORMAL',1)
                    """)).isInstanceOf(SQLException.class);
        }
    }

    /** 已应用 V8 的 History 保持原 checksum，V11 只补注释且不重放 V8。 */
    @Test
    void publishedV8UpgradesWithoutChecksumChange() throws SQLException {
        flyway("mom_wms_v9", "9").migrate();
        Flyway latest = flyway("mom_wms_v9", null);
        latest.migrate();
        latest.validate();
        try (Connection connection = connection("mom_wms_v9")) {
            assertThat(number(connection, "SELECT checksum FROM flyway_schema_history WHERE version='8'"))
                    .isEqualTo(114386239);
            assertThat(number(connection, "SELECT count(*) FROM flyway_schema_history WHERE version='8'"))
                    .isEqualTo(1);
            assertThat(value(connection, "SELECT col_description('wms_code_rule_revision'::regclass, "
                    + "(SELECT attnum FROM pg_attribute WHERE attrelid='wms_code_rule_revision'::regclass "
                    + "AND attname='status'))")).isNotBlank();
        }
    }

    private static Flyway flyway(String schema, String target) {
        var configuration = Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration/wms");
        if (target != null) configuration.target(MigrationVersion.fromVersion(target));
        return configuration.load();
    }

    private static Connection connection(String schema) throws SQLException {
        Connection connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        connection.createStatement().execute("SET search_path TO " + schema);
        return connection;
    }

    private static int number(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static String value(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }
}
