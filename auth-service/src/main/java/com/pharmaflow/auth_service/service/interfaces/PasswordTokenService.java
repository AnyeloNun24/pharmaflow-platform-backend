package com.pharmaflow.auth_service.service.interfaces;

import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;

public interface PasswordTokenService {

    /** Emite un token de tipo SET_PASSWORD (TTL largo) para usuarios recién creados sin contraseña. */
    String issueSetPasswordToken(AuthUserEntity user);

    /** Invalida el token RESET_PASSWORD anterior del usuario y emite uno nuevo (TTL corto). */
    String issueResetPasswordToken(AuthUserEntity user);

    /**
     * Valida el token (existencia, no consumido, no expirado), actualiza la contraseña con BCrypt
     * y revoca todos los refresh tokens activos para forzar re-login en todos los dispositivos.
     */
    void consumeAndChangePassword(String token, String newRawPassword);

}
