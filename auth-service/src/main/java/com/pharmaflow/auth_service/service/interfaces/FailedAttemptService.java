package com.pharmaflow.auth_service.service.interfaces;

import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;

import java.util.Optional;

public interface FailedAttemptService {

    /**
     * Resetea el contador de fallos y estampa {@code last_login_at}.
     * Corre en {@code REQUIRES_NEW} para que el commit sea independiente de la transacción del login.
     */
    Optional<AuthUserEntity> onLoginSuccess(String email);

    /**
     * Incrementa el contador de fallos en {@code REQUIRES_NEW} (sobrevive al rollback del login).
     * Bloquea la cuenta si supera {@code max-failed-attempts} y audita {@code ACCOUNT_LOCKED}.
     */
    Optional<AuthUserEntity> onLoginFailure(String email);

    /** Si {@code lockout-duration-minutes} expiró desde {@code locked_at}, limpia el bloqueo antes de autenticar. */
    void tryAutoUnlock(String email);

    /** Desbloqueo manual por un actor; limpia lock y contador, audita {@code ACCOUNT_UNLOCKED} con el email del actor. */
    void forceUnlock(Long idUser, String actorEmail);
}
