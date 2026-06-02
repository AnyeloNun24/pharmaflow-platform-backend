package com.pharmaflow.notification_service.messaging.event;

public record PasswordResetRequestedEvent(
        Long userId,
        String email,
        String names,
        String resetToken
) implements AuthDomainEvent {}
