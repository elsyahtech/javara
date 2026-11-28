package com.elsyah.tech.javara.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;

import com.elsyah.tech.javara.testsupport.PostgresRedisTestSupport;

@SpringBootTest(properties = {
        "app.security.jwt.signing-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.bootstrap-admin.enabled=false"
})
class V8RoleSchemaIntegrationTests extends PostgresRedisTestSupport {
    @Autowired
    private DataSource dataSource;

    @Autowired
    private PublicKeyCredentialUserEntityRepository webAuthnUserRepository;

    @Test
    void webAuthnJdbcRepositoryUsesPrefixedTable() {
        Bytes userId = new Bytes(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        String username = "webauthn-" + UUID.randomUUID();
        var user = ImmutablePublicKeyCredentialUserEntity.builder()
                .id(userId)
                .name(username)
                .displayName("WebAuthn test")
                .build();

        try {
            webAuthnUserRepository.save(user);

            var storedUser = webAuthnUserRepository.findById(userId);
            assertEquals(username, storedUser.getName());
        } finally {
            webAuthnUserRepository.delete(userId);
        }
    }

    @Test
    void v8CreatesPrefixedRoleFoundationWithoutLegacyRoleColumn() {
        String schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
        String qualifiedSchema = "\"" + schema + "\"";
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        try {
            migrate(schema, "8");

            assertEquals(
                    2,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM " + qualifiedSchema
                                    + ".javara_user_roles WHERE role_key IN ('USER', 'ADMIN')",
                            Integer.class));
            assertEquals(
                    7,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM " + qualifiedSchema
                                    + ".javara_permissions",
                            Integer.class));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM " + qualifiedSchema
                                    + ".javara_user_role_assignments",
                            Integer.class));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM information_schema.columns
                            WHERE table_schema = ?
                              AND table_name = 'javara_users'
                              AND column_name = 'role'
                            """,
                            Integer.class,
                            schema));
            for (String legacyName : List.of(
                    "admin_password_reset_audit",
                    "email_verification_tokens",
                    "mfa_challenges",
                    "oauth2_authorization",
                    "oauth2_authorization_consent",
                    "oauth2_registered_client",
                    "password_reset_tokens",
                    "permissions",
                    "refresh_tokens",
                    "tenant_memberships",
                    "tenants",
                    "user_compromised_password_hashes",
                    "user_credentials",
                    "user_entities",
                    "user_mfa_methods",
                    "user_role_assignments",
                    "user_role_details",
                    "user_roles",
                    "users")) {
                assertNull(
                        jdbc.queryForObject(
                                "SELECT to_regclass(?)",
                                String.class,
                                schema + "." + legacyName),
                        "Unprefixed table should not remain: " + legacyName);
            }
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + qualifiedSchema + " CASCADE");
        }
    }

    @Test
    void developmentRoleSeedIsRepeatableAndDoesNotAssignTheRole() throws Exception {
        String schema = "role_seed_test_" + UUID.randomUUID().toString().replace("-", "");
        String qualifiedSchema = "\"" + schema + "\"";
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        FileSystemResource seedFile = new FileSystemResource(
                Path.of("examples/postgres/authorization/seed-dev.sql"));

        try {
            migrate(schema, "8");
            try (var connection = dataSource.getConnection()) {
                connection.setSchema(schema);
                ScriptUtils.executeSqlScript(connection, seedFile);
                ScriptUtils.executeSqlScript(connection, seedFile);
            }

            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM " + qualifiedSchema
                                    + ".javara_user_roles WHERE role_key = 'ACCOUNT_READER'",
                            Integer.class));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM %s.javara_user_role_details detail
                            JOIN %s.javara_user_roles role_definition
                              ON role_definition.id = detail.role_id
                            WHERE role_definition.role_key = 'ACCOUNT_READER'
                              AND detail.permission_key = 'users:read'
                            """.formatted(qualifiedSchema, qualifiedSchema),
                            Integer.class));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM %s.javara_user_role_assignments assignment
                            JOIN %s.javara_user_roles role_definition
                              ON role_definition.id = assignment.role_id
                            WHERE role_definition.role_key = 'ACCOUNT_READER'
                            """.formatted(qualifiedSchema, qualifiedSchema),
                            Integer.class));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + qualifiedSchema + " CASCADE");
        }
    }

    private void migrate(String schema, String targetVersion) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(targetVersion))
                .load()
                .migrate();
    }
}
