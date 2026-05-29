package com.pharmaflow.auth_service.service.interfaces;

public interface FailedAttemptService {

    void onLoginSuccess(String email);

    void onLoginFailure(String email);

    void tryAutoUnlock(String email);

    void forceUnlock(Long idUser, String actorEmail);
}