package com.elsyah.tech.javara.service.user;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.entity.user.UserRole;
import com.elsyah.tech.javara.middleware.config.ApplicationIdentityProperties;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.utils.security.PasswordHashService;

@Service
public class BootstrapAdminService {
    private final UserRepository userRepository;
    private final PasswordHashService passwordHashService;
    private final PasswordPolicyService passwordPolicyService;
    private final ApplicationIdentityProperties applicationIdentity;

    public BootstrapAdminService(
            UserRepository userRepository,
            PasswordHashService passwordHashService,
            PasswordPolicyService passwordPolicyService,
            ApplicationIdentityProperties applicationIdentity) {
        this.userRepository = userRepository;
        this.passwordHashService = passwordHashService;
        this.passwordPolicyService = passwordPolicyService;
        this.applicationIdentity = applicationIdentity;
    }

    @Transactional
    public void createInitialAdmin(String email, String password) {
        var existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            if (existingUser.get().getRole() != UserRole.ADMIN) {
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
        admin.setRole(UserRole.ADMIN);
        userRepository.saveAndFlush(admin);
    }
}
