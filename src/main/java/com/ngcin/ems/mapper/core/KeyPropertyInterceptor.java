package com.ngcin.ems.mapper.core;

import com.ngcin.ems.mapper.ref.EntityClassResolver;
import com.ngcin.ems.mapper.ref.TableFieldInfo;
import com.ngcin.ems.mapper.ref.TableInfo;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.Configuration;

import java.util.Map;
import java.util.Properties;

/**
 * Interceptor that dynamically sets the correct keyProperty for auto-generated keys.
 *
 * <p>This interceptor solves the problem where the {@code @Options} annotation in
 * {@code BaseMapper} has a hardcoded {@code keyProperty = "id"}. When an entity's
 * primary key field is named differently (e.g., "userId", "orderId"), MyBatis cannot
 * correctly populate the generated key back into the entity.
 *
 * <p>This interceptor:
 * <ol>
 *   <li>Intercepts INSERT operations</li>
 *   <li>Extracts the entity object from parameters</li>
 *   <li>Resolves entity metadata to find the actual ID field name</li>
 *   <li>Creates a new MappedStatement with the correct keyProperty</li>
 * </ol>
 */
@Intercepts({
    @Signature(type = Executor.class, method = "update",
               args = {MappedStatement.class, Object.class})
})
public class KeyPropertyInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];

        // Only process INSERT operations
        if (ms.getSqlCommandType() != SqlCommandType.INSERT) {
            return invocation.proceed();
        }

        // Extract entity object from parameters
        Object entity = extractEntity(parameter);
        if (entity == null) {
            return invocation.proceed();
        }

        // Resolve entity metadata
        TableInfo tableInfo = EntityClassResolver.resolve(entity.getClass());
        TableFieldInfo idField = tableInfo.idField();

        // Skip if no ID field
        if (idField == null) {
            return invocation.proceed();
        }

        String actualKeyProperty = idField.getProperty();

        // For AUTO type: need useGeneratedKeys with correct keyProperty
        if (idField.idType() == IdType.AUTO) {
            // If default "id" is already correct, no need to modify
            if ("id".equals(actualKeyProperty)) {
                return invocation.proceed();
            }

            // Create new MappedStatement with correct keyProperty
            MappedStatement newMs = createMappedStatementWithKeyProperty(ms, actualKeyProperty);
            args[0] = newMs;
            return invocation.proceed();
        }

        // For UUID and SNOWFLAKE types: disable useGeneratedKeys
        // (ID is generated in application and included in INSERT statement)
        MappedStatement newMs = createMappedStatementWithoutKeyGenerator(ms);
        args[0] = newMs;

        return invocation.proceed();
    }

    /**
     * Extracts the entity object from MyBatis parameter object.
     *
     * @param parameter the parameter object (could be entity directly or a Map)
     * @return the entity object, or null if not found
     */
    private Object extractEntity(Object parameter) {
        // Handle direct entity parameter
        if (!(parameter instanceof Map)) {
            return parameter;
        }

        // Handle MyBatis wrapped Map parameter
        @SuppressWarnings("unchecked")
        Map<String, Object> paramMap = (Map<String, Object>) parameter;
        for (Object value : paramMap.values()) {
            if (value != null && !value.getClass().isPrimitive() && !(value instanceof String)) {
                return value;
            }
        }
        return null;
    }

    /**
     * Creates a new MappedStatement with the specified keyProperty.
     *
     * @param original the original MappedStatement
     * @param keyProperty the correct keyProperty name
     * @return a new MappedStatement with updated keyProperty
     */
    private MappedStatement createMappedStatementWithKeyProperty(
            MappedStatement original, String keyProperty) {

        Configuration config = original.getConfiguration();
        MappedStatement.Builder builder = new MappedStatement.Builder(
            config,
            original.getId(),
            original.getSqlSource(),
            original.getSqlCommandType()
        );

        // Copy original configuration
        builder.resource(original.getResource())
               .fetchSize(original.getFetchSize())
               .statementType(original.getStatementType())
               .keyProperty(keyProperty)           // Set correct keyProperty
               .keyGenerator(Jdbc3KeyGenerator.INSTANCE)  // Enable auto-generated keys
               .databaseId(original.getDatabaseId())
               .lang(original.getLang())
               .resultOrdered(original.isResultOrdered())
               .resultSets(null)  // Not needed for INSERT
               .flushCacheRequired(original.isFlushCacheRequired())
               .useCache(original.isUseCache())
               .cache(original.getCache())
               .timeout(original.getTimeout())
               .parameterMap(original.getParameterMap())
               .resultMaps(original.getResultMaps());

        return builder.build();
    }

    /**
     * Creates a new MappedStatement with keyGenerator disabled.
     * Used for UUID and SNOWFLAKE ID types where the ID is generated in the application.
     *
     * @param original the original MappedStatement
     * @return a new MappedStatement with keyGenerator set to NoKeyGenerator
     */
    private MappedStatement createMappedStatementWithoutKeyGenerator(MappedStatement original) {
        Configuration config = original.getConfiguration();
        MappedStatement.Builder builder = new MappedStatement.Builder(
            config,
            original.getId(),
            original.getSqlSource(),
            original.getSqlCommandType()
        );

        // Copy original configuration but disable key generation
        builder.resource(original.getResource())
               .fetchSize(original.getFetchSize())
               .statementType(original.getStatementType())
               .keyGenerator(org.apache.ibatis.executor.keygen.NoKeyGenerator.INSTANCE)  // Disable auto-generated keys
               .databaseId(original.getDatabaseId())
               .lang(original.getLang())
               .resultOrdered(original.isResultOrdered())
               .resultSets(null)  // Not needed for INSERT
               .flushCacheRequired(original.isFlushCacheRequired())
               .useCache(original.isUseCache())
               .cache(original.getCache())
               .timeout(original.getTimeout())
               .parameterMap(original.getParameterMap())
               .resultMaps(original.getResultMaps());

        return builder.build();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // No properties needed
    }
}
