package com.pharmaflow.auth_service.service.interfaces;

import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.entity.RefreshTokenEntity;

public interface RefreshTokenService {

    IssuedToken issueForUser(AuthUserEntity user, String ipAddress, String userAgent);

    RefreshTokenEntity validateAndConsume(String rawToken);

    IssuedToken rotate(RefreshTokenEntity current, String ipAddress, String userAgent);

    void revoke(String rawToken);

    AuthUserEntity revokeAndReturnUser(String rawToken);

    void revokeAllForUser(Long idUser);

    record IssuedToken(String rawToken, RefreshTokenEntity entity) {}
}
