package com.elsyah.tech.javara.middleware.security;

import java.time.Clock;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import com.elsyah.tech.javara.service.auth.SessionTokenValidator;

@Configuration
@ConditionalOnProperty(
        prefix = "app.features",
        name = "resource-server",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties({
    JwtProperties.class,
    SessionProperties.class
})
public class JwtTokenConfiguration {
    private static final String ISSUER = "javara";

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(properties.secretKey()));
    }

    @Bean
    public JwtDecoder jwtDecoder(
            JwtProperties properties,
            ObjectProvider<RegisteredClientRepository> registeredClientRepositories,
            ObjectProvider<ServiceAuthProperties> serviceAuthProperties,
            SessionTokenValidator sessionTokenValidator,
            Clock clock) {
        return createJwtDecoder(
                properties,
                registeredClientRepositories::getIfAvailable,
                serviceAuthProperties::getIfAvailable,
                sessionTokenValidator::isActive,
                clock);
    }

    public JwtDecoder jwtDecoder(JwtProperties properties) {
        return jwtDecoder(properties, Clock.systemUTC());
    }

    public JwtDecoder jwtDecoder(JwtProperties properties, Clock clock) {
        return createJwtDecoder(properties, () -> null, () -> null, token -> true, clock);
    }

    private JwtDecoder createJwtDecoder(
            JwtProperties properties,
            Supplier<RegisteredClientRepository> registeredClientRepository,
            Supplier<ServiceAuthProperties> serviceAuthProperties,
            Predicate<Jwt> userSessionValidator,
            Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(ISSUER);
        decoder.setJwtValidator(token -> {
            if ("service".equals(token.getClaimAsString("principal_type"))) {
                ServiceAuthProperties config = serviceAuthProperties.get();
                RegisteredClientRepository repository = registeredClientRepository.get();
                String clientId = token.getClaimAsString("client_id");
                List<String> scopes = token.getClaimAsStringList("scope");
                if (config == null
                        || !config.enabled()
                        || repository == null
                        || clientId == null
                        || !clientId.equals(token.getSubject())
                        || token.hasClaim("roles")
                        || scopes == null
                        || scopes.isEmpty()
                        || !config.allowedScopes().containsAll(scopes)) {
                    return invalidToken();
                }
                var issuerResult = JwtValidators.createDefaultWithIssuer(config.issuer()).validate(token);
                if (issuerResult.hasErrors()) {
                    return issuerResult;
                }
                RegisteredClient client = repository.findByClientId(clientId);
                if (client == null
                        || client.getClientSecretExpiresAt() != null
                                && !client.getClientSecretExpiresAt().isAfter(clock.instant())) {
                    return invalidToken();
                }
                return OAuth2TokenValidatorResult.success();
            }

            var issuerResult = issuerValidator.validate(token);
            if (issuerResult.hasErrors()) {
                return issuerResult;
            }
            List<String> roles = token.getClaimAsStringList("roles");
            if (roles != null
                    && !roles.isEmpty()
                    && roles.stream().allMatch(role -> "USER".equals(role) || "ADMIN".equals(role))
                    && userSessionValidator.test(token)) {
                return OAuth2TokenValidatorResult.success();
            }
            return invalidToken();
        });
        return decoder;
    }

    private static OAuth2TokenValidatorResult invalidToken() {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "The access token claims are not valid", null));
    }

    public static String issuer() {
        return ISSUER;
    }
}
