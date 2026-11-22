package com.elsyah.tech.javara.middleware.ratelimit;

import java.time.Duration;

public interface RateLimitSettings {
    int maxRequests();

    Duration window();

    String keyPrefix();

    boolean enabled();
}
