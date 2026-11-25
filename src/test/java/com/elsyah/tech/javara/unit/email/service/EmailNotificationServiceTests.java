package com.elsyah.tech.javara.unit.email.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.elsyah.tech.javara.middleware.email.EmailSettings;
import com.elsyah.tech.javara.service.email.EmailEventType;
import com.elsyah.tech.javara.service.email.EmailMessageEvent;
import com.elsyah.tech.javara.service.email.EmailNotificationService;

class EmailNotificationServiceTests {
    @Test
    void publishesVerificationWithSingleUseTokenLink() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        EmailNotificationService service = service(true, publisher);

        service.registrationVerification("person@example.com", "opaque-token");

        ArgumentCaptor<EmailMessageEvent> eventCaptor =
                ArgumentCaptor.forClass(EmailMessageEvent.class);
        verify(publisher).publishEvent(eventCaptor.capture());
        EmailMessageEvent event = eventCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(
                EmailEventType.REGISTRATION_VERIFICATION, event.type());
        org.junit.jupiter.api.Assertions.assertEquals("person@example.com", event.recipient());
        org.junit.jupiter.api.Assertions.assertTrue(event.body().contains(
                "https://javara.example.com/api/v1/auth/email/verify?token=opaque-token"));
    }

    @Test
    void publishesSecurityNoticeForEachNewSession() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        EmailNotificationService service = service(true, publisher);

        service.newSession("person@example.com");

        ArgumentCaptor<EmailMessageEvent> eventCaptor =
                ArgumentCaptor.forClass(EmailMessageEvent.class);
        verify(publisher).publishEvent(eventCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                EmailEventType.NEW_SESSION, eventCaptor.getValue().type());
    }

    @Test
    void disabledEmailDoesNotPublishMessage() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        service(false, publisher).passwordChanged("person@example.com");

        verify(publisher, never()).publishEvent(any());
    }

    private static EmailNotificationService service(
            boolean enabled,
            ApplicationEventPublisher publisher) {
        return new EmailNotificationService(
                new EmailSettings(
                        enabled,
                        Duration.ofHours(24),
                        "notify@example.com",
                        "https://javara.example.com"),
                publisher);
    }
}
