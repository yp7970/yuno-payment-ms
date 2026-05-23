package com.yuno.payment.config;

import org.apache.ibatis.type.*;

import java.sql.*;
import java.util.UUID;

/**
 * MyBatis TypeHandler for UUID ↔ PostgreSQL UUID column.
 *
 * PostgreSQL stores UUIDs as JdbcType.OTHER.
 * Without this handler MyBatis would try to call setString() which
 * PostgreSQL rejects for UUID-typed columns.
 *
 * Registered globally in application.yml:
 *   mybatis.type-handlers-package: com.yuno.payment.config
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class UUIDTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType)
            throws SQLException {
        // PostgreSQL JDBC driver accepts java.util.UUID via setObject
        ps.setObject(i, parameter);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return toUUID(value);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return toUUID(value);
    }

    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return toUUID(value);
    }

    private UUID toUUID(String value) {
        return (value != null && !value.isBlank()) ? UUID.fromString(value) : null;
    }
}
