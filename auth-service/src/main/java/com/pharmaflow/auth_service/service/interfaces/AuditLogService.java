package com.pharmaflow.auth_service.service.interfaces;

import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;

public interface AuditLogService {

    enum ActionType {
        LOGIN,
        LOGOUT,
        LOGIN_FAILED,
        REFRESH_TOKEN,
        REFRESH_TOKEN_REUSE,
        SET_PASSWORD,
        RESET_PASSWORD,
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED,
        ROLE_ASSIGNED,
        ROLE_REVOKED,
        PASSWORD_CHANGED
    }

    void recordSuccess(ActionType action, AuthUserEntity user, String description);

    void recordFailure(ActionType action, AuthUserEntity user, String description, String failureReason);
}
