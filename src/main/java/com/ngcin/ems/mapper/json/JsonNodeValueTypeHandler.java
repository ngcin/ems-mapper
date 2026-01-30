package com.ngcin.ems.mapper.json;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis TypeHandler for JsonNodeValue container.
 * Always returns non-null value (EMPTY for null/empty columns).
 *
 * @see JsonNodeValue
 */
@MappedTypes({JsonNodeValue.class})
public class JsonNodeValueTypeHandler extends BaseTypeHandler<JsonNodeValue> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
            JsonNodeValue parameter, JdbcType jdbcType) throws SQLException {
        if (parameter.isPresent()) {
            String json;
            if (parameter.hasDbSource()) {
                json = parameter.getSource();
            } else {
                try {
                    json = ReaderWriter.write(parameter.get());
                } catch (IOException ex) {
                    throw new SQLException("Failed to serialize JSON: " + ex.getMessage(), ex);
                }
            }
            ps.setString(i, json);
        } else {
            ps.setString(i, null);
        }
    }

    @Override
    public JsonNodeValue getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return JsonNodeValue.fromDb(rs.getString(columnName));
    }

    @Override
    public JsonNodeValue getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return JsonNodeValue.fromDb(rs.getString(columnIndex));
    }

    @Override
    public JsonNodeValue getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return JsonNodeValue.fromDb(cs.getString(columnIndex));
    }
}