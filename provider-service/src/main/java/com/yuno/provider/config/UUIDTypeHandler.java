package com.yuno.provider.config;

import org.apache.ibatis.type.*;
import java.sql.*;
import java.util.UUID;

@MappedTypes(UUID.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class UUIDTypeHandler extends BaseTypeHandler<UUID> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID p, JdbcType j) throws SQLException {
        ps.setObject(i, p);
    }
    @Override
    public UUID getNullableResult(ResultSet rs, String col) throws SQLException {
        String v = rs.getString(col); return v != null ? UUID.fromString(v) : null;
    }
    @Override
    public UUID getNullableResult(ResultSet rs, int col) throws SQLException {
        String v = rs.getString(col); return v != null ? UUID.fromString(v) : null;
    }
    @Override
    public UUID getNullableResult(CallableStatement cs, int col) throws SQLException {
        String v = cs.getString(col); return v != null ? UUID.fromString(v) : null;
    }
}
