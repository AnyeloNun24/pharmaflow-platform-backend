package com.pharmaflow.auth_service.config.security;

import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.repository.AuthUserRepository;
import com.pharmaflow.auth_service.service.interfaces.AuditLogService;
import com.pharmaflow.auth_service.service.interfaces.FailedAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureExpiredEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationEventListener {

    private final FailedAttemptService failedAttemptService;
    private final AuditLogService auditLogService;
    private final AuthUserRepository authUserRepository;

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String email = event.getAuthentication().getName();
        this.failedAttemptService.onLoginSuccess(email);
        this.findUser(email).ifPresent(user ->
                this.auditLogService.recordSuccess(
                        AuditLogService.ActionType.LOGIN,
                        user,
                        "Login exitoso"
                ));
    }

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String email = event.getAuthentication().getName();
        boolean userNotFound = event.getException() instanceof UsernameNotFoundException
                || event.getException().getCause() instanceof UsernameNotFoundException;

        if (!userNotFound) {
            this.failedAttemptService.onLoginFailure(email);
        }

        AuthUserEntity user = this.findUser(email).orElse(null);
        this.auditLogService.recordFailure(
                AuditLogService.ActionType.LOGIN_FAILED,
                user,
                "Login fallido para email=" + email,
                userNotFound ? "Usuario no encontrado" : "Credenciales invalidas"
        );
    }

    @EventListener
    public void onLocked(AuthenticationFailureLockedEvent event) {
        this.auditLog(event.getAuthentication().getName(),
                "Login rechazado: cuenta bloqueada",
                "Cuenta bloqueada");
    }

    @EventListener
    public void onDisabled(AuthenticationFailureDisabledEvent event) {
        this.auditLog(event.getAuthentication().getName(),
                "Login rechazado: cuenta deshabilitada",
                "Cuenta deshabilitada");
    }

    @EventListener
    public void onExpired(AuthenticationFailureExpiredEvent event) {
        this.auditLog(event.getAuthentication().getName(),
                "Login rechazado: cuenta expirada",
                "Cuenta expirada");
    }

    private void auditLog(String email, String description, String reason) {
        AuthUserEntity user = this.findUser(email).orElse(null);
        this.auditLogService.recordFailure(
                AuditLogService.ActionType.LOGIN_FAILED, user, description, reason);
    }

    private java.util.Optional<AuthUserEntity> findUser(String email) {
        if (email == null || email.isBlank()) return java.util.Optional.empty();
        try {
            return this.authUserRepository.findByEmailIgnoreCase(email);
        } catch (Exception ex) {
            log.warn("No se pudo localizar usuario para audit: {}", ex.getMessage());
            return java.util.Optional.empty();
        }
    }
}
