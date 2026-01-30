package com.ngcin.ems.mapper.json;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis TypeHandler for Jackson TreeNode types.
 * Returns MissingNode instead of null for empty/null values.
 *
 * @see TreeNode
 * @see JsonNode
 */
@MappedTypes({JsonNode.class, TreeNode.class, ArrayNode.class, ObjectNode.class})
public class TreeNodeTypeHandler extends BaseTypeHandler<TreeNode> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
            TreeNode parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, ReaderWriter.write(parameter));
        } catch (IOException ex) {
            throw new SQLException("Failed to serialize JSON: " + ex.getMessage(), ex);
        }
    }

    @Override
    public TreeNode getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return fromString(rs.getString(columnName));
    }

    @Override
    public TreeNode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return fromString(rs.getString(columnIndex));
    }

    @Override
    public TreeNode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return fromString(cs.getString(columnIndex));
    }

    private TreeNode fromString(String source) {
        if (source == null || source.isEmpty()) {
            return MissingNode.getInstance();
        }
        return new TreeNodeLazyWrapper(source);
    }
}