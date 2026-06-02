package com.pharmaflow.auth_service.messaging.event;

public record UserCreatedEvent(
        Long userId,
        String email,
        String names,
        String surnames,
        String setPasswordToken
) implements AuthDomainEvent {}
