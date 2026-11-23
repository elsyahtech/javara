package com.elsyah.tech.javara.service.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import com.elsyah.tech.javara.exception.ApiException;
import com.elsyah.tech.javara.middleware.security.ServiceAuthProperties;
import com.elsyah.tech.javara.request.auth.CreateServiceClientRequest;
import com.elsyah.tech.javara.response.auth.ProvisionedServiceClientResponse;
import com.elsyah.tech.javara.utils.security.PasswordHashService;

@Service
@ConditionalOnProperty(
        prefix = "app.security.service-auth",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ServiceClientService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RegisteredClientRepository clients;
    private final PasswordHashService passwordHashService;
    private final ServiceAuthProperties properties;
    private final Clock clock;

    public ServiceClientService(
            RegisteredClientRepository clients,
            PasswordHashService passwordHashService,
            ServiceAuthProperties properties,
            Clock clock) {
        this.clients = clients;
        this.passwordHashService = passwordHashService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ProvisionedServiceClientResponse provision(CreateServiceClientRequest request) {
        Set<String> scopes = validatedScopes(request.scopes());
        String clientId = "svc_" + randomValue(18);
        String secret = randomValue(32);
        Instant createdAt = clock.instant();
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientIdIssuedAt(createdAt)
                .clientSecret(passwordHashService.bcryptEncoder().encode(secret))
                .clientName(request.name().trim())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scopes(registeredClient -> registeredClient.addAll(scopes))
                .clientSettings(ClientSettings.builder().build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(properties.accessTokenTtl())
                        .build())
                .build();
        clients.save(client);
        return secretResponse(client, secret);
    }

    @Transactional
    public ProvisionedServiceClientResponse rotate(String clientId) {
        RegisteredClient existing = requireClient(clientId);
        String secret = randomValue(32);
        RegisteredClient rotated = copy(existing)
                .clientSecret(passwordHashService.bcryptEncoder().encode(secret))
                .clientSecretExpiresAt(null)
                .build();
        clients.save(rotated);
        return secretResponse(rotated, secret);
    }

    @Transactional
    public void revoke(String clientId) {
        RegisteredClient existing = requireClient(clientId);
        RegisteredClient revoked = copy(existing)
                .clientSecret(passwordHashService.bcryptEncoder().encode(randomValue(32)))
                .clientSecretExpiresAt(clock.instant())
                .build();
        clients.save(revoked);
    }

    private RegisteredClient requireClient(String clientId) {
        RegisteredClient client = clients.findByClientId(clientId);
        if (client == null) {
            throw new ApiException("Service client was not found", HttpStatus.NOT_FOUND);
        }
        return client;
    }

    private Set<String> validatedScopes(Set<String> requested) {
        if (requested == null || requested.isEmpty()
                || !properties.allowedScopes().containsAll(requested)) {
            throw new ApiException("Requested scopes are not allowed", HttpStatus.BAD_REQUEST);
        }
        return Set.copyOf(new TreeSet<>(requested));
    }

    private ProvisionedServiceClientResponse secretResponse(RegisteredClient client, String secret) {
        return new ProvisionedServiceClientResponse(
                client.getClientId(),
                secret,
                client.getClientName(),
                client.getScopes(),
                client.getClientIdIssuedAt());
    }

    private static RegisteredClient.Builder copy(RegisteredClient client) {
        return RegisteredClient.from(client);
    }

    private static String randomValue(int byteCount) {
        byte[] bytes = new byte[byteCount];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
