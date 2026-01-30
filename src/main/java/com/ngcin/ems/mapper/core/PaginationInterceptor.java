package com.ngcin.ems.mapper.core;

import com.ngcin.ems.mapper.IPage;
import com.ngcin.ems.mapper.PageHelper;
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

    /** Cached Field for BoundSql.additionalParameters to avoid repeated reflection */
    private static final Field ADDITIONAL_PARAMETERS_FIELD;

    static {
        Field field = null;
        try {
            field = BoundSql.class.getDeclaredField("additionalParameters");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            // Field not available, will skip copying additional parameters
        }
        ADDITIONAL_PARAMETERS_FIELD = field;
    }

    /** Dialect type for SQL generation. volatile for thread safety. */
    private volatile Dialect dialect = Dialect.MYSQL;

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
            IPage<?> page = extractPage(parameter, null);

            if (page != null) {
                // Validate page parameters
                if (page.getSize() <= 0) {
                    throw new IllegalArgumentException("Page size must be greater than 0, but was: " + page.getSize());
                }
                if (page.getCurrent() <= 0) {
                    throw new IllegalArgumentException("Current page must be greater than 0, but was: " + page.getCurrent());
                }

                // Check once if page is a Page instance to avoid repeated instanceof checks
                Page<?> pageImpl = (page instanceof Page<?>) ? (Page<?>) page : null;

                BoundSql boundSql = ms.getBoundSql(parameter);
                String originalSql = boundSql.getSql().trim();

                Executor executor = (Executor) invocation.getTarget();
                Configuration configuration = ms.getConfiguration();
                Dialect dialect = getDialect();

                // Execute COUNT query
                String countSql = dialect.buildCountSql(originalSql);
                BoundSql countBoundSql = newBoundSql(configuration, countSql, parameter, boundSql);

                long total = executeCount(executor, countBoundSql, ms);
                if (pageImpl != null) {
                    pageImpl.setTotal(total);
                }

                // Handle empty results
                if (total == 0) {
                    if (pageImpl != null) {
                        pageImpl.records(Collections.emptyList());
                    }
                    return Collections.emptyList();
                }

                // Handle overflow
                if (overflow && page.getCurrent() > 1) {
                    long pages = (total + page.getSize() - 1) / page.getSize();
                    if (page.getCurrent() > pages) {
                        if (pageImpl != null) {
                            pageImpl.records(Collections.emptyList());
                        }
                        return Collections.emptyList();
                    }
                }

                // Build pagination SQL
                String pagedSql = dialect.buildPaginationSql(originalSql, page.getCurrent(), page.getSize());
                BoundSql pagedBoundSql = newBoundSql(configuration, pagedSql, parameter, boundSql);

                // Create or get cached MappedStatement for paged query
                String pagedMsId = ms.getId() + "_PAGE";
                MappedStatement pagedMs = createPagedMappedStatement(ms, pagedMsId, pagedBoundSql);

                // Replace args for paged query
                args[0] = pagedMs;
            }
        }

        return invocation.proceed();
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

        // 从 ThreadLocal 获取分页信息 (PageHelper 方式)
        return PageHelper.getLocalPage();
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

        // Use cached field to avoid repeated reflection
        if (ADDITIONAL_PARAMETERS_FIELD != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> additionalParams = (Map<String, Object>) ADDITIONAL_PARAMETERS_FIELD.get(original);
                if (additionalParams != null) {
                    ADDITIONAL_PARAMETERS_FIELD.set(boundSql, new HashMap<>(additionalParams));
                }
            } catch (IllegalAccessException e) {
                // Failed to copy additional parameters, continue without them
            }
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
            // Create a unique ID for count MappedStatement
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
            throw new RuntimeException("Failed to execute count query for " + originalMs.getId() + ": " + e.getMessage(), e);
        }
        return 0;
    }

    /**
     * Creates a new MappedStatement for paged query, preserving original result mappings.
     */
    protected MappedStatement createPagedMappedStatement(MappedStatement originalMs, String id, BoundSql boundSql) {
        SqlSource sqlSource = new StaticSqlSource(originalMs.getConfiguration(), boundSql.getSql(), boundSql.getParameterMappings());

        MappedStatement.Builder builder = new MappedStatement.Builder(
            originalMs.getConfiguration(),
            id,
            sqlSource,
            originalMs.getSqlCommandType()
        );

        builder.resource(originalMs.getResource())
                .fetchSize(originalMs.getFetchSize())
                .timeout(originalMs.getTimeout())
                .statementType(originalMs.getStatementType())
                .keyGenerator(originalMs.getKeyGenerator())
                .keyProperty(originalMs.getKeyProperties() == null ? null : String.join(",", originalMs.getKeyProperties()))
                .keyColumn(originalMs.getKeyColumns() == null ? null : String.join(",", originalMs.getKeyColumns()))
                .databaseId(originalMs.getDatabaseId())
                .lang(originalMs.getLang())
                .resultOrdered(originalMs.isResultOrdered())
                .resultSets(originalMs.getResultSets() == null ? null : String.join(",", originalMs.getResultSets()))
                .resultMaps(originalMs.getResultMaps())
                .flushCacheRequired(originalMs.isFlushCacheRequired())
                .useCache(originalMs.isUseCache())
                .cache(originalMs.getCache());

        return builder.build();
    }

    protected MappedStatement createCountMappedStatement(Configuration config, String id, BoundSql boundSql) {
        // boundSql already contains the COUNT SQL from Dialect.buildCountSql()
        SqlSource sqlSource = new StaticSqlSource(config, boundSql.getSql(), boundSql.getParameterMappings());

        // Create a simple inline result map for Long type
        List<org.apache.ibatis.mapping.ResultMapping> resultMappings = new ArrayList<>();
        org.apache.ibatis.mapping.ResultMap.Builder resultMapBuilder =
            new org.apache.ibatis.mapping.ResultMap.Builder(
                config,
                id + "-Inline",
                Long.class,
                resultMappings,
                true  // autoMapping
            );
        org.apache.ibatis.mapping.ResultMap resultMap = resultMapBuilder.build();
        List<org.apache.ibatis.mapping.ResultMap> resultMaps = new ArrayList<>();
        resultMaps.add(resultMap);

        MappedStatement.Builder builder = new MappedStatement.Builder(config, id, sqlSource, SqlCommandType.SELECT);
        builder.resource("internal")
                .fetchSize(null)
                .timeout(null)
                .statementType(org.apache.ibatis.mapping.StatementType.PREPARED)
                .keyGenerator(org.apache.ibatis.executor.keygen.NoKeyGenerator.INSTANCE)
                .keyProperty(null)
                .keyColumn(null)
                .databaseId(null)
                .lang(config.getDefaultScriptingLanguageInstance())
                .resultOrdered(false)
                .resultSets(null)
                .resultMaps(resultMaps)
                .flushCacheRequired(false)
                .useCache(false)
                .cache(null);

        return builder.build();
    }

    protected MetaObject configurationMetaObject(Object object) {
        return org.apache.ibatis.reflection.SystemMetaObject.forObject(object);
    }

    /**
     * Gets the dialect instance for the configured database type.
     *
     * @return the Dialect implementation
     * @throws IllegalStateException if dialect is not configured
     */
    protected Dialect getDialect() {
        if (this.dialect == null) {
            throw new IllegalStateException("Dialect is not configured. Please set dialectType property in plugin configuration.");
        }
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
