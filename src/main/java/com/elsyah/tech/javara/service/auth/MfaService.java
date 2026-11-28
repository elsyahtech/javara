package com.elsyah.tech.javara.service.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.crypto.Mac;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutableRelyingPartyRegistrationRequest;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.entity.auth.MfaChallengeEntity;
import com.elsyah.tech.javara.entity.auth.MfaChallengePurpose;
import com.elsyah.tech.javara.entity.auth.MfaMethod;
import com.elsyah.tech.javara.entity.auth.UserMfaMethodEntity;
import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.middleware.security.MfaProperties;
import com.elsyah.tech.javara.repository.auth.MfaChallengeRepository;
import com.elsyah.tech.javara.repository.auth.UserMfaMethodRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.response.auth.MfaChallengeResponse;
import com.elsyah.tech.javara.response.auth.MfaStatusResponse;
import com.elsyah.tech.javara.service.common.ServiceResult;
import com.elsyah.tech.javara.service.email.EmailNotificationService;
import com.elsyah.tech.javara.utils.security.PasswordHashService;
import com.webauthn4j.util.exception.WebAuthnException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(
        prefix = "app.features",
        name = "auth-api",
        havingValue = "true",
        matchIfMissing = true)
public class MfaService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MfaService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String OTP_HMAC_ALGORITHM = "HmacSHA256";
    private static final TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse>>
            ATTESTATION_TYPE = new TypeReference<>() {};
    private static final TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse>>
            ASSERTION_TYPE = new TypeReference<>() {};

    private final MfaProperties properties;
    private final MfaChallengeRepository challengeRepository;
    private final UserMfaMethodRepository methodRepository;
    private final UserRepository userRepository;
    private final SessionRevocationService sessionRevocationService;
    private final PasswordHashService passwordHashService;
    private final EmailNotificationService emailNotificationService;
    private final Map<MfaMethod, MfaOtpDeliveryChannel> otpChannels;
    private final PublicKeyCredentialUserEntityRepository webAuthnUserRepository;
    private final UserCredentialRepository webAuthnCredentialRepository;
    private final WebAuthnRelyingPartyOperations webAuthnOperations;
    private final ObjectMapper webAuthnObjectMapper;
    private final JwtProperties jwtProperties;
    private final SuspiciousAuthenticationService suspiciousAuthenticationService;
    private final Clock clock;

    public MfaService(
            MfaProperties properties,
            MfaChallengeRepository challengeRepository,
            UserMfaMethodRepository methodRepository,
            UserRepository userRepository,
            SessionRevocationService sessionRevocationService,
            PasswordHashService passwordHashService,
            EmailNotificationService emailNotificationService,
            List<MfaOtpDeliveryChannel> otpChannels,
            PublicKeyCredentialUserEntityRepository webAuthnUserRepository,
            UserCredentialRepository webAuthnCredentialRepository,
            WebAuthnRelyingPartyOperations webAuthnOperations,
            ObjectMapper webAuthnObjectMapper,
            JwtProperties jwtProperties,
            SuspiciousAuthenticationService suspiciousAuthenticationService,
            Clock clock) {
        this.properties = properties;
        this.challengeRepository = challengeRepository;
        this.methodRepository = methodRepository;
        this.userRepository = userRepository;
        this.sessionRevocationService = sessionRevocationService;
        this.passwordHashService = passwordHashService;
        this.emailNotificationService = emailNotificationService;
        this.otpChannels = otpChannels.stream().collect(Collectors.toUnmodifiableMap(
                MfaOtpDeliveryChannel::method, Function.identity()));
        this.webAuthnUserRepository = webAuthnUserRepository;
        this.webAuthnCredentialRepository = webAuthnCredentialRepository;
        this.webAuthnOperations = webAuthnOperations;
        this.webAuthnObjectMapper = webAuthnObjectMapper;
        this.jwtProperties = jwtProperties;
        this.suspiciousAuthenticationService = suspiciousAuthenticationService;
        this.clock = clock;
    }

    @Transactional
    public ServiceResult<MfaChallengeResponse> beginLogin(
            UserEntity user, MfaMethod requestedMethod) {
        if (!properties.enabled()) {
            if (requestedMethod != null) {
                return failure(HttpStatus.FORBIDDEN, "MFA is disabled by deployment policy");
            }
            return success(null, HttpStatus.OK, "MFA is disabled");
        }
        if (requestedMethod != null && !properties.allowedMethods().contains(requestedMethod)) {
            return failure(HttpStatus.FORBIDDEN, "MFA method is not allowed by policy");
        }

        List<MfaMethod> enrolled = enrolledMethods(user.getId());
        boolean enrollment = enrolled.isEmpty() && properties.required();
        if (enrolled.isEmpty() && !enrollment) {
            if (requestedMethod != null) {
                return failure(HttpStatus.BAD_REQUEST, "MFA is not enabled for this account");
            }
            return success(null, HttpStatus.OK, "MFA is not enabled for this account");
        }

        if (!enrollment && requestedMethod != null && !enrolled.contains(requestedMethod)) {
            return failure(HttpStatus.BAD_REQUEST, "MFA method is not enrolled for this account");
        }
        MfaMethod method = requestedMethod != null
                ? requestedMethod
                : enrollment
                        ? properties.defaultMethod()
                        : preferredMethod(user.getId(), enrolled);
        if (!properties.allowedMethods().contains(method)) {
            return failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The configured MFA method is not allowed by the deployment policy");
        }

        MfaChallengePurpose purpose = enrollment
                ? MfaChallengePurpose.REQUIRED_ENROLLMENT
                : MfaChallengePurpose.LOGIN;
        List<MfaMethod> availableMethods = enrollment
                ? List.copyOf(properties.allowedMethods())
                : enrolled;
        String action = enrollment ? "ENROLL" : "VERIFY";
        if (method == MfaMethod.WEBAUTHN) {
            return issuePasskeyChallenge(user, purpose, availableMethods, action);
        }
        return issueOtpChallenge(user, method, purpose, availableMethods, action);
    }

    @Transactional
    public ServiceResult<MfaStatusResponse> status(UUID userId) {
        List<MfaMethod> enrolled = enrolledMethods(userId);
        MfaMethod preferred = preferredMethodOrNull(userId, enrolled);
        return success(
                new MfaStatusResponse(
                        !enrolled.isEmpty(),
                        properties.required(),
                        List.copyOf(properties.allowedMethods()),
                        enrolled,
                        preferred),
                HttpStatus.OK,
                "MFA settings retrieved");
    }

    @Transactional
    public ServiceResult<MfaChallengeResponse> beginEmailEnrollment(
            UUID userId, String currentPassword) {
        if (!properties.enabled()
                || !properties.allowedMethods().contains(MfaMethod.EMAIL_OTP)) {
            return failure(HttpStatus.FORBIDDEN, "Email MFA is not allowed by policy");
        }
        if (!emailNotificationService.isEnabled()) {
            return failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Email MFA is unavailable because email delivery is not configured");
        }
        Optional<UserEntity> optionalUser = userRepository.findByIdForUpdate(userId);
        if (optionalUser.isEmpty()
                || !Boolean.TRUE.equals(optionalUser.get().getIsActive())
                || optionalUser.get().isPasswordResetRequired()
                || !optionalUser.get().isEmailVerified()
                || !passwordHashService.matches(
                        currentPassword, optionalUser.get().getPassword())) {
            return failure(HttpStatus.UNAUTHORIZED, "Current password is invalid");
        }
        if (methodRepository.findByUserIdAndMethodAndEnabledTrue(
                userId, MfaMethod.EMAIL_OTP).isPresent()) {
            return failure(HttpStatus.CONFLICT, "Email MFA is already enabled");
        }
        return issueOtpChallenge(
                optionalUser.get(),
                MfaMethod.EMAIL_OTP,
                MfaChallengePurpose.EMAIL_ENROLLMENT,
                List.of(MfaMethod.EMAIL_OTP),
                "ENROLL");
    }

    @Transactional
    public ServiceResult<MfaStatusResponse> verifyEmailEnrollment(UUID userId, UUID challengeId, String code) {
        ServiceResult<MfaChallengeEntity> challengeResult = verifiedEmailChallenge(
                challengeId, userId, MfaChallengePurpose.EMAIL_ENROLLMENT, code);
        if (challengeResult.isError()) {
            return failure(challengeResult.status(), challengeResult.message());
        }
        UserEntity user = activeUserForUpdate(userId);
        if (user == null
                || !user.isEmailVerified()
                || !passwordHashIsCurrent(challengeResult.data(), user)) {
            return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
        }
        MfaChallengeEntity challenge = challengeResult.data();
        challenge.setConsumedAt(clock.instant());
        enableMethod(userId, MfaMethod.EMAIL_OTP);
        suspiciousAuthenticationService.securityChange(user, "email MFA was enrolled");
        revokeSessions(userId);
        return status(userId);
    }

    @Transactional
    public ServiceResult<MfaChallengeResponse> beginPasskeyEnrollment(
            UUID userId, String currentPassword) {
        if (!properties.enabled()
                || !properties.allowedMethods().contains(MfaMethod.WEBAUTHN)) {
            return failure(HttpStatus.FORBIDDEN, "Passkey MFA is not allowed by policy");
        }
        Optional<UserEntity> optionalUser = userRepository.findByIdForUpdate(userId);
        if (optionalUser.isEmpty()
                || !Boolean.TRUE.equals(optionalUser.get().getIsActive())
                || optionalUser.get().isPasswordResetRequired()
                || !optionalUser.get().isEmailVerified()
                || !passwordHashService.matches(
                        currentPassword, optionalUser.get().getPassword())) {
            return failure(HttpStatus.UNAUTHORIZED, "Current password is invalid");
        }
        return issuePasskeyChallenge(
                optionalUser.get(),
                MfaChallengePurpose.PASSKEY_ENROLLMENT,
                List.of(MfaMethod.WEBAUTHN),
                "ENROLL");
    }

    @Transactional
    public ServiceResult<MfaStatusResponse> completePasskeyEnrollment(
            UUID userId, UUID challengeId, String label, JsonNode credential) {
        ServiceResult<MfaChallengeEntity> challengeResult = passkeyRegistrationChallenge(
                challengeId, userId, MfaChallengePurpose.PASSKEY_ENROLLMENT);
        if (challengeResult.isError()) {
            return failure(challengeResult.status(), challengeResult.message());
        }
        UserEntity user = activeUserForUpdate(userId);
        if (user == null
                || !user.isEmailVerified()
                || !passwordHashIsCurrent(challengeResult.data(), user)) {
            return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
        }
        ServiceResult<Void> registration = registerCredential(
                challengeResult.data(), label, credential);
        if (registration.isError()) {
            return failure(registration.status(), registration.message());
        }
        challengeResult.data().setConsumedAt(clock.instant());
        enableMethod(userId, MfaMethod.WEBAUTHN);
        suspiciousAuthenticationService.securityChange(user, "a passkey was enrolled");
        revokeSessions(userId);
        return status(userId);
    }

    @Transactional
    public ServiceResult<MfaLoginCompletion> verifyLoginOtp(UUID challengeId, String code) {
        Optional<MfaChallengeEntity> optionalChallenge =
                challengeRepository.findByIdForUpdate(challengeId);
        if (optionalChallenge.isEmpty()) {
            return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
        }
        MfaChallengeEntity challenge = optionalChallenge.get();
        if (!isUsable(challenge)
                || challenge.getMethod() == MfaMethod.WEBAUTHN
                || !properties.allowedMethods().contains(challenge.getMethod())
                || (challenge.getPurpose() != MfaChallengePurpose.LOGIN
                        && challenge.getPurpose() != MfaChallengePurpose.REQUIRED_ENROLLMENT)) {
            return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
        }
        if (!matchesCode(challenge, code)) {
            return failedAttempt(challenge);
        }
        UserEntity user = activeUserForUpdate(challenge.getUserId());
        if (user == null
                || !user.isEmailVerified()
                || !passwordHashIsCurrent(challenge, user)) {
            return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
        }
        challenge.setConsumedAt(clock.instant());
        if (challenge.getPurpose() == MfaChallengePurpose.REQUIRED_ENROLLMENT) {
            enableMethod(user.getId(), challenge.getMethod());
            suspiciousAuthenticationService.securityChange(
                    user, challenge.getMethod() == MfaMethod.WEBAUTHN
                            ? "a passkey was enrolled"
                            : "email MFA was enrolled");
        }
        return success(
                new MfaLoginCompletion(user.getId(), challenge.getMethod()),
                HttpStatus.OK,
                "MFA verification successful");
    }

    @Transactional
    public ServiceResult<MfaLoginCompletion> verifyLoginPasskey(
            UUID challengeId, JsonNode credential) {
        Optional<MfaChallengeEntity> optionalChallenge =
                challengeRepository.findByIdForUpdate(challengeId);
        if (optionalChallenge.isEmpty()) {
            return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
        }
        MfaChallengeEntity challenge = optionalChallenge.get();
        if (!isUsable(challenge)
                || challenge.getMethod() != MfaMethod.WEBAUTHN
                || challenge.getPurpose() != MfaChallengePurpose.LOGIN) {
            return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
        }
        UserEntity user = activeUserForUpdate(challenge.getUserId());
        if (user == null
                || !user.isEmailVerified()
                || !passwordHashIsCurrent(challenge, user)) {
            return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
        }
        ServiceResult<Void> verification = verifyPasskeyAssertion(challenge, user, credential);
        if (verification.isError()) {
            return failure(verification.status(), verification.message());
        }
        challenge.setConsumedAt(clock.instant());
        return success(
                new MfaLoginCompletion(user.getId(), MfaMethod.WEBAUTHN),
                HttpStatus.OK,
                "MFA verification successful");
    }

    @Transactional
    public ServiceResult<MfaLoginCompletion> completeRequiredPasskeyEnrollment(
            UUID challengeId, String label, JsonNode credential) {
        ServiceResult<MfaChallengeEntity> challengeResult = passkeyRegistrationChallenge(
                challengeId, null, MfaChallengePurpose.REQUIRED_ENROLLMENT);
        if (challengeResult.isError()) {
            return failure(challengeResult.status(), challengeResult.message());
        }
        MfaChallengeEntity challenge = challengeResult.data();
        UserEntity user = activeUserForUpdate(challenge.getUserId());
        if (user == null
                || !user.isEmailVerified()
                || !passwordHashIsCurrent(challenge, user)) {
            return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
        }
        ServiceResult<Void> registration = registerCredential(challenge, label, credential);
        if (registration.isError()) {
            recordLoginMfaFailure(challenge);
            return failure(registration.status(), registration.message());
        }
        challenge.setConsumedAt(clock.instant());
        enableMethod(user.getId(), MfaMethod.WEBAUTHN);
        suspiciousAuthenticationService.securityChange(user, "a passkey was enrolled");
        return success(
                new MfaLoginCompletion(user.getId(), MfaMethod.WEBAUTHN),
                HttpStatus.OK,
                "MFA enrollment successful");
    }

    @Transactional
    public ServiceResult<MfaStatusResponse> setPreferredMethod(
            UUID userId, MfaMethod method, String currentPassword) {
        if (!properties.allowedMethods().contains(method)) {
            return failure(HttpStatus.FORBIDDEN, "MFA method is not allowed by policy");
        }
        Optional<UserEntity> optionalUser = userRepository.findByIdForUpdate(userId);
        if (optionalUser.isEmpty()
                || !Boolean.TRUE.equals(optionalUser.get().getIsActive())
                || optionalUser.get().isPasswordResetRequired()
                || !passwordHashService.matches(
                        currentPassword, optionalUser.get().getPassword())) {
            return failure(HttpStatus.UNAUTHORIZED, "Current password is invalid");
        }
        Optional<UserMfaMethodEntity> selected =
                methodRepository.findByUserIdAndMethodAndEnabledTrue(userId, method);
        if (selected.isEmpty()) {
            return failure(HttpStatus.BAD_REQUEST, "MFA method is not enrolled");
        }
        for (UserMfaMethodEntity entry :
                methodRepository.findAllByUserIdAndEnabledTrueOrderByCreatedAtAsc(userId)) {
            entry.setPreferred(entry.getMethod() == method);
            entry.setUpdatedAt(clock.instant());
        }
        suspiciousAuthenticationService.securityChange(
                optionalUser.get(), "the preferred MFA method was changed");
        revokeSessions(userId);
        return status(userId);
    }

    @Transactional
    public ServiceResult<MfaStatusResponse> disableMethod(
            UUID userId, MfaMethod method, String currentPassword) {
        Optional<UserEntity> optionalUser = userRepository.findByIdForUpdate(userId);
        if (optionalUser.isEmpty()
                || !Boolean.TRUE.equals(optionalUser.get().getIsActive())
                || optionalUser.get().isPasswordResetRequired()
                || !passwordHashService.matches(
                        currentPassword, optionalUser.get().getPassword())) {
            return failure(HttpStatus.UNAUTHORIZED, "Current password is invalid");
        }
        Optional<UserMfaMethodEntity> current =
                methodRepository.findByUserIdAndMethodAndEnabledTrue(userId, method);
        if (current.isEmpty()) {
            return failure(HttpStatus.NOT_FOUND, "MFA method is not enabled");
        }
        List<UserMfaMethodEntity> activeMethods =
                methodRepository.findAllByUserIdAndEnabledTrueOrderByCreatedAtAsc(userId);
        if (properties.required() && activeMethods.size() == 1) {
            return failure(
                    HttpStatus.CONFLICT,
                    "At least one MFA method must remain enabled by deployment policy");
        }

        if (method == MfaMethod.WEBAUTHN) {
            Bytes userIdBytes = webAuthnUserId(userId);
            webAuthnCredentialRepository.findByUserId(userIdBytes)
                    .forEach(credential ->
                            webAuthnCredentialRepository.delete(credential.getCredentialId()));
            webAuthnUserRepository.delete(userIdBytes);
        }
        methodRepository.deleteByUserIdAndMethod(userId, method);
        if (current.get().isPreferred()) {
            activeMethods.stream()
                    .filter(entry -> entry.getMethod() != method)
                    .findFirst()
                    .ifPresent(entry -> {
                        entry.setPreferred(true);
                        entry.setUpdatedAt(clock.instant());
                    });
        }
        suspiciousAuthenticationService.securityChange(
                optionalUser.get(), "an MFA method was disabled");
        revokeSessions(userId);
        return status(userId);
    }

    private ServiceResult<MfaChallengeResponse> issueOtpChallenge(
            UserEntity user,
            MfaMethod method,
            MfaChallengePurpose purpose,
            List<MfaMethod> availableMethods,
            String action) {
        MfaOtpDeliveryChannel channel = otpChannels.get(method);
        if (channel == null || !channel.isEnabled()) {
            return failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The configured MFA delivery provider is unavailable");
        }
        Instant now = clock.instant();
        UUID challengeId = UUID.randomUUID();
        String code = String.format(
                java.util.Locale.ROOT, "%08d", SECURE_RANDOM.nextInt(100_000_000));
        MfaChallengeEntity challenge = newChallenge(
                challengeId,
                user,
                purpose,
                method,
                now);
        challenge.setCodeVerifier(codeVerifier(challengeId, user.getId(), code));
        challengeRepository.save(challenge);
        challengeRepository.deleteExpiredBefore(now);
        channel.send(user, code, properties.challengeTtl());
        return success(
                new MfaChallengeResponse(
                        challengeId,
                        method,
                        availableMethods,
                        action,
                        null,
                        challenge.getExpiresAt()),
                HttpStatus.ACCEPTED,
                "MFA verification code sent");
    }

    private ServiceResult<MfaChallengeResponse> issuePasskeyChallenge(
            UserEntity user,
            MfaChallengePurpose purpose,
            List<MfaMethod> availableMethods,
            String action) {
        Instant now = clock.instant();
        UUID challengeId = UUID.randomUUID();
        MfaChallengeEntity challenge = newChallenge(
                challengeId,
                user,
                purpose,
                MfaMethod.WEBAUTHN,
                now);
        JsonNode options;
        try {
            UsernamePasswordAuthenticationToken authentication = authenticationContext(user);
            if (purpose == MfaChallengePurpose.REQUIRED_ENROLLMENT
                    || purpose == MfaChallengePurpose.PASSKEY_ENROLLMENT) {
                ensureWebAuthnUserEntity(user);
                PublicKeyCredentialCreationOptions creationOptions =
                        webAuthnOperations.createPublicKeyCredentialCreationOptions(
                                new ImmutablePublicKeyCredentialCreationOptionsRequest(
                                        authentication));
                challenge.setOptionsJson(webAuthnObjectMapper.writeValueAsString(creationOptions));
                options = webAuthnObjectMapper.valueToTree(creationOptions);
            } else {
                ensureWebAuthnUserEntity(user);
                PublicKeyCredentialRequestOptions requestOptions =
                        webAuthnOperations.createCredentialRequestOptions(
                                new ImmutablePublicKeyCredentialRequestOptionsRequest(
                                        authentication));
                if (requestOptions.getAllowCredentials().isEmpty()) {
                    return failure(HttpStatus.UNAUTHORIZED, "Passkey MFA is not enrolled");
                }
                challenge.setOptionsJson(webAuthnObjectMapper.writeValueAsString(requestOptions));
                options = webAuthnObjectMapper.valueToTree(requestOptions);
            }
        } catch (JacksonException | IllegalArgumentException exception) {
            LOGGER.warn("Unable to create WebAuthn MFA challenge ({})",
                    exception.getClass().getSimpleName());
            return failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "WebAuthn MFA could not create a challenge");
        }
        challengeRepository.save(challenge);
        challengeRepository.deleteExpiredBefore(now);
        return success(
                new MfaChallengeResponse(
                        challengeId,
                        MfaMethod.WEBAUTHN,
                        availableMethods,
                        action,
                        options,
                        challenge.getExpiresAt()),
                HttpStatus.ACCEPTED,
                "WebAuthn MFA challenge created");
    }

    private ServiceResult<Void> registerCredential(
            MfaChallengeEntity challenge, String label, JsonNode credentialJson) {
        if (credentialJson == null || credentialJson.isNull() || label == null || label.isBlank()) {
            return failure(HttpStatus.BAD_REQUEST, "A passkey credential and label are required");
        }
        try {
            PublicKeyCredential<AuthenticatorAttestationResponse> credential =
                    webAuthnObjectMapper.convertValue(credentialJson, ATTESTATION_TYPE);
            PublicKeyCredentialCreationOptions options =
                    webAuthnObjectMapper.readValue(
                            challenge.getOptionsJson(), PublicKeyCredentialCreationOptions.class);
            if (!options.getUser().getId().equals(webAuthnUserId(challenge.getUserId()))) {
                return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
            }
            CredentialRecord registeredCredential =
                    webAuthnOperations.registerCredential(
                            new ImmutableRelyingPartyRegistrationRequest(
                                    options, new RelyingPartyPublicKey(credential, label)));
            if (!registeredCredential.getUserEntityUserId()
                    .equals(webAuthnUserId(challenge.getUserId()))) {
                webAuthnCredentialRepository.delete(registeredCredential.getCredentialId());
                return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
            }
            return success(null, HttpStatus.OK, "Passkey registered");
        } catch (WebAuthnException | IllegalArgumentException | JacksonException exception) {
            return rejectedWebAuthnAttempt(challenge, exception);
        }
    }

    private ServiceResult<Void> verifyPasskeyAssertion(
            MfaChallengeEntity challenge, UserEntity user, JsonNode credentialJson) {
        if (credentialJson == null || credentialJson.isNull()) {
            return failure(HttpStatus.BAD_REQUEST, "A passkey assertion is required");
        }
        try {
            PublicKeyCredential<AuthenticatorAssertionResponse> credential =
                    webAuthnObjectMapper.convertValue(credentialJson, ASSERTION_TYPE);
            PublicKeyCredentialRequestOptions options =
                    webAuthnObjectMapper.readValue(
                            challenge.getOptionsJson(), PublicKeyCredentialRequestOptions.class);
            PublicKeyCredentialUserEntity authenticatedUser =
                    webAuthnOperations.authenticate(
                            new RelyingPartyAuthenticationRequest(options, credential));
            if (!authenticatedUser.getId().equals(webAuthnUserId(user.getId()))) {
                return rejectedWebAuthnAttempt(challenge, null);
            }
            return success(null, HttpStatus.OK, "Passkey verified");
        } catch (WebAuthnException | IllegalArgumentException | JacksonException exception) {
            return rejectedWebAuthnAttempt(challenge, exception);
        }
    }

    private ServiceResult<Void> rejectedWebAuthnAttempt(
            MfaChallengeEntity challenge, RuntimeException exception) {
        if (exception != null) {
            LOGGER.warn("WebAuthn MFA verification was rejected ({})",
                    exception.getClass().getSimpleName());
        }
        challenge.setAttempts(challenge.getAttempts() + 1);
        if (challenge.getAttempts() >= properties.maxAttempts()) {
            challenge.setConsumedAt(clock.instant());
        }
        recordLoginMfaFailure(challenge);
        return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
    }

    private ServiceResult<MfaChallengeEntity> verifiedEmailChallenge(
            UUID challengeId,
            UUID userId,
            MfaChallengePurpose purpose,
            String code) {
        Optional<MfaChallengeEntity> optionalChallenge =
                challengeRepository.findByIdForUpdate(challengeId);
        if (optionalChallenge.isEmpty()) {
            return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
        }
        MfaChallengeEntity challenge = optionalChallenge.get();
        if (!isUsable(challenge)
                || !userId.equals(challenge.getUserId())
                || challenge.getPurpose() != purpose
                || challenge.getMethod() != MfaMethod.EMAIL_OTP) {
            return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
        }
        if (!matchesCode(challenge, code)) {
            return failedAttempt(challenge);
        }
        return success(challenge, HttpStatus.OK, "MFA code verified");
    }

    private ServiceResult<MfaChallengeEntity> passkeyRegistrationChallenge(
            UUID challengeId, UUID userId, MfaChallengePurpose purpose) {
        Optional<MfaChallengeEntity> optionalChallenge =
                challengeRepository.findByIdForUpdate(challengeId);
        if (optionalChallenge.isEmpty()) {
            return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
        }
        MfaChallengeEntity challenge = optionalChallenge.get();
        if (!isUsable(challenge)
                || (userId != null && !userId.equals(challenge.getUserId()))
                || challenge.getPurpose() != purpose
                || challenge.getMethod() != MfaMethod.WEBAUTHN) {
            return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
        }
        return success(challenge, HttpStatus.OK, "Passkey registration challenge is valid");
    }

    private boolean matchesCode(MfaChallengeEntity challenge, String code) {
        if (code == null || challenge.getCodeVerifier() == null) {
            return false;
        }
        byte[] expected = HexFormat.of().parseHex(challenge.getCodeVerifier());
        byte[] actual = HexFormat.of().parseHex(
                codeVerifier(challenge.getId(), challenge.getUserId(), code));
        return MessageDigest.isEqual(expected, actual);
    }

    private boolean passwordHashIsCurrent(MfaChallengeEntity challenge, UserEntity user) {
        byte[] expected = challenge.getPasswordHashDigest().getBytes(StandardCharsets.US_ASCII);
        byte[] actual = passwordHashDigest(user.getPassword()).getBytes(StandardCharsets.US_ASCII);
        boolean matches = MessageDigest.isEqual(expected, actual);
        if (!matches) {
            challenge.setConsumedAt(clock.instant());
        }
        return matches;
    }

    private <T> ServiceResult<T> failedAttempt(MfaChallengeEntity challenge) {
        challenge.setAttempts(challenge.getAttempts() + 1);
        if (challenge.getAttempts() >= properties.maxAttempts()) {
            challenge.setConsumedAt(clock.instant());
        }
        recordLoginMfaFailure(challenge);
        return failure(HttpStatus.UNAUTHORIZED, invalidChallengeMessage());
    }

    private void recordLoginMfaFailure(MfaChallengeEntity challenge) {
        if (challenge.getPurpose() == MfaChallengePurpose.LOGIN
                || challenge.getPurpose() == MfaChallengePurpose.REQUIRED_ENROLLMENT) {
            suspiciousAuthenticationService.recordFailedMfa(challenge.getUserId());
        }
    }

    private MfaChallengeEntity newChallenge(
            UUID id,
            UserEntity user,
            MfaChallengePurpose purpose,
            MfaMethod method,
            Instant now) {
        MfaChallengeEntity challenge = new MfaChallengeEntity();
        challenge.setId(id);
        challenge.setUserId(user.getId());
        challenge.setPurpose(purpose);
        challenge.setMethod(method);
        challenge.setPasswordHashDigest(passwordHashDigest(user.getPassword()));
        challenge.setAttempts(0);
        challenge.setExpiresAt(now.plus(properties.challengeTtl()));
        challenge.setCreatedAt(now);
        return challenge;
    }

    private static String passwordHashDigest(String passwordHash) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void enableMethod(UUID userId, MfaMethod method) {
        Instant now = clock.instant();
        List<UserMfaMethodEntity> activeMethods =
                methodRepository.findAllByUserIdAndEnabledTrueOrderByCreatedAtAsc(userId);
        UserMfaMethodEntity entry = methodRepository.findByUserIdAndMethod(userId, method)
                .orElseGet(() -> {
                    UserMfaMethodEntity created = new UserMfaMethodEntity();
                    created.setId(UUID.randomUUID());
                    created.setUserId(userId);
                    created.setMethod(method);
                    created.setCreatedAt(now);
                    return created;
                });
        entry.setEnabled(true);
        entry.setUpdatedAt(now);
        if (activeMethods.isEmpty()) {
            entry.setPreferred(true);
        }
        methodRepository.save(entry);
    }

    private List<MfaMethod> enrolledMethods(UUID userId) {
        List<MfaMethod> enrolled = new ArrayList<>();
        for (UserMfaMethodEntity entry :
                methodRepository.findAllByUserIdAndEnabledTrueOrderByCreatedAtAsc(userId)) {
            if (properties.allowedMethods().contains(entry.getMethod())
                    && (entry.getMethod() != MfaMethod.WEBAUTHN || hasPasskeys(userId))) {
                enrolled.add(entry.getMethod());
            }
        }
        return List.copyOf(enrolled);
    }

    private MfaMethod preferredMethod(UUID userId, List<MfaMethod> enrolled) {
        return preferredMethodOrNull(userId, enrolled) != null
                ? preferredMethodOrNull(userId, enrolled)
                : enrolled.contains(properties.defaultMethod())
                        ? properties.defaultMethod()
                        : enrolled.getFirst();
    }

    private MfaMethod preferredMethodOrNull(UUID userId, List<MfaMethod> enrolled) {
        return methodRepository.findAllByUserIdAndEnabledTrueOrderByCreatedAtAsc(userId)
                .stream()
                .filter(UserMfaMethodEntity::isPreferred)
                .map(UserMfaMethodEntity::getMethod)
                .filter(enrolled::contains)
                .findFirst()
                .orElse(null);
    }

    private boolean hasPasskeys(UUID userId) {
        PublicKeyCredentialUserEntity userEntity =
                webAuthnUserRepository.findById(webAuthnUserId(userId));
        return userEntity != null
                && !webAuthnCredentialRepository.findByUserId(userEntity.getId()).isEmpty();
    }

    private void ensureWebAuthnUserEntity(UserEntity user) {
        Bytes stableId = webAuthnUserId(user.getId());
        PublicKeyCredentialUserEntity existing = webAuthnUserRepository.findById(stableId);
        if (existing == null) {
            webAuthnUserRepository.save(
                    org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity
                            .builder()
                            .id(stableId)
                            .name(user.getId().toString())
                            .displayName(user.getId().toString())
                            .build());
        }
    }

    private UsernamePasswordAuthenticationToken authenticationContext(UserEntity user) {
        return UsernamePasswordAuthenticationToken.authenticated(
                user.getId().toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private UserEntity activeUserForUpdate(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive())
                        && !user.isPasswordResetRequired())
                .orElse(null);
    }

    private void revokeSessions(UUID userId) {
        sessionRevocationService.revokeAllForUser(userId, clock.instant());
    }

    private boolean isUsable(MfaChallengeEntity challenge) {
        return challenge.getConsumedAt() == null
                && challenge.getExpiresAt().isAfter(clock.instant())
                && challenge.getAttempts() < properties.maxAttempts();
    }

    private String codeVerifier(UUID challengeId, UUID userId, String code) {
        try {
            Mac mac = Mac.getInstance(OTP_HMAC_ALGORITHM);
            mac.init(jwtProperties.secretKey());
            String input = "javara-mfa-otp:" + challengeId + ":" + userId + ":" + code;
            return HexFormat.of().formatHex(
                    mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("MFA verifier is unavailable", exception);
        }
    }

    private static Bytes webAuthnUserId(UUID userId) {
        ByteBuffer bytes = ByteBuffer.allocate(16)
                .putLong(userId.getMostSignificantBits())
                .putLong(userId.getLeastSignificantBits());
        return new Bytes(bytes.array());
    }

    private static String invalidChallengeMessage() {
        return "MFA challenge is invalid, expired, or has already been used";
    }

    private static <T> ServiceResult<T> success(T data, HttpStatus status, String message) {
        return new ServiceResult<>(data, status, message);
    }

    private static <T> ServiceResult<T> failure(HttpStatus status, String message) {
        return new ServiceResult<>(null, status, message);
    }
}
