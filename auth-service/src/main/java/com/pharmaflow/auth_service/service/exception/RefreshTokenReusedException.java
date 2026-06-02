package com.pharmaflow.auth_service.service.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * Se lanza cuando el refresh token presentado en /auth/refresh ya habia sido
 * rotado o revocado previamente. Es un evento de seguridad: la familia entera
 * se revoca como defensa contra robo de token.
 */
public class RefreshTokenReusedException extends AuthenticationException {

    public RefreshTokenReusedException(String message) {
        super(message);
    }
}
