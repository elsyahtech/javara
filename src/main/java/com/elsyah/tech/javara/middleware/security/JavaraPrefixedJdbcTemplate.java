package com.elsyah.tech.javara.middleware.security;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

final class JavaraPrefixedJdbcTemplate extends JdbcTemplate {
    private static final Map<String, String> FRAMEWORK_TABLES = Map.of(
            "oauth2_authorization", "javara_oauth2_authorization",
            "oauth2_authorization_consent", "javara_oauth2_authorization_consent",
            "oauth2_registered_client", "javara_oauth2_registered_client",
            "user_credentials", "javara_user_credentials",
            "user_entities", "javara_user_entities");
    private static final Map<String, Pattern> FRAMEWORK_TABLE_PATTERNS = FRAMEWORK_TABLES.entrySet()
            .stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    entry -> Pattern.compile("\\b" + entry.getKey() + "\\b")));

    JavaraPrefixedJdbcTemplate(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public int update(String sql, PreparedStatementSetter pss) {
        return super.update(prefixFrameworkTables(sql), pss);
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
        return super.query(prefixFrameworkTables(sql), rowMapper, args);
    }

    @Override
    public <T> List<T> query(
            String sql, PreparedStatementSetter pss, RowMapper<T> rowMapper) {
        return super.query(prefixFrameworkTables(sql), pss, rowMapper);
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        return super.queryForObject(prefixFrameworkTables(sql), requiredType, args);
    }

    @Override
    public <T> T execute(ConnectionCallback<T> action) throws DataAccessException {
        return super.execute((ConnectionCallback<T>) connection ->
                action.doInConnection(connectionWithPrefixedTableMetadata(connection)));
    }

    private static String prefixFrameworkTables(String sql) {
        String prefixedSql = sql;
        for (Map.Entry<String, Pattern> entry : FRAMEWORK_TABLE_PATTERNS.entrySet()) {
            prefixedSql = entry.getValue()
                    .matcher(prefixedSql)
                    .replaceAll(FRAMEWORK_TABLES.get(entry.getKey()));
        }
        return prefixedSql;
    }

    private static Connection connectionWithPrefixedTableMetadata(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    Object result = invoke(method, connection, args);
                    if (method.getName().equals("getMetaData") && result instanceof DatabaseMetaData metadata) {
                        return metadataWithPrefixedTables(metadata);
                    }
                    return result;
                });
    }

    private static DatabaseMetaData metadataWithPrefixedTables(DatabaseMetaData metadata) {
        return (DatabaseMetaData) Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(),
                new Class<?>[] {DatabaseMetaData.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getColumns")
                            && args != null
                            && args.length > 2
                            && args[2] instanceof String tableName) {
                        args = args.clone();
                        args[2] = FRAMEWORK_TABLES.getOrDefault(tableName, tableName);
                    }
                    return invoke(method, metadata, args);
                });
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
