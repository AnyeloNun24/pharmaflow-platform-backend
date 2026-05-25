package com.pharmaflow.auth_service.service.interfaces;

public interface FailedAttemptService {

    void onLoginSuccess(String email);

    void onLoginFailure(String email);
}
