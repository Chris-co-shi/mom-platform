package io.github.chrisshi.mom.wms.infrastructure.entity;

import org.apache.ibatis.type.ArrayTypeHandler;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import tools.jackson.databind.json.JsonMapper;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/** 保留盘点范围的字符串数组语义，分别映射 PostgreSQL 数组和 MySQL JSON 数组。 */
public final class InventoryCountScopeTypeHandler extends BaseTypeHandler<String[]> {
    private static final JsonMapper JSON = new JsonMapper();
    private final ArrayTypeHandler postgres = new ArrayTypeHandler();

    /** 按 JDBC 数据库产品绑定原生数组或 JSON。 */
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, String[] value, JdbcType jdbcType)
            throws SQLException {
        if ("MySQL".equals(statement.getConnection().getMetaData().getDatabaseProductName())) {
            statement.setString(index, JSON.writeValueAsString(value));
        } else {
            postgres.setNonNullParameter(statement, index, value, jdbcType);
        }
    }

    /** 按列名读取盘点范围数组。 */
    @Override
    public String[] getNullableResult(ResultSet result, String column) throws SQLException {
        return getNullableResult(result, result.findColumn(column));
    }

    /** 按列序号读取盘点范围数组。 */
    @Override
    public String[] getNullableResult(ResultSet result, int column) throws SQLException {
        if (result.getMetaData().getColumnType(column) == Types.ARRAY) {
            return (String[]) postgres.getNullableResult(result, column);
        }
        return readJson(result.getString(column));
    }

    /** 从 JDBC 过程返回值读取盘点范围数组。 */
    @Override
    public String[] getNullableResult(CallableStatement statement, int column) throws SQLException {
        if ("MySQL".equals(statement.getConnection().getMetaData().getDatabaseProductName())) {
            return readJson(statement.getString(column));
        }
        return (String[]) postgres.getNullableResult(statement, column);
    }

    private static String[] readJson(String value) {
        return value == null ? null : JSON.readValue(value, String[].class);
    }
}
