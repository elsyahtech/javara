package com.elsyah.tech.javara.middleware.email;

import java.time.Duration;

public record EmailSettings(
        boolean enabled,
        Duration verificationTokenTtl,
        String fromAddress,
        String publicBaseUrl) {}
