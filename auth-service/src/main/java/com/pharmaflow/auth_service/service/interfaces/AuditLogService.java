package com.pharmaflow.auth_service.service.interfaces;

import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;

public interface AuditLogService {

    /**
     * Cada valor debe existir también en el CHECK constraint {@code ck__auth_audit_log__action_type} de la DB.
     * Al añadir un nuevo valor hay que incluir una migración Flyway que actualice el constraint.
     */
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
        PASSWORD_CHANGED,
        USER_CREATED
    }

    /** Publica un {@code AuditLogEvent(success=true)}; el listener lo persiste tras el commit ({@code AFTER_COMMIT}). */
    void recordSuccess(ActionType action, AuthUserEntity user, String description);

    /**
     * Publica un {@code AuditLogEvent(success=false)}; el listener lo persiste al completar ({@code AFTER_COMPLETION}),
     * incluso si la transacción del caller hizo rollback — crítico para auditar fallos de login.
     */
    void recordFailure(ActionType action, AuthUserEntity user, String description, String failureReason);
}
