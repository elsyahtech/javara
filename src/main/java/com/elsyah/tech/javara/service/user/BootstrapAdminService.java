package com.elsyah.tech.javara.service.user;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.middleware.config.ApplicationIdentityProperties;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.utils.security.PasswordHashService;

@Service
@ConditionalOnProperty(
        prefix = "app.features",
        name = "auth-api",
        havingValue = "true",
        matchIfMissing = true)
public class BootstrapAdminService {
    private final UserRepository userRepository;
    private final PasswordHashService passwordHashService;
    private final PasswordPolicyService passwordPolicyService;
    private final UserRoleService userRoleService;
    private final ApplicationIdentityProperties applicationIdentity;

    public BootstrapAdminService(
            UserRepository userRepository,
            PasswordHashService passwordHashService,
            PasswordPolicyService passwordPolicyService,
            UserRoleService userRoleService,
            ApplicationIdentityProperties applicationIdentity) {
        this.userRepository = userRepository;
        this.passwordHashService = passwordHashService;
        this.passwordPolicyService = passwordPolicyService;
        this.userRoleService = userRoleService;
        this.applicationIdentity = applicationIdentity;
    }

    @Transactional
    public void createInitialAdmin(String email, String password) {
        var existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            if (!userRepository.hasPermission(existingUser.get().getId(), "users:manage")) {
                throw new IllegalStateException(
                        "Bootstrap admin email already belongs to a non-admin account");
            }
            return;
        }
        if (!passwordPolicyService.isPasswordAllowed(password)) {
            throw new IllegalStateException(
                    "Bootstrap administrator password was rejected by the password policy. "
                            + "Configure a unique password before starting "
                            + applicationIdentity.name() + ".");
        }

        UserEntity admin = new UserEntity();
        admin.setId(UUID.randomUUID());
        admin.setEmail(email);
        admin.setPassword(passwordHashService.encode(password));
        admin.setIsActive(true);
        UserEntity createdAdmin = userRepository.saveAndFlush(admin);
        userRoleService.assignInitialRoles(createdAdmin.getId(), java.util.List.of("ADMIN"));
    }
}
