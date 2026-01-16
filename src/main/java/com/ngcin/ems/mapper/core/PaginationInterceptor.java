package com.ngcin.ems.mapper.core;

import com.ngcin.ems.mapper.IPage;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Pagination interceptor that automatically adds COUNT query and LIMIT clause.
 *
 * <p>For SELECT queries containing an {@link IPage} parameter, this interceptor:
 * <ol>
 *   <li>Extracts the Page object from method parameters</li>
 *   <li>Executes COUNT(*) query to get total records</li>
 *   <li>Modifies SQL with dialect-specific pagination syntax</li>
 *   <li>Sets the total count on the Page object</li>
 * </ol>
 *
 * <p>Supported databases: MySQL, PostgreSQL, Oracle, H2
 */
@Intercepts({
    @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class PaginationInterceptor implements Interceptor {

    /** Dialect type for SQL generation. volatile for thread safety. */
    private volatile Dialect dialect;

    /** When true, returns empty result if current page exceeds total pages. */
    private boolean overflow = false;

    /**
     * Intercepts Executor.query/update to apply pagination.
     *
     * <p>Process flow:
     * <ol>
     *   <li>Extract MappedStatement and parameters</li>
     *   <li>Check if it's a SELECT statement</li>
     *   <li>Extract IPage from parameters</li>
     *   <li>Execute COUNT query</li>
     *   <li>Handle overflow (current page &gt; total pages)</li>
     *   <li>Modify SQL with LIMIT/OFFSET</li>
     * </ol>
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];

        SqlCommandType sqlCommandType = ms.getSqlCommandType();

        if (sqlCommandType == SqlCommandType.SELECT) {
            Object parameterObject = parameter;
            IPage<?> page = extractPage(parameterObject, null);

            if (page != null) {
                BoundSql boundSql = ms.getBoundSql(parameter);
                String originalSql = boundSql.getSql().trim();

                Executor executor = (Executor) invocation.getTarget();
                Configuration configuration = ms.getConfiguration();
                Dialect dialect = getDialect();

                String countSql = dialect.buildCountSql(originalSql);
                BoundSql countBoundSql = newBoundSql(configuration, countSql, parameterObject, boundSql);

                long total = executeCount(executor, countBoundSql, ms);
                if (page instanceof Page<?> p) {
                    p.setTotal(total);
                }

                if (total == 0) {
                    if (page instanceof Page<?> p) {
                        p.setRecords(Collections.emptyList());
                    }
                    return Collections.emptyList();
                }

                if (overflow && page.getCurrent() > 1) {
                    long pages = (total + page.getSize() - 1) / page.getSize();
                    if (page.getCurrent() > pages) {
                        if (page instanceof Page<?> p) {
                            p.setRecords(Collections.emptyList());
                        }
                        return Collections.emptyList();
                    }
                }

                String pagedSql = dialect.buildPaginationSql(originalSql, page.getCurrent(), page.getSize());
                BoundSql pagedBoundSql = newBoundSql(configuration, pagedSql, parameterObject, boundSql);

                // Create new MappedStatement for paged query
                String pagedMsId = ms.getId() + "_PAGE";
                MappedStatement pagedMs = createCountMappedStatement(configuration, pagedMsId, pagedBoundSql);

                // Replace args for paged query
                args[0] = pagedMs;
            }
        }

        return invocation.proceed();
    }

    protected Object getParameterObject(Object parameter) {
        if (parameter instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) parameter;
            if (map.containsKey(MapperConsts.ENTITY)) return map.get(MapperConsts.ENTITY);
            if (map.containsKey(MapperConsts.ENTITY_WHERE)) return map.get(MapperConsts.ENTITY_WHERE);
            if (map.containsKey(MapperConsts.PARAM_1)) return map.get(MapperConsts.PARAM_1);
            for (Object value : map.values()) {
                if (value != null) return value;
            }
        }
        return parameter;
    }

    /**
     * Extracts IPage from method parameters.
     *
     * <p>Searches in order:
     * <ol>
     *   <li>Direct IPage parameter</li>
     *   <li>Map entry with key "page"</li>
     *   <li>Map entry with key "param1"</li>
     *   <li>Any Map value that is an IPage</li>
     *   <li>RowBounds if it's an IPage</li>
     * </ol>
     *
     * @param parameterObject the parameter from mapper method
     * @param rowBounds MyBatis RowBounds (legacy, for compatibility)
     * @return extracted IPage, or null if not found
     */
    protected IPage<?> extractPage(Object parameterObject, RowBounds rowBounds) {
        if (parameterObject instanceof IPage<?> page) return page;
        if (parameterObject instanceof Map<?, ?> map) {
            if (map.containsKey(MapperConsts.PAGE)) return (IPage<?>) map.get(MapperConsts.PAGE);
            if (map.containsKey(MapperConsts.PARAM_1) && map.get(MapperConsts.PARAM_1) instanceof IPage<?> page) return page;
            for (Object value : map.values()) {
                if (value instanceof IPage<?> page) return page;
            }
        }
        if (rowBounds instanceof IPage<?> page) return page;
        return null;
    }

    /**
     * Creates a new BoundSql with the given SQL, copying parameter mappings and additional parameters.
     *
     * @param configuration MyBatis configuration
     * @param sql the new SQL statement
     * @param parameterObject the parameter object
     * @param original the original BoundSql to copy from
     * @return new BoundSql with updated SQL
     */
    protected BoundSql newBoundSql(Configuration configuration, String sql,
                                   Object parameterObject, BoundSql original) {
        List<ParameterMapping> mappings = new ArrayList<>(original.getParameterMappings());
        BoundSql boundSql = new BoundSql(configuration, sql, mappings, parameterObject);

        try {
            Field additionalParamsField = BoundSql.class.getDeclaredField("additionalParameters");
            additionalParamsField.setAccessible(true);
            Map<String, Object> additionalParams = (Map<String, Object>) additionalParamsField.get(original);
            Field targetField = BoundSql.class.getDeclaredField("additionalParameters");
            targetField.setAccessible(true);
            targetField.set(boundSql, new HashMap<>(additionalParams));
        } catch (Exception e) {
            // Preserve additional parameters is optional, continue without them
        }

        MetaObject sourceMeta = configurationMetaObject(original);
        MetaObject targetMeta = configurationMetaObject(boundSql);

        for (ParameterMapping mapping : original.getParameterMappings()) {
            String property = mapping.getProperty();
            if (property != null && sourceMeta.hasGetter(property)) {
                Object value = sourceMeta.getValue(property);
                targetMeta.setValue(property, value);
            }
        }

        return boundSql;
    }

    /**
     * Executes COUNT query using a new MappedStatement.
     *
     * <p>This method creates a new MappedStatement for the COUNT query,
     * avoiding modification of shared BoundSql objects for thread safety.
     *
     * @param executor the MyBatis executor
     * @param countBoundSql BoundSql containing the COUNT SQL
     * @param originalMs the original MappedStatement
     * @return total count of records
     * @throws RuntimeException if query fails
     */
    @SuppressWarnings("unchecked")
    protected long executeCount(Executor executor, BoundSql countBoundSql, MappedStatement originalMs) {
        try {
            // Create a new MappedStatement to avoid modifying the original
            String countMsId = originalMs.getId() + "_COUNT";
            MappedStatement countMs = createCountMappedStatement(
                originalMs.getConfiguration(),
                countMsId,
                countBoundSql
            );

            List<Object> results = (List<Object>) executor.query(
                    countMs, countBoundSql.getParameterObject(), RowBounds.DEFAULT, null);

            if (results != null && !results.isEmpty()) {
                Object result = results.get(0);
                if (result instanceof Number) {
                    return ((Number) result).longValue();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute count query", e);
        }
        return 0;
    }

    protected MappedStatement createCountMappedStatement(Configuration config, String id, BoundSql boundSql) {
        String countSql = "SELECT COUNT(*) AS total FROM (" + boundSql.getSql() + ") AS _t";
        List<ParameterMapping> newMappings = new ArrayList<>(boundSql.getParameterMappings());
        SqlSource sqlSource = new StaticSqlSource(config, countSql, newMappings);

        MappedStatement.Builder builder = new MappedStatement.Builder(config, id, sqlSource, SqlCommandType.SELECT);
        builder.resource("internal")
                .fetchSize(-1)
                .statementType(org.apache.ibatis.mapping.StatementType.PREPARED)
                .keyGenerator(org.apache.ibatis.executor.keygen.NoKeyGenerator.INSTANCE)
                .keyProperty(null).keyColumn(null)
                .databaseId(null).lang(null)
                .resultOrdered(false).resultSets(null)
                .flushCacheRequired(true).useCache(false)
                .cache(null);

        MappedStatement ms = builder.build();

        try {
            java.lang.reflect.Field resultTypeField = MappedStatement.class.getDeclaredField("resultType");
            resultTypeField.setAccessible(true);
            resultTypeField.set(ms, Long.class);

            java.lang.reflect.Field resultMapIdField = MappedStatement.class.getDeclaredField("resultMapId");
            resultMapIdField.setAccessible(true);
            resultMapIdField.set(ms, "");
        } catch (Exception e) {
        }

        return ms;
    }

    protected MetaObject configurationMetaObject(Object object) {
        return org.apache.ibatis.reflection.SystemMetaObject.forObject(object);
    }

    /**
     * Gets the dialect instance for the configured database type.
     *
     * @return the Dialect implementation
     */
    protected Dialect getDialect() {
        return this.dialect;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public synchronized void setProperties(Properties properties) {
        String dialectName = properties.getProperty("dialectType", "mysql");
        try {
            this.dialect = Dialect.valueOf(dialectName.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.dialect = Dialect.MYSQL;
        }
        this.overflow = Boolean.parseBoolean(properties.getProperty("overflow", "false"));
    }
}
