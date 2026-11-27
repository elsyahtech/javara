package com.elsyah.tech.javara.service.auth;

import java.util.UUID;

import com.elsyah.tech.javara.entity.auth.MfaMethod;

public record MfaLoginCompletion(UUID userId, MfaMethod method) {}
