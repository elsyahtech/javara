package com.elsyah.tech.javara.service.email;

public record EmailMessageEvent(
        EmailEventType type,
        String recipient,
        String subject,
        String body) {}
