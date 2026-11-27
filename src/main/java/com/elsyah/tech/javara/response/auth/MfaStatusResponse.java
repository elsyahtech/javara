package com.elsyah.tech.javara.response.auth;

import java.util.List;

import com.elsyah.tech.javara.entity.auth.MfaMethod;

public record MfaStatusResponse(
        boolean enabled,
        boolean requiredByPolicy,
        List<MfaMethod> allowedMethods,
        List<MfaMethod> enrolledMethods,
        MfaMethod preferredMethod) {}
