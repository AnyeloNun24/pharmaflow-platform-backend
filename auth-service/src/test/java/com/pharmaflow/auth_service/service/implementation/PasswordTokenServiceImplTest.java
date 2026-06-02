package com.pharmaflow.auth_service.service.implementation;

import com.pharmaflow.auth_service.config.properties.PasswordTokenProperties;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.entity.PasswordTokenEntity;
import com.pharmaflow.auth_service.persistence.entity.type.PasswordTokenType;
import com.pharmaflow.auth_service.persistence.repository.AuthUserRepository;
import com.pharmaflow.auth_service.persistence.repository.PasswordTokenRepository;
import com.pharmaflow.auth_service.service.interfaces.AuditLogService;
import com.pharmaflow.auth_service.service.interfaces.RefreshTokenService;
import com.pharmaflow.auth_service.util.TokenHasherUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordTokenServiceImpl")
class PasswordTokenServiceImplTest {

    @Mock
    private PasswordTokenRepository passwordTokenRepository;
    @Mock
    private AuthUserRepository authUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenHasherUtils tokenHasherUtils;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AuditLogService auditLogService;

    private PasswordTokenServiceImpl service;
    private AuthUserEntity user;

    @BeforeEach
    void setUp() {
        PasswordTokenProperties properties = new PasswordTokenProperties(1440, 60);
        service = new PasswordTokenServiceImpl(
                passwordTokenRepository, authUserRepository, passwordEncoder,
                tokenHasherUtils, properties, refreshTokenService, auditLogService);
        user = AuthUserEntity.builder()
                .idUser(5L).email("u@x.com")
                .accountLocked(true).lockedAt(Instant.now())
                .forcePasswordChange(true).credentialsExpired(true)
                .failedAttempts((short) 4)
                .build();
    }

    private PasswordTokenEntity token(PasswordTokenType type, boolean used, Instant expiry) {
        return PasswordTokenEntity.builder()
                .idPasswordToken(1L).user(user).tokenHash("hash")
                .type(type).used(used).expiryAt(expiry)
                .build();
    }

    @Test
    @DisplayName("issueSetPasswordToken invalida los tokens previos y persiste el nuevo")
    void issueSetPasswordToken_invalidatesPrevious() {
        when(tokenHasherUtils.generateRawToken()).thenReturn("raw");
        when(tokenHasherUtils.sha256Hex("raw")).thenReturn("hash");

        String raw = service.issueSetPasswordToken(user);

        assertThat(raw).isEqualTo("raw");
        verify(passwordTokenRepository).invalidatePreviousTokens(
                eq(5L), eq(PasswordTokenType.SET_PASSWORD), any());
        verify(passwordTokenRepository).save(any(PasswordTokenEntity.class));
    }

    @Test
    @DisplayName("token en blanco lanza BadCredentialsException")
    void consume_blank() {
        assertThatThrownBy(() -> service.consumeAndChangePassword("", "newpass"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("token inexistente lanza BadCredentialsException")
    void consume_notFound() {
        when(tokenHasherUtils.sha256Hex("raw")).thenReturn("hash");
        when(passwordTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consumeAndChangePassword("raw", "newpass"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Token invalido");
    }

    @Test
    @DisplayName("token ya consumido lanza BadCredentialsException")
    void consume_alreadyUsed() {
        when(tokenHasherUtils.sha256Hex("raw")).thenReturn("hash");
        when(passwordTokenRepository.findByTokenHash("hash"))
                .thenReturn(Optional.of(token(PasswordTokenType.SET_PASSWORD, true,
                        Instant.now().plus(1, ChronoUnit.HOURS))));

        assertThatThrownBy(() -> service.consumeAndChangePassword("raw", "newpass"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("ya consumido");
    }

    @Test
    @DisplayName("token expirado lanza BadCredentialsException")
    void consume_expired() {
        when(tokenHasherUtils.sha256Hex("raw")).thenReturn("hash");
        when(passwordTokenRepository.findByTokenHash("hash"))
                .thenReturn(Optional.of(token(PasswordTokenType.SET_PASSWORD, false,
                        Instant.now().minus(1, ChronoUnit.HOURS))));

        assertThatThrownBy(() -> service.consumeAndChangePassword("raw", "newpass"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Token expirado");
    }

    @Test
    @DisplayName("consumo valido cambia password, limpia flags, desbloquea y revoca todas las sesiones")
    void consume_happyPath() {
        PasswordTokenEntity entity = token(PasswordTokenType.SET_PASSWORD, false,
                Instant.now().plus(1, ChronoUnit.HOURS));
        when(tokenHasherUtils.sha256Hex("raw")).thenReturn("hash");
        when(passwordTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(entity));
        when(passwordEncoder.encode("newpass")).thenReturn("encoded");

        service.consumeAndChangePassword("raw", "newpass");

        assertThat(user.getPasswordHash()).isEqualTo("encoded");
        assertThat(user.getPasswordChangedAt()).isNotNull();
        assertThat(user.getForcePasswordChange()).isFalse();
        assertThat(user.getCredentialsExpired()).isFalse();
        assertThat(user.getFailedAttempts()).isEqualTo((short) 0);
        assertThat(user.getAccountLocked()).isFalse();
        assertThat(user.getLockedAt()).isNull();
        assertThat(entity.getUsed()).isTrue();
        assertThat(entity.getUsedAt()).isNotNull();
        verify(refreshTokenService).revokeAllForUser(5L);
    }

    @Test
    @DisplayName("token SET_PASSWORD audita SET_PASSWORD")
    void consume_auditsSetPassword() {
        PasswordTokenEntity entity = token(PasswordTokenType.SET_PASSWORD, false,
                Instant.now().plus(1, ChronoUnit.HOURS));
        when(tokenHasherUtils.sha256Hex("raw")).thenReturn("hash");
        when(passwordTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(entity));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");

        service.consumeAndChangePassword("raw", "newpass");

        verify(auditLogService).recordSuccess(
                eq(AuditLogService.ActionType.SET_PASSWORD), eq(user), anyString());
        verify(auditLogService, never()).recordSuccess(
                eq(AuditLogService.ActionType.PASSWORD_CHANGED), any(), anyString());
    }

    @Test
    @DisplayName("token RESET_PASSWORD audita PASSWORD_CHANGED")
    void consume_auditsPasswordChanged() {
        PasswordTokenEntity entity = token(PasswordTokenType.RESET_PASSWORD, false,
                Instant.now().plus(1, ChronoUnit.HOURS));
        when(tokenHasherUtils.sha256Hex("raw")).thenReturn("hash");
        when(passwordTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(entity));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");

        service.consumeAndChangePassword("raw", "newpass");

        verify(auditLogService).recordSuccess(
                eq(AuditLogService.ActionType.PASSWORD_CHANGED), eq(user), anyString());
    }
}
