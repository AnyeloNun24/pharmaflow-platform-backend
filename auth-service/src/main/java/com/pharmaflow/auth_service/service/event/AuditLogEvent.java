package com.pharmaflow.auth_service.service.event;

import com.pharmaflow.auth_service.service.interfaces.AuditLogService.ActionType;

import java.util.UUID;

public record AuditLogEvent(
        ActionType action,
        Long userId,
        String description,
        boolean success,
        String failureReason,
        UUID requestId,
        String ipAddress,
        String userAgent
) {
}
