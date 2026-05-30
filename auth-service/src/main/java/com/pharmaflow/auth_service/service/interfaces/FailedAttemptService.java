package com.pharmaflow.auth_service.service.interfaces;

import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;

import java.util.Optional;

public interface FailedAttemptService {

    /**
     * Resetea el contador de fallos y estampa last_login_at.
     * Devuelve el usuario si existe (para que el listener evite un lookup adicional).
     */
    Optional<AuthUserEntity> onLoginSuccess(String email);

    /**
     * Incrementa el contador de fallos de forma atomica y bloquea la cuenta si supera el limite.
     * Devuelve el usuario si existe.
     */
    Optional<AuthUserEntity> onLoginFailure(String email);

    void tryAutoUnlock(String email);

    void forceUnlock(Long idUser, String actorEmail);
}
