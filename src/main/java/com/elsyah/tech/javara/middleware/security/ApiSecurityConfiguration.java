package com.elsyah.tech.javara.middleware.security;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.elsyah.tech.javara.middleware.cors.CorsProperties;
import com.elsyah.tech.javara.middleware.feature.FeatureModulesProperties;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityHeadersProperties.class)
public class ApiSecurityConfiguration {
    private static final Set<String> PUBLIC_AUTH_ENDPOINTS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/mfa/login/otp/verify",
            "/api/v1/auth/mfa/login/passkey/verify",
            "/api/v1/auth/mfa/login/passkey/enroll",
            "/api/v1/auth/email/verify",
            "/api/v1/auth/email/verification/resend",
            "/api/v1/auth/password/reset/request",
            "/api/v1/auth/password/reset",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/csrf");

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            SecurityHeadersProperties securityHeaders,
            ObjectProvider<JwtProperties> jwtPropertiesProvider,
            CorsProperties corsProperties,
            ObjectProvider<CookieCsrfTokenRepository> csrfTokenRepositories,
            ObjectProvider<JwtDecoder> jwtDecoders,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler,
            FeatureModulesProperties features,
            Environment environment) throws Exception {
        boolean serviceAuthEnabled = environment.getProperty(
                "app.security.service-auth.enabled", Boolean.class, true);
        JwtProperties jwtProperties = null;
        if (features.resourceServer()) {
            jwtProperties = jwtPropertiesProvider.getIfAvailable();
            if (jwtProperties == null) {
                throw new IllegalStateException(
                        "Feature 'resource-server' is enabled but JWT settings are unavailable.");
            }
        }
        JwtProperties configuredJwtProperties = jwtProperties;
        JwtDecoder jwtDecoder = null;
        if (features.resourceServer()) {
            jwtDecoder = jwtDecoders.getIfAvailable();
            if (jwtDecoder == null) {
                throw new IllegalStateException(
                        "Feature 'resource-server' is enabled but no JwtDecoder is configured.");
            }
        }
        JwtDecoder configuredJwtDecoder = jwtDecoder;
        if (features.resourceServer()
                && configuredJwtProperties.transport() == TokenTransport.COOKIE
                && corsProperties.enabled()
                && !corsProperties.allowCredentials()) {
            throw new IllegalArgumentException(
                    "Cookie token transport with CORS requires app.web.cors.allow-credentials=true");
        }
        if (features.resourceServer()
                && configuredJwtProperties.transport() == TokenTransport.COOKIE
                && corsProperties.enabled()
                && corsProperties.allowedHeaders().stream()
                        .noneMatch(header -> "X-XSRF-TOKEN".equalsIgnoreCase(header))) {
            throw new IllegalArgumentException(
                    "Cookie token transport with CORS requires X-XSRF-TOKEN in app.web.cors.allowed-headers");
        }

        HttpSecurity security = http
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        security.authorizeHttpRequests(authorize -> {
            authorize.requestMatchers("/", "/error").permitAll();
            if (!features.resourceServer()) {
                authorize.anyRequest().permitAll();
                return;
            }
            if (!features.authApi()) {
                authorize.requestMatchers(
                        "/api/v1/auth", "/api/v1/auth/**",
                        "/api/v1/admin", "/api/v1/admin/**",
                        "/api/v1/users", "/api/v1/users/**").permitAll();
            } else {
                authorize.requestMatchers(PUBLIC_AUTH_ENDPOINTS.toArray(String[]::new)).permitAll();
            }
            if (!serviceAuthEnabled) {
                authorize.requestMatchers(
                        "/api/v1/service-clients", "/api/v1/service-clients/**",
                        "/oauth2/**", "/.well-known/**").permitAll();
            }
            authorize.anyRequest().authenticated();
        });

        if (!features.resourceServer()) {
            security.csrf(AbstractHttpConfigurer::disable);
        } else if (configuredJwtProperties.transport() == TokenTransport.COOKIE) {
            CookieCsrfTokenRepository csrfTokenRepository =
                    csrfTokenRepositories.getIfAvailable();
            if (csrfTokenRepository == null) {
                throw new IllegalStateException(
                        "Cookie token transport requires a CSRF token repository.");
            }
            security.csrf(csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers(
                            "/api/v1/auth/email/verify",
                            "/api/v1/auth/password/reset"));
        } else {
            security.csrf(AbstractHttpConfigurer::disable);
        }

        security.exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        if (features.resourceServer()) {
            security.oauth2ResourceServer(oauth2 -> oauth2
                    .bearerTokenResolver(bearerTokenResolver(
                            configuredJwtProperties,
                            configuredJwtDecoder,
                            features,
                            serviceAuthEnabled))
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        }

        if (securityHeaders.enabled()) {
            security.headers(Customizer.withDefaults());
        } else {
            security.headers(AbstractHttpConfigurer::disable);
        }

        return security.build();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.features",
            name = "resource-server",
            havingValue = "true",
            matchIfMissing = true)
    public CookieCsrfTokenRepository csrfTokenRepository(JwtProperties properties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .httpOnly(false));
        return repository;
    }

    private static BearerTokenResolver bearerTokenResolver(
            JwtProperties properties,
            JwtDecoder jwtDecoder,
            FeatureModulesProperties features,
            boolean serviceAuthEnabled) {
        BearerTokenResolver delegate;
        if (properties.transport() == TokenTransport.COOKIE) {
            delegate = new CookieOrServiceBearerTokenResolver(
                    properties.accessCookieName(), jwtDecoder);
        } else {
            delegate = new DefaultBearerTokenResolver();
        }
        return request -> isDisabledModuleRoute(
                        requestPath(request), features, serviceAuthEnabled)
                ? null
                : delegate.resolve(request);
    }

    private static boolean isDisabledModuleRoute(
            String path,
            FeatureModulesProperties features,
            boolean serviceAuthEnabled) {
        return !features.authApi()
                        && (path.equals("/api/v1/auth")
                                || path.startsWith("/api/v1/auth/")
                                || path.equals("/api/v1/admin")
                                || path.startsWith("/api/v1/admin/")
                                || path.equals("/api/v1/users")
                                || path.startsWith("/api/v1/users/"))
                || !serviceAuthEnabled
                        && (path.startsWith("/api/v1/service-clients")
                                || path.startsWith("/oauth2/")
                                || path.startsWith("/.well-known/"))
                || PUBLIC_AUTH_ENDPOINTS.contains(path);
    }

    private static String requestPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() || !path.startsWith(contextPath)
                ? path
                : path.substring(contextPath.length());
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        JwtGrantedAuthoritiesConverter scopeAuthorities = new JwtGrantedAuthoritiesConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new java.util.ArrayList<GrantedAuthority>();
            if ("service".equals(jwt.getClaimAsString("principal_type"))) {
                authorities.addAll(scopeAuthorities.convert(jwt));
            } else {
                var roles = jwt.getClaimAsStringList("roles");
                if (roles != null) {
                    roles.stream()
                            .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .forEach(authorities::add);
                }
            }
            return authorities;
        });
        return converter;
    }
}
