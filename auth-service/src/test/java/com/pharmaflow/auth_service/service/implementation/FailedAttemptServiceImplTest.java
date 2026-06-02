package com.pharmaflow.auth_service.service.implementation;

import com.pharmaflow.auth_service.config.properties.SecurityPolicyProperties;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.repository.AuthUserRepository;
import com.pharmaflow.auth_service.service.interfaces.AuditLogService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FailedAttemptServiceImpl")
class FailedAttemptServiceImplTest {

    @Mock
    private AuthUserRepository authUserRepository;
    @Mock
    private AuditLogService auditLogService;

    private SecurityPolicyProperties policy;
    private FailedAttemptServiceImpl service;

    @BeforeEach
    void setUp() {
        policy = new SecurityPolicyProperties(5, 15);
        service = new FailedAttemptServiceImpl(authUserRepository, policy, auditLogService);
    }

    private AuthUserEntity user() {
        return AuthUserEntity.builder().idUser(3L).email("u@x.com").accountLocked(false).build();
    }

    @Test
    @DisplayName("onLoginFailure: usuario inexistente no incrementa nada")
    void onLoginFailure_userNotFound() {
        when(authUserRepository.findByEmailIgnoreCase("u@x.com")).thenReturn(Optional.empty());

        service.onLoginFailure("u@x.com");

        verify(authUserRepository, never()).incrementFailedAttemptsAndReturn(any(), any());
    }

    @Test
    @DisplayName("onLoginFailure: cuenta ya bloqueada no incrementa")
    void onLoginFailure_alreadyLocked() {
        AuthUserEntity user = user();
        user.setAccountLocked(true);
        when(authUserRepository.findByEmailIgnoreCase("u@x.com")).thenReturn(Optional.of(user));

        service.onLoginFailure("u@x.com");

        verify(authUserRepository, never()).incrementFailedAttemptsAndReturn(any(), any());
    }

    @Test
    @DisplayName("onLoginFailure: por debajo del maximo solo incrementa, no bloquea")
    void onLoginFailure_belowMax() {
        when(authUserRepository.findByEmailIgnoreCase("u@x.com")).thenReturn(Optional.of(user()));
        when(authUserRepository.incrementFailedAttemptsAndReturn(eq(3L), any())).thenReturn(3);

        service.onLoginFailure("u@x.com");

        verify(authUserRepository, never()).lockAccount(any(), any());
        verify(auditLogService, never()).recordSuccess(
                eq(AuditLogService.ActionType.ACCOUNT_LOCKED), any(), anyString());
    }

    @Test
    @DisplayName("onLoginFailure: al alcanzar el maximo bloquea la cuenta y audita")
    void onLoginFailure_locksAtMax() {
        when(authUserRepository.findByEmailIgnoreCase("u@x.com")).thenReturn(Optional.of(user()));
        when(authUserRepository.incrementFailedAttemptsAndReturn(eq(3L), any())).thenReturn(5);
        when(authUserRepository.lockAccount(eq(3L), any())).thenReturn(1);

        service.onLoginFailure("u@x.com");

        verify(authUserRepository).lockAccount(eq(3L), any());
        verify(auditLogService).recordSuccess(
                eq(AuditLogService.ActionType.ACCOUNT_LOCKED), any(), anyString());
    }

    @Test
    @DisplayName("onLoginFailure: si el UPDATE atomico devuelve null (carrera) no bloquea")
    void onLoginFailure_raceReturnsNull() {
        when(authUserRepository.findByEmailIgnoreCase("u@x.com")).thenReturn(Optional.of(user()));
        when(authUserRepository.incrementFailedAttemptsAndReturn(eq(3L), any())).thenReturn(null);

        service.onLoginFailure("u@x.com");

        verify(authUserRepository, never()).lockAccount(any(), any());
    }

    @Test
    @DisplayName("tryAutoUnlock: email en blanco no hace nada")
    void tryAutoUnlock_blank() {
        service.tryAutoUnlock("  ");
        verify(authUserRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    @DisplayName("tryAutoUnlock: cuenta no bloqueada no se toca")
    void tryAutoUnlock_notLocked() {
        when(authUserRepository.findByEmailIgnoreCase("u@x.com")).thenReturn(Optional.of(user()));

        service.tryAutoUnlock("u@x.com");

        verify(authUserRepository, never()).unlockAccount(any(), any());
    }

    @Test
    @DisplayName("tryAutoUnlock: aun dentro de la ventana de bloqueo no desbloquea")
    void tryAutoUnlock_notYetElapsed() {
        AuthUserEntity user = user();
        user.setAccountLocked(true);
        user.setLockedAt(Instant.now().minus(5, ChronoUnit.MINUTES)); // < 15 min
        when(authUserRepository.findByEmailIgnoreCase("u@x.com")).thenReturn(Optional.of(user));

        service.tryAutoUnlock("u@x.com");

        verify(authUserRepository, never()).unlockAccount(any(), any());
    }

    @Test
    @DisplayName("tryAutoUnlock: superada la ventana desbloquea y audita ACCOUNT_UNLOCKED")
    void tryAutoUnlock_elapsedUnlocks() {
        AuthUserEntity user = user();
        user.setAccountLocked(true);
        user.setLockedAt(Instant.now().minus(20, ChronoUnit.MINUTES)); // > 15 min
        when(authUserRepository.findByEmailIgnoreCase("u@x.com")).thenReturn(Optional.of(user));

        service.tryAutoUnlock("u@x.com");

        verify(authUserRepository).unlockAccount(eq(3L), any());
        verify(auditLogService).recordSuccess(
                eq(AuditLogService.ActionType.ACCOUNT_UNLOCKED), eq(user), anyString());
    }

    @Test
    @DisplayName("forceUnlock: usuario inexistente lanza EntityNotFoundException")
    void forceUnlock_notFound() {
        when(authUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forceUnlock(99L, "admin@x.com"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("forceUnlock: desbloquea y audita registrando el actor")
    void forceUnlock_success() {
        AuthUserEntity user = user();
        user.setAccountLocked(true);
        when(authUserRepository.findById(3L)).thenReturn(Optional.of(user));
        lenient().when(authUserRepository.unlockAccount(eq(3L), any())).thenReturn(1);

        service.forceUnlock(3L, "admin@x.com");

        verify(authUserRepository).unlockAccount(eq(3L), any());
        verify(auditLogService).recordSuccess(
                eq(AuditLogService.ActionType.ACCOUNT_UNLOCKED), eq(user), anyString());
    }
}
