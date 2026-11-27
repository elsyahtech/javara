package com.elsyah.tech.javara.middleware.security;

import java.net.URI;
import java.util.Set;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcOperations;

import com.elsyah.tech.javara.entity.auth.MfaMethod;
import com.elsyah.tech.javara.service.auth.MfaOtpDeliveryChannel;

import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;

import com.elsyah.tech.javara.middleware.config.ApplicationIdentityProperties;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(MfaProperties.class)
public class MfaConfiguration {
    @Bean
    ObjectMapper mfaWebAuthnObjectMapper() {
        return JsonMapper.builder()
                .addModule(new WebauthnJacksonModule())
                .build();
    }

    @Bean
    PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository(
            JdbcOperations jdbcOperations) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbcOperations);
    }

    @Bean
    UserCredentialRepository userCredentialRepository(JdbcOperations jdbcOperations) {
        return new JdbcUserCredentialRepository(jdbcOperations);
    }

    @Bean
    WebAuthnRelyingPartyOperations webAuthnRelyingPartyOperations(
            PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository userCredentials,
            MfaProperties properties,
            ApplicationIdentityProperties applicationIdentity) {
        Webauthn4JRelyingPartyOperations operations = new Webauthn4JRelyingPartyOperations(
                userEntities,
                userCredentials,
                PublicKeyCredentialRpEntity.builder()
                        .id(properties.relyingPartyId())
                        .name(applicationIdentity.name())
                        .build(),
                properties.allowedOrigins());
        operations.setCustomizeCreationOptions(options -> options.authenticatorSelection(
                AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build()));
        operations.setCustomizeRequestOptions(
                options -> options.userVerification(UserVerificationRequirement.REQUIRED));
        return operations;
    }

    @Bean
    MfaPolicyValidator mfaPolicyValidator(
            MfaProperties properties,
            Set<MfaOtpDeliveryChannel> otpDeliveryChannels,
            Environment environment) {
        return new MfaPolicyValidator(properties, otpDeliveryChannels, environment);
    }

    static final class MfaPolicyValidator {
        MfaPolicyValidator(
                MfaProperties properties,
                Set<MfaOtpDeliveryChannel> otpDeliveryChannels,
                Environment environment) {
            if (!properties.enabled()) {
                return;
            }
            for (MfaMethod method : properties.allowedMethods()) {
                if (method != MfaMethod.WEBAUTHN
                        && otpDeliveryChannels.stream().noneMatch(
                                channel -> channel.method() == method)) {
                    throw new IllegalStateException(
                            "MFA method " + method + " is allowed but has no delivery provider");
                }
            }
            boolean production = environment.acceptsProfiles(
                    Profiles.of("prod", "production", "staging"));
            if (production && properties.allowedMethods().contains(MfaMethod.WEBAUTHN)) {
                if ("localhost".equalsIgnoreCase(properties.relyingPartyId())) {
                    throw new IllegalStateException(
                            "Configure a production WebAuthn relying-party-id");
                }
                for (String origin : properties.allowedOrigins()) {
                    URI parsed = URI.create(origin);
                    if (!"https".equalsIgnoreCase(parsed.getScheme())) {
                        throw new IllegalStateException(
                                "WebAuthn allowed-origins must use HTTPS in production");
                    }
                }
            }
        }
    }
}
