package com.pharmaflow.auth_service.service.interfaces;

import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;

public interface PasswordTokenService {

    String issueSetPasswordToken(AuthUserEntity user);

    String issueResetPasswordToken(AuthUserEntity user);

    void consumeAndChangePassword(String token, String newRawPassword);
}
