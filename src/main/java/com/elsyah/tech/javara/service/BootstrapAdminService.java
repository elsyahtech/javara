package com.elsyah.tech.javara.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.entity.UserEntity;
import com.elsyah.tech.javara.entity.UserRole;
import com.elsyah.tech.javara.repository.UserRepository;
import com.elsyah.tech.javara.utils.security.PasswordHashService;

@Service
public class BootstrapAdminService {
    private final UserRepository userRepository;
    private final PasswordHashService passwordHashService;

    public BootstrapAdminService(
            UserRepository userRepository,
            PasswordHashService passwordHashService) {
        this.userRepository = userRepository;
        this.passwordHashService = passwordHashService;
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

        UserEntity admin = new UserEntity();
        admin.setId(UUID.randomUUID().toString());
        admin.setEmail(email);
        admin.setPassword(passwordHashService.encode(password));
        admin.setIsActive(true);
        admin.setRole(UserRole.ADMIN);
        userRepository.saveAndFlush(admin);
    }
}
