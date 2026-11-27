package com.elsyah.tech.javara.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(
        prefix = "app.email",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EmailMessageListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailMessageListener.class);

    private final EmailDelivery emailDelivery;

    public EmailMessageListener(EmailDelivery emailDelivery) {
        this.emailDelivery = emailDelivery;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(EmailMessageEvent event) {
        try {
            emailDelivery.send(event.recipient(), event.subject(), event.body());
        } catch (MailException exception) {
            LOGGER.error(
                    "SMTP delivery failed for email event type {} ({}). Check SMTP configuration "
                            + "and provider availability; verification messages can be requested again.",
                    event.type(),
                    exception.getClass().getSimpleName());
        }
    }
}
