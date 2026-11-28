package com.elsyah.tech.javara.unit.middleware.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.elsyah.tech.javara.middleware.security.ApiSecurityConfiguration;
import com.elsyah.tech.javara.service.user.UserRoleService;

class JwtAuthorityResolutionTests {
    @Test
    void ignoresLegacyJwtRoleClaimsAndLoadsCurrentAuthoritiesFromDatabase() {
        UUID userId = UUID.randomUUID();
        UserRoleService userRoleService = mock(UserRoleService.class);
        when(userRoleService.authoritiesForUser(userId)).thenReturn(List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("auth:self")));
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("userRoleService", userRoleService);
        ObjectProvider<UserRoleService> userRoleServices =
                beanFactory.getBeanProvider(UserRoleService.class);

        Jwt jwt = Jwt.withTokenValue("signed-test-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("roles", List.of("ADMIN"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        var authentication = new ApiSecurityConfiguration()
                .jwtAuthenticationConverter(userRoleServices)
                .convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_USER", "auth:self")
                .doesNotContain("ROLE_ADMIN");
    }
}
