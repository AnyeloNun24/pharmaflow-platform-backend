package com.pharmaflow.auth_service.service.interfaces;

import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.entity.RefreshTokenEntity;

public interface RefreshTokenService {

    /**
     * Persiste un nuevo token con familia UUID nueva y {@code absolute_expiry_at} fijo para toda esa familia.
     */
    IssuedToken issueForUser(AuthUserEntity user, String ipAddress, String userAgent);

    /**
     * Verifica que el token exista, no esté revocado y no haya superado los TTL deslizante y absoluto.
     * Si el token ya fue revocado, revoca toda la familia (detección de reuse).
     */
    RefreshTokenEntity validateAndConsume(String rawToken);

    /**
     * Emite un nuevo token en la misma familia con el mismo {@code absolute_expiry_at} y marca el actual como revocado.
     */
    IssuedToken rotate(RefreshTokenEntity current, String ipAddress, String userAgent);

    /** Revoca el token y devuelve el usuario propietario; revoca la familia completa si el token ya estaba revocado. */
    AuthUserEntity revokeAndReturnUser(String rawToken);

    /** Revoca todos los refresh tokens activos del usuario; se llama al cambiar contraseña o deshabilitar cuenta. */
    void revokeAllForUser(Long idUser);

    /** {@code rawToken}: valor opaco entregado al cliente — solo su SHA-256 se persiste en DB. */
    record IssuedToken(String rawToken, RefreshTokenEntity entity) {}
}
