package com.elsyah.tech.javara.service.email;

public interface EmailDelivery {
    void send(String recipient, String subject, String body);
}
