package com.elsyah.tech.javara.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public abstract class PostgresRedisTestSupport {
    private static final String POSTGRES_URL = "JAVARA_TEST_POSTGRES_URL";
    private static final String POSTGRES_USERNAME = "JAVARA_TEST_POSTGRES_USERNAME";
    private static final String POSTGRES_PASSWORD = "JAVARA_TEST_POSTGRES_PASSWORD";
    private static final String REDIS_HOST = "JAVARA_TEST_REDIS_HOST";
    private static final String REDIS_PORT = "JAVARA_TEST_REDIS_PORT";
    private static final String REDIS_USERNAME = "JAVARA_TEST_REDIS_USERNAME";
    private static final String REDIS_PASSWORD = "JAVARA_TEST_REDIS_PASSWORD";
    private static final List<String> REQUIRED_EXTERNAL_SETTINGS =
            List.of(POSTGRES_URL, POSTGRES_USERNAME, POSTGRES_PASSWORD, REDIS_HOST, REDIS_PORT);
    private static final List<String> ALL_EXTERNAL_SETTINGS = List.of(
            POSTGRES_URL,
            POSTGRES_USERNAME,
            POSTGRES_PASSWORD,
            REDIS_HOST,
            REDIS_PORT,
            REDIS_USERNAME,
            REDIS_PASSWORD);

    private static final ExternalServices EXTERNAL_SERVICES = ExternalServices.fromEnvironment();
    private static final String CONTAINER_POSTGRES_PASSWORD = UUID.randomUUID().toString();
    private static final GenericContainer<?> POSTGRES = EXTERNAL_SERVICES == null
            ? postgresContainer()
            : null;
    private static final GenericContainer<?> REDIS = EXTERNAL_SERVICES == null
            ? redisContainer()
            : null;

    static {
        if (EXTERNAL_SERVICES == null) {
            POSTGRES.start();
            REDIS.start();
        }
    }

    @DynamicPropertySource
    protected static void registerTestServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresRedisTestSupport::postgresUrl);
        registry.add("spring.datasource.username", PostgresRedisTestSupport::postgresUsername);
        registry.add("spring.datasource.password", PostgresRedisTestSupport::postgresPassword);
        registry.add("spring.data.redis.host", PostgresRedisTestSupport::redisHost);
        registry.add("spring.data.redis.port", PostgresRedisTestSupport::redisPort);
        registry.add("spring.data.redis.username", PostgresRedisTestSupport::redisUsername);
        registry.add("spring.data.redis.password", PostgresRedisTestSupport::redisPassword);
    }

    private static GenericContainer<?> postgresContainer() {
        return new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withEnv("POSTGRES_DB", "javara_test")
                .withEnv("POSTGRES_USER", "javara_test")
                .withEnv("POSTGRES_PASSWORD", CONTAINER_POSTGRES_PASSWORD)
                .withExposedPorts(5432)
                .waitingFor(Wait.forSuccessfulCommand(
                        "pg_isready -U javara_test -d javara_test"));
    }

    private static GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .waitingFor(Wait.forListeningPort());
    }

    private static String postgresUrl() {
        return EXTERNAL_SERVICES == null
                ? "jdbc:postgresql://%s:%d/javara_test".formatted(
                        POSTGRES.getHost(), POSTGRES.getMappedPort(5432))
                : EXTERNAL_SERVICES.postgresUrl();
    }

    private static String postgresUsername() {
        return EXTERNAL_SERVICES == null
                ? "javara_test"
                : EXTERNAL_SERVICES.postgresUsername();
    }

    private static String postgresPassword() {
        return EXTERNAL_SERVICES == null
                ? CONTAINER_POSTGRES_PASSWORD
                : EXTERNAL_SERVICES.postgresPassword();
    }

    private static String redisHost() {
        return EXTERNAL_SERVICES == null
                ? REDIS.getHost()
                : EXTERNAL_SERVICES.redisHost();
    }

    private static Integer redisPort() {
        return EXTERNAL_SERVICES == null
                ? REDIS.getMappedPort(6379)
                : EXTERNAL_SERVICES.redisPort();
    }

    private static String redisUsername() {
        return EXTERNAL_SERVICES == null
                ? ""
                : EXTERNAL_SERVICES.redisUsername();
    }

    private static String redisPassword() {
        return EXTERNAL_SERVICES == null
                ? ""
                : EXTERNAL_SERVICES.redisPassword();
    }

    private record ExternalServices(
            String postgresUrl,
            String postgresUsername,
            String postgresPassword,
            String redisHost,
            int redisPort,
            String redisUsername,
            String redisPassword) {
        private static ExternalServices fromEnvironment() {
            Map<String, String> environment = System.getenv();
            boolean configured = ALL_EXTERNAL_SETTINGS.stream()
                    .anyMatch(environment::containsKey);
            if (!configured) {
                return null;
            }

            List<String> missing = new ArrayList<>();
            for (String setting : REQUIRED_EXTERNAL_SETTINGS) {
                String value = environment.get(setting);
                if (value == null || value.isBlank() && !setting.equals(POSTGRES_PASSWORD)) {
                    missing.add(setting);
                }
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException(
                        "Configure all required JAVARA_TEST_* service settings or unset the partial "
                                + "configuration to use Testcontainers. Missing or blank: "
                                + String.join(", ", missing));
            }

            String jdbcUrl = environment.get(POSTGRES_URL);
            if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
                throw new IllegalArgumentException(
                        "JAVARA_TEST_POSTGRES_URL must be a PostgreSQL JDBC URL");
            }

            int port;
            try {
                port = Integer.parseInt(environment.get(REDIS_PORT));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "JAVARA_TEST_REDIS_PORT must be a number from 1 to 65535", exception);
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException(
                        "JAVARA_TEST_REDIS_PORT must be a number from 1 to 65535");
            }

            return new ExternalServices(
                    jdbcUrl,
                    environment.get(POSTGRES_USERNAME),
                    environment.get(POSTGRES_PASSWORD),
                    environment.get(REDIS_HOST),
                    port,
                    environment.getOrDefault(REDIS_USERNAME, ""),
                    environment.getOrDefault(REDIS_PASSWORD, ""));
        }
    }
}
