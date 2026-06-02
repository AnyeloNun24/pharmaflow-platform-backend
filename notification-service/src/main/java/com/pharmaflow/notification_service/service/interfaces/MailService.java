package com.pharmaflow.notification_service.service.interfaces;

import java.util.Map;

public interface MailService {

    // Envia el correo en formato HTML
    void sendHtml(String to, String subject, String template, Map<String, Object> variables);

}
