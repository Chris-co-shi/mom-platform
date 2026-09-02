package io.github.chrisshi.mom.data.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL {@code jsonb} 与已经完成确定性序列化的 JSON String 之间的 MyBatis TypeHandler。
 *
 * <p>该处理器属于 Framework Data 基础设施，只负责 JDBC 类型桥接，不负责 JSON 业务校验、排序或
 * 反序列化。调用方必须通过 {@code @TableName(autoResultMap = true)} 和字段级 {@code @TableField}
 * 显式启用，避免将所有 String 字段全局注册为 jsonb。</p>
 */
public final class PostgresqlJsonbStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(
        PreparedStatement statement, int index, String parameter, JdbcType jdbcType) throws SQLException {
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        jsonb.setValue(parameter);
        statement.setObject(index, jsonb);
    }

    @Override
    public String getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return statement.getString(columnIndex);
    }
}
