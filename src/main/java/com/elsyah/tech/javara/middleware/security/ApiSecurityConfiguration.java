package com.elsyah.tech.javara.middleware.security;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({
    SecurityHeadersProperties.class,
    JwtProperties.class,
    SessionProperties.class
})
public class ApiSecurityConfiguration {
    private static final Set<String> PUBLIC_AUTH_ENDPOINTS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/csrf");

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            SecurityHeadersProperties securityHeaders,
            JwtProperties jwtProperties,
            CorsProperties corsProperties,
            CookieCsrfTokenRepository csrfTokenRepository,
            JwtDecoder jwtDecoder,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler) throws Exception {
        if (jwtProperties.transport() == TokenTransport.COOKIE
                && corsProperties.enabled()
                && !corsProperties.allowCredentials()) {
            throw new IllegalArgumentException(
                    "Cookie token transport with CORS requires app.web.cors.allow-credentials=true");
        }
        if (jwtProperties.transport() == TokenTransport.COOKIE
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
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/csrf").permitAll()
                        .anyRequest().authenticated());

        if (jwtProperties.transport() == TokenTransport.COOKIE) {
            security.csrf(csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
        } else {
            security.csrf(AbstractHttpConfigurer::disable);
        }

        security.exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver(jwtProperties, jwtDecoder))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        if (securityHeaders.enabled()) {
            security.headers(Customizer.withDefaults());
        } else {
            security.headers(AbstractHttpConfigurer::disable);
        }

        return security.build();
    }

    @Bean
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
            JwtDecoder jwtDecoder) {
        BearerTokenResolver delegate;
        if (properties.transport() == TokenTransport.COOKIE) {
            delegate = new CookieOrServiceBearerTokenResolver(
                    properties.accessCookieName(), jwtDecoder);
        } else {
            delegate = new DefaultBearerTokenResolver();
        }
        return request -> PUBLIC_AUTH_ENDPOINTS.contains(requestPath(request))
                ? null
                : delegate.resolve(request);
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
