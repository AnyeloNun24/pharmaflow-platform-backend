package com.pharmaflow.notification_service.service.implementation;

import com.pharmaflow.notification_service.config.properties.NotificationMailProperties;
import com.pharmaflow.notification_service.messaging.event.AuthDomainEvent;
import com.pharmaflow.notification_service.messaging.event.PasswordResetRequestedEvent;
import com.pharmaflow.notification_service.messaging.event.UserCreatedEvent;
import com.pharmaflow.notification_service.service.interfaces.MailService;
import com.pharmaflow.notification_service.service.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Year;
import java.util.Map;

/**
 * Implementacion de {@link NotificationService} para el canal de email.
 * Traduce eventos de dominio de autenticacion en correos concretos: decide asunto,
 * plantilla y modelo de datos segun el tipo de evento y delega el envio en {@link MailService}.
 * <p>
 * Solo se registra como bean cuando {@code notification.channel=email} (valor por defecto).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "notification", name = "channel", havingValue = "email", matchIfMissing = true)
public class EmailNotificationService implements NotificationService {

    private static final String WELCOME_TEMPLATE = "mail/welcome";
    private static final String PASSWORD_RESET_TEMPLATE = "mail/password-reset";

    private final NotificationMailProperties mailProperties;
    private final MailService mailService;

    @Override
    public void sendMessage(AuthDomainEvent authDomainEvent) {
        switch (authDomainEvent) {
            case UserCreatedEvent e -> sendWelcome(e);
            case PasswordResetRequestedEvent e -> sendPasswordReset(e);
        }
    }

    private void sendWelcome(UserCreatedEvent userCreatedEvent) {
        String setPasswordUrl = buildFrontendUrl("/auth/set-password", userCreatedEvent.setPasswordToken());

        Map<String, Object> model = Map.of(
                "appName", mailProperties.appName(),
                "fullName", fullName(userCreatedEvent.names(), userCreatedEvent.surnames()),
                "setPasswordUrl", setPasswordUrl,
                "currentYear", Year.now().getValue()
        );

        mailService.sendHtml(
                userCreatedEvent.email(),
                "Bienvenido a %s - activa tu cuenta".formatted(mailProperties.appName()),
                WELCOME_TEMPLATE,
                model
        );

    }

    private void sendPasswordReset(PasswordResetRequestedEvent passwordResetRequestedEvent) {
        String resetUrl = buildFrontendUrl("/auth/reset-password", passwordResetRequestedEvent.resetToken());

        Map<String, Object> model = Map.of(
                "appName", mailProperties.appName(),
                "name", safe(passwordResetRequestedEvent.names()),
                "resetUrl", resetUrl,
                "currentYear", Year.now().getValue()
        );

        mailService.sendHtml(
                passwordResetRequestedEvent.email(),
                "Restablece tu contraseña de %s".formatted(mailProperties.appName()),
                PASSWORD_RESET_TEMPLATE,
                model
        );
    }

    private String buildFrontendUrl(String path, String token) {
        return UriComponentsBuilder
                .fromUriString(mailProperties.frontendBaseUrl())
                .path(path)
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    private String fullName(String names, String surnames) {
        return "%s %s".formatted(safe(names), safe(surnames)).trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

}
