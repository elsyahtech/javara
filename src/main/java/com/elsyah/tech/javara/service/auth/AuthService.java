package com.elsyah.tech.javara.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.entity.auth.MfaMethod;
import com.elsyah.tech.javara.entity.auth.RefreshTokenEntity;
import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.middleware.security.SessionProperties;
import com.elsyah.tech.javara.repository.auth.RefreshTokenRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.request.auth.LoginRequest;
import com.elsyah.tech.javara.response.auth.MfaChallengeResponse;
import com.elsyah.tech.javara.service.common.ServiceResult;
import com.elsyah.tech.javara.service.email.EmailNotificationService;
import com.elsyah.tech.javara.service.user.UserService;

@Service
@ConditionalOnProperty(
        prefix = "app.features",
        name = "auth-api",
        havingValue = "true",
        matchIfMissing = true)
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserService userService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenService accessTokenService;
    private final JwtProperties jwtProperties;
    private final SessionProperties sessionProperties;
    private final EmailNotificationService emailNotificationService;
    private final MfaService mfaService;
    private final SuspiciousAuthenticationService suspiciousAuthenticationService;
    private final Clock clock;

    public AuthService(
            UserService userService,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            AccessTokenService accessTokenService,
            JwtProperties jwtProperties,
            SessionProperties sessionProperties,
            EmailNotificationService emailNotificationService,
            MfaService mfaService,
            SuspiciousAuthenticationService suspiciousAuthenticationService,
            Clock clock) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenService = accessTokenService;
        this.jwtProperties = jwtProperties;
        this.sessionProperties = sessionProperties;
        this.emailNotificationService = emailNotificationService;
        this.mfaService = mfaService;
        this.suspiciousAuthenticationService = suspiciousAuthenticationService;
        this.clock = clock;
    }

    @Transactional
    public AuthServiceResult login(LoginRequest request) {
        return login(request, null);
    }

    @Transactional
    public AuthServiceResult login(LoginRequest request, String remoteAddress) {
        ServiceResult<UserEntity> login = userService.login(request);
        if (login.isError()) {
            if (login.status() == HttpStatus.UNAUTHORIZED
                    && request.email() != null
                    && !request.email().isBlank()) {
                userRepository.findByEmailForUpdate(request.email())
                        .filter(user -> Boolean.TRUE.equals(user.getIsActive())
                                && user.isEmailVerified()
                                && !user.isPasswordResetRequired())
                        .ifPresent(suspiciousAuthenticationService::recordFailedPassword);
            }
            return AuthServiceResult.failure(login.status(), login.message());
        }

        UserEntity user = login.data();
        Optional<UserEntity> lockedUser = userRepository.findByIdForUpdate(user.getId());
        if (lockedUser.isEmpty() || !Boolean.TRUE.equals(lockedUser.get().getIsActive())) {
            return AuthServiceResult.failure(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password");
        }
        if (suspiciousAuthenticationService.isLocked(lockedUser.get())) {
            return AuthServiceResult.failure(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password");
        }
        suspiciousAuthenticationService.resetPasswordFailures(lockedUser.get());

        ServiceResult<MfaChallengeResponse> mfa =
                mfaService.beginLogin(lockedUser.get(), request.mfaMethod());
        if (mfa.isError()) {
            return AuthServiceResult.failure(mfa.status(), mfa.message());
        }
        if (mfa.data() != null) {
            return AuthServiceResult.challenge(mfa.data(), mfa.message());
        }
        return issueLoginTokens(lockedUser.get(), List.of("pwd"), remoteAddress);
    }

    @Transactional
    public AuthServiceResult verifyMfaOtpLogin(UUID challengeId, String code) {
        return verifyMfaOtpLogin(challengeId, code, null);
    }

    @Transactional
    public AuthServiceResult verifyMfaOtpLogin(
            UUID challengeId, String code, String remoteAddress) {
        ServiceResult<MfaLoginCompletion> verification =
                mfaService.verifyLoginOtp(challengeId, code);
        if (verification.isError()) {
            return AuthServiceResult.failure(verification.status(), verification.message());
        }
        return completeMfaLogin(verification.data(), remoteAddress);
    }

    @Transactional
    public AuthServiceResult verifyMfaPasskeyLogin(
            UUID challengeId, tools.jackson.databind.JsonNode credential) {
        return verifyMfaPasskeyLogin(challengeId, credential, null);
    }

    @Transactional
    public AuthServiceResult verifyMfaPasskeyLogin(
            UUID challengeId,
            tools.jackson.databind.JsonNode credential,
            String remoteAddress) {
        ServiceResult<MfaLoginCompletion> verification =
                mfaService.verifyLoginPasskey(challengeId, credential);
        if (verification.isError()) {
            return AuthServiceResult.failure(verification.status(), verification.message());
        }
        return completeMfaLogin(verification.data(), remoteAddress);
    }

    @Transactional
    public AuthServiceResult completeRequiredPasskeyEnrollment(
            UUID challengeId, String label, tools.jackson.databind.JsonNode credential) {
        return completeRequiredPasskeyEnrollment(challengeId, label, credential, null);
    }

    @Transactional
    public AuthServiceResult completeRequiredPasskeyEnrollment(
            UUID challengeId,
            String label,
            tools.jackson.databind.JsonNode credential,
            String remoteAddress) {
        ServiceResult<MfaLoginCompletion> enrollment =
                mfaService.completeRequiredPasskeyEnrollment(challengeId, label, credential);
        if (enrollment.isError()) {
            return AuthServiceResult.failure(enrollment.status(), enrollment.message());
        }
        return completeMfaLogin(enrollment.data(), remoteAddress);
    }

    @Transactional
    public AuthServiceResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return invalidRefreshToken();
        }

        Instant now = clock.instant();
        String tokenHash = hashToken(rawRefreshToken);
        Optional<UUID> tokenUserId = refreshTokenRepository.findUserIdByTokenHash(tokenHash);
        if (tokenUserId.isEmpty()) {
            return invalidRefreshToken();
        }

        UUID userId = tokenUserId.get();
        Optional<UserEntity> optionalUser = userRepository.findByIdForUpdate(userId);
        Optional<RefreshTokenEntity> storedToken =
                refreshTokenRepository.findByTokenHashForUpdate(tokenHash);
        if (storedToken.isEmpty()) {
            return invalidRefreshToken();
        }

        RefreshTokenEntity token = storedToken.get();
        if (token.getRevokedAt() != null) {
            revokeActiveTokens(token.getUserId(), now);
            return invalidRefreshToken();
        }
        Instant idleDeadline = now.minus(sessionProperties.idleTimeout());
        if (!token.getExpiresAt().isAfter(now)
                || !token.getSessionExpiresAt().isAfter(now)
                || !token.getLastActivityAt().isAfter(idleDeadline)) {
            token.setRevokedAt(now);
            refreshTokenRepository.revokeActiveTokensForSession(
                    token.getSessionId(), userId, now);
            return invalidRefreshToken();
        }

        if (optionalUser.isEmpty() || !Boolean.TRUE.equals(optionalUser.get().getIsActive())) {
            token.setRevokedAt(now);
            revokeActiveTokens(token.getUserId(), now);
            return invalidRefreshToken();
        }

        token.setRevokedAt(now);
        return issueTokens(
                optionalUser.get(),
                token.getSessionId(),
                token.getSessionStartedAt(),
                token.getSessionExpiresAt(),
                storedAuthenticationMethods(token),
                "Token refreshed successfully");
    }

    @Transactional
    public boolean logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return false;
        }

        Optional<RefreshTokenEntity> storedToken =
                refreshTokenRepository.findByTokenHashForUpdate(hashToken(rawRefreshToken));
        if (storedToken.isEmpty()) {
            return false;
        }

        RefreshTokenEntity token = storedToken.get();
        if (token.getRevokedAt() == null) {
            refreshTokenRepository.revokeActiveTokensForSession(
                    token.getSessionId(), token.getUserId(), clock.instant());
        }
        return true;
    }

    private AuthServiceResult issueTokens(
            UserEntity user,
            UUID sessionId,
            Instant sessionStartedAt,
            Instant sessionExpiresAt,
            List<String> authenticationMethods,
            String message) {
        String rawRefreshToken = newRefreshToken();
        Instant now = clock.instant();
        RefreshTokenEntity storedToken = new RefreshTokenEntity();
        storedToken.setTokenHash(hashToken(rawRefreshToken));
        storedToken.setUserId(user.getId());
        storedToken.setSessionId(sessionId);
        storedToken.setSessionStartedAt(sessionStartedAt);
        storedToken.setLastActivityAt(now);
        storedToken.setSessionExpiresAt(sessionExpiresAt);
        storedToken.setAuthenticationMethods(String.join(",", authenticationMethods));
        storedToken.setCreatedAt(now);
        storedToken.setExpiresAt(min(
                now.plus(jwtProperties.refreshTokenTtl()),
                sessionExpiresAt));
        refreshTokenRepository.save(storedToken);

        return new AuthServiceResult(
                user,
                accessTokenService.issue(
                        user, sessionId, sessionStartedAt, authenticationMethods),
                rawRefreshToken,
                HttpStatus.OK,
                message);
    }

    private AuthServiceResult issueLoginTokens(
            UserEntity user, List<String> authenticationMethods, String remoteAddress) {
        Instant now = clock.instant();
        long activeSessions = refreshTokenRepository.countActiveSessions(
                user.getId(),
                now,
                now.minus(sessionProperties.idleTimeout()));
        if (activeSessions >= sessionProperties.maxConcurrentSessions()) {
            return AuthServiceResult.failure(
                    HttpStatus.CONFLICT,
                    "Maximum number of active sessions reached");
        }
        if (!suspiciousAuthenticationService.recordSuccessfulSource(user, remoteAddress)) {
            return AuthServiceResult.failure(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password");
        }
        AuthServiceResult result = issueTokens(
                user,
                UUID.randomUUID(),
                now,
                now.plus(sessionProperties.absoluteLifetime()),
                authenticationMethods,
                "Login successful");
        emailNotificationService.newSession(user.getEmail());
        return result;
    }

    private AuthServiceResult completeMfaLogin(
            MfaLoginCompletion completion, String remoteAddress) {
        Optional<UserEntity> optionalUser =
                userRepository.findByIdForUpdate(completion.userId());
        if (optionalUser.isEmpty()
                || !Boolean.TRUE.equals(optionalUser.get().getIsActive())
                || !optionalUser.get().isEmailVerified()
                || optionalUser.get().isPasswordResetRequired()
                || suspiciousAuthenticationService.isLocked(optionalUser.get())) {
            return AuthServiceResult.failure(
                    HttpStatus.UNAUTHORIZED,
                    "MFA challenge is invalid, expired, or has already been used");
        }
        suspiciousAuthenticationService.resetMfaFailures(optionalUser.get());
        String method = completion.method() == MfaMethod.WEBAUTHN ? "hwk" : "otp";
        return issueLoginTokens(optionalUser.get(), List.of("pwd", method), remoteAddress);
    }

    private static List<String> storedAuthenticationMethods(RefreshTokenEntity token) {
        String methods = token.getAuthenticationMethods();
        return methods == null || methods.isBlank()
                ? List.of("pwd")
                : List.of(methods.split(","));
    }

    private void revokeActiveTokens(UUID userId, Instant revokedAt) {
        refreshTokenRepository.revokeActiveTokensForUser(userId, revokedAt);
    }

    private static Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static String newRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static AuthServiceResult invalidRefreshToken() {
        return AuthServiceResult.failure(
                HttpStatus.UNAUTHORIZED,
                "Refresh token is invalid or expired");
    }
}
