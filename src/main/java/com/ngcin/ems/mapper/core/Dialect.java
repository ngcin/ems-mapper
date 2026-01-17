package com.ngcin.ems.mapper.core;

public enum Dialect {
    MYSQL {
        @Override
        public String buildPaginationSql(String sql, long current, long size) {
            long offset = (current - 1) * size;
            return sql + " LIMIT " + offset + ", " + size;
        }

        @Override
        public String buildCountSql(String sql) {
            return "SELECT COUNT(*) FROM (" + sql + ") AS _count_table";
        }
    },
    ORACLE {
        @Override
        public String buildPaginationSql(String sql, long current, long size) {
            long offset = (current - 1) * size;
            long endRow = current * size;
            return "SELECT * FROM (SELECT ROWNUM AS rn, t.* FROM (" + sql + ") t WHERE ROWNUM <= " + endRow + ") WHERE rn > " + offset;
        }

        @Override
        public String buildCountSql(String sql) {
            return "SELECT COUNT(*) FROM (" + sql + ")";
        }

    },
    POSTGRESQL {
        @Override
        public String buildPaginationSql(String sql, long current, long size) {
            long offset = (current - 1) * size;
            return sql + " LIMIT " + size + " OFFSET " + offset;
        }

        @Override
        public String buildCountSql(String sql) {
            return "SELECT COUNT(*) FROM (" + sql + ") AS _count_table";
        }

    };

    /**
     * 获取分页sql
     *
     * @param sql     原始sql
     * @param current 开始页码
     * @param size    当前页展示数量
     * @return 分页sql
     */
    public abstract String buildPaginationSql(String sql, long current, long size);

    /**
     * 获取统计的sql
     *
     * @param sql 原始sql
     * @return 方言对应的sql
     */
    public abstract String buildCountSql(final String sql);

    /**
     * 获取统计的sql
     *
     * @param removeSelect 是否移除select与order by 部分
     */
    public String getCountPrefixSQL(boolean removeSelect) {
        return " SELECT  COUNT(0) FROM ( ";
    }

    /**
     * 获取统计的sql
     *
     * @param removeSelect 是否移除select与order by 部分
     */
    public String getCountSuffixSQL(boolean removeSelect) {
        return " ) as t";
    }

    /**
     * 获取统计的sql
     *
     * @param sql          原始sql
     * @param removeSelect 是否移除select与order by 部分
     * @return 方言对应的sql
     */
    public String buildCountSql(final String sql, boolean removeSelect) {
        if (removeSelect) {
            return buildCountSql(sql);
        }
        return "";
    }
}