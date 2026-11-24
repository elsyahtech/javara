package com.elsyah.tech.javara.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.repository.auth.RefreshTokenRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.request.auth.LoginRequest;
import com.elsyah.tech.javara.request.user.UserRequest;
import com.elsyah.tech.javara.request.user.UserUpdateRequest;
import com.elsyah.tech.javara.service.auth.AuthService;
import com.elsyah.tech.javara.service.auth.AuthServiceResult;
import com.elsyah.tech.javara.service.common.ServiceResult;
import com.elsyah.tech.javara.service.user.UserService;
import com.elsyah.tech.javara.testsupport.PostgresRedisTestSupport;
import com.elsyah.tech.javara.utils.security.PasswordHashService;

@SpringBootTest
@TestPropertySource(properties = {
        "app.security.jwt.signing-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.jwt.transport=BEARER",
        "app.security.bootstrap-admin.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class UserConcurrentIntegrationTests extends PostgresRedisTestSupport {

    private static ExecutorService executor;

    @Autowired
    private UserRepository repository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHashService passwordHashService;

    @BeforeAll
    static void startExecutor() {
        executor = Executors.newFixedThreadPool(8);
    }

    @AfterAll
    static void stopExecutor() {
        executor.shutdownNow();
    }

    @BeforeEach
    void clearUsers() {
        jdbcTemplate.update("""
                DELETE FROM refresh_tokens
                WHERE user_id IN (
                    SELECT id FROM users
                    WHERE email LIKE 'concurrent-%'
                       OR email LIKE 'before-%'
                       OR email LIKE 'delete-%'
                       OR email LIKE 'mixed-%'
                       OR email LIKE 'password-change-%'
                )
                """);
        jdbcTemplate.update(
                "DELETE FROM users WHERE email LIKE 'concurrent-%'"
                        + " OR email LIKE 'before-%'"
                        + " OR email LIKE 'delete-%'"
                        + " OR email LIKE 'mixed-%'"
                        + " OR email LIKE 'password-change-%'");
        repository.flush();
    }

    // Verifies that simultaneous create requests allow exactly one active duplicate email.
    @Test
    void concurrentCreateAllowsOnlyOneActiveDuplicateEmail() throws Exception {
        String email = "concurrent-" + UUID.randomUUID() + "@example.com";
        List<Callable<HttpStatus>> requests = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            requests.add(() -> {
                try {
                    ServiceResult<UserEntity> result = userService.createUser(
                            new UserRequest(email, "password123", true));
                    return result.status();
                } catch (DataIntegrityViolationException exception) {
                    return HttpStatus.CONFLICT;
                }
            });
        }

        List<Future<HttpStatus>> futures = executor.invokeAll(requests)
                .stream()
                .map(future -> (Future<HttpStatus>) future)
                .toList();

        List<HttpStatus> statuses = futures.stream()
                .map(this::getFutureStatus)
                .toList();

        assertThat(statuses.stream().filter(HttpStatus.CREATED::equals).count())
                .isEqualTo(1);
        assertThat(statuses.stream().filter(HttpStatus.CONFLICT::equals).count())
                .isEqualTo(7);
        assertThat(repository.findByEmail(email)).isPresent();
    }

    // Verifies that concurrent updates are serialized and leave one valid final value.
    @Test
    void concurrentUpdateSerializesMutationsForOneUser() throws Exception {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("before-" + UUID.randomUUID() + "@example.com");
        user.setPassword("initial-password");
        user.setIsActive(true);
        repository.saveAndFlush(user);

        Callable<HttpStatus> firstUpdate = () -> userService.updateUser(
                user.getId(),
                new UserUpdateRequest(user.getEmail(), false)).status();
        Callable<HttpStatus> secondUpdate = () -> userService.updateUser(
                user.getId(),
                new UserUpdateRequest(user.getEmail(), true)).status();

        List<Future<HttpStatus>> futures = executor.invokeAll(
                List.of(firstUpdate, secondUpdate))
                .stream()
                .map(future -> (Future<HttpStatus>) future)
                .toList();

        assertThat(futures.stream().map(this::getFutureStatus).toList())
                .allMatch(status -> status == HttpStatus.OK);

        assertThat(repository.findById(user.getId()).orElseThrow().getIsActive()).isNotNull();
    }

    @Test
    void changingPasswordRevokesExistingRefreshTokens() {
        String email = "password-change-" + UUID.randomUUID() + "@example.com";
        ServiceResult<UserEntity> created = userService.createUser(
                new UserRequest(email, "old-password-123", true));
        AuthServiceResult login = authService.login(
                new LoginRequest(email, "old-password-123"));

        assertThat(login.status()).isEqualTo(HttpStatus.OK);
        assertThat(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(
                created.data().getId())).hasSize(1);

        ServiceResult<Void> updated = userService.changePassword(
                created.data().getId(),
                "old-password-123",
                "new-password-123");

        assertThat(updated.status()).isEqualTo(HttpStatus.OK);
        assertThat(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(
                created.data().getId())).isEmpty();
        assertThat(authService.refresh(login.refreshToken()).status())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void simultaneousPasswordChangeAndRefreshLeaveNoActiveRefreshTokens() throws Exception {
        String email = "password-change-" + UUID.randomUUID() + "@example.com";
        UserEntity user = userService.createUser(
                new UserRequest(email, "old-password-123", true)).data();
        AuthServiceResult login = authService.login(
                new LoginRequest(email, "old-password-123"));
        CountDownLatch start = new CountDownLatch(1);

        Future<AuthServiceResult> refresh = executor.submit(() -> {
            start.await();
            return authService.refresh(login.refreshToken());
        });
        Future<HttpStatus> update = executor.submit(() -> {
            start.await();
            return userService.changePassword(
                    user.getId(),
                "old-password-123",
                "new-password-123").status();
        });
        start.countDown();

        AuthServiceResult refreshResult = refresh.get();
        assertThat(refreshResult.status()).isIn(HttpStatus.OK, HttpStatus.UNAUTHORIZED);
        HttpStatus updateStatus = update.get();
        assertThat(updateStatus).isEqualTo(HttpStatus.OK);
        assertThat(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId()))
                .as("refresh status: %s, update status: %s",
                        refreshResult.status(),
                        updateStatus)
                .isEmpty();
    }

    // Verifies that concurrent delete requests do not delete or mutate beyond one success.
    @Test
    void concurrentDeleteReturnsOneSuccessAndOneNotFound() throws Exception {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("delete-" + UUID.randomUUID() + "@example.com");
        user.setPassword("password123");
        user.setIsActive(true);
        repository.saveAndFlush(user);

        Callable<HttpStatus> firstDelete = () ->
                userService.deleteUser(user.getId()).status();
        Callable<HttpStatus> secondDelete = () ->
                userService.deleteUser(user.getId()).status();

        List<Future<HttpStatus>> futures = executor.invokeAll(
                List.of(firstDelete, secondDelete))
                .stream()
                .map(future -> (Future<HttpStatus>) future)
                .toList();

        List<HttpStatus> statuses = futures.stream()
                .map(this::getFutureStatus)
                .toList();

        assertThat(statuses.stream().filter(HttpStatus.OK::equals).count())
                .isEqualTo(1);
        assertThat(statuses.stream().filter(HttpStatus.NOT_FOUND::equals).count())
                .isEqualTo(1);
        assertThat(repository.findById(user.getId())).isEmpty();
    }

    // Verifies that update and delete serialized for the same user do not corrupt final state.
    @Test
    void concurrentUpdateAndDeleteProducesConsistentFinalState() throws Exception {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("mixed-" + UUID.randomUUID() + "@example.com");
        user.setPassword("initial-password");
        user.setIsActive(true);
        repository.saveAndFlush(user);

        Callable<HttpStatus> update = () -> userService.updateUser(
                user.getId(),
                new UserUpdateRequest(user.getEmail(), true)).status();
        Callable<HttpStatus> delete = () -> userService.deleteUser(user.getId()).status();

        List<Future<HttpStatus>> futures = executor.invokeAll(
                List.of(update, delete))
                .stream()
                .map(future -> (Future<HttpStatus>) future)
                .toList();

        List<HttpStatus> statuses = futures.stream()
                .map(this::getFutureStatus)
                .toList();
        assertThat(statuses.stream().filter(HttpStatus.OK::equals).count())
                .isBetween(1L, 2L);
        assertThat(statuses.stream()
                .allMatch(status -> status == HttpStatus.OK || status == HttpStatus.NOT_FOUND))
                .isTrue();
        assertThat(repository.findById(user.getId())).isEmpty();
    }

    private HttpStatus getFutureStatus(Future<HttpStatus> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError("Concurrent request failed", exception);
        }
    }
}
