package com.pharmaflow.auth_service.service.implementation;

import com.pharmaflow.auth_service.config.security.CustomUserDetails;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.entity.RefreshTokenEntity;
import com.pharmaflow.auth_service.persistence.repository.AuthUserRepository;
import com.pharmaflow.auth_service.presentation.dto.request.RequestForgotPasswordDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestLoginDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestRefreshDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestSetPasswordDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseLoginDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseRefreshDto;
import com.pharmaflow.auth_service.service.interfaces.AuditLogService;
import com.pharmaflow.auth_service.service.interfaces.FailedAttemptService;
import com.pharmaflow.auth_service.service.interfaces.PasswordTokenService;
import com.pharmaflow.auth_service.service.interfaces.RefreshTokenService;
import com.pharmaflow.auth_service.util.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl")
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserDetailsServiceImpl userDetailsService;
    @Mock
    private AuthUserRepository authUserRepository;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private PasswordTokenService passwordTokenService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private FailedAttemptService failedAttemptService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(
                authenticationManager, userDetailsService, authUserRepository, jwtUtils,
                refreshTokenService, passwordTokenService, auditLogService, failedAttemptService,
                eventPublisher);
    }

    private CustomUserDetails userDetails(AuthUserEntity user) {
        return new CustomUserDetails(
                user.getEmail(), "hash", true, true, true, true,
                Set.of(), user.getIdUser(), Set.of("ADMIN"));
    }

    private AuthUserEntity user() {
        return AuthUserEntity.builder()
                .idUser(7L).email("u@x.com")
                .active(true).accountLocked(false).accountExpired(false)
                .build();
    }

    @Test
    @DisplayName("login: intenta autounlock, autentica, emite access + refresh")
    void login_happyPath() {
        AuthUserEntity user = user();
        CustomUserDetails details = userDetails(user);
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(details);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(authUserRepository.findById(7L)).thenReturn(Optional.of(user));
        when(jwtUtils.generateAccessToken(details)).thenReturn("access-jwt");
        RefreshTokenEntity rt = RefreshTokenEntity.builder().user(user).build();
        when(refreshTokenService.issueForUser(eq(user), anyString(), anyString()))
                .thenReturn(new RefreshTokenService.IssuedToken("raw-refresh", rt));

        ResponseLoginDto response = service.login(
                new RequestLoginDto("u@x.com", "pwd"), "1.1.1.1", "agent");

        assertThat(response.accessToken()).isEqualTo("access-jwt");
        assertThat(response.refreshToken()).isEqualTo("raw-refresh");
        assertThat(response.userId()).isEqualTo(7L);
        verify(failedAttemptService).tryAutoUnlock("u@x.com");
    }

    @Test
    @DisplayName("refresh: cuenta inactiva revoca todas las sesiones y lanza DisabledException")
    void refresh_disabledAccount() {
        AuthUserEntity user = user();
        user.setActive(false);
        RefreshTokenEntity current = RefreshTokenEntity.builder().user(user).build();
        when(refreshTokenService.validateAndConsume("raw")).thenReturn(current);
        when(authUserRepository.findById(7L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.refresh(new RequestRefreshDto("raw"), "ip", "ua"))
                .isInstanceOf(DisabledException.class);

        verify(refreshTokenService).revokeAllForUser(7L);
        verify(auditLogService).recordFailure(
                eq(AuditLogService.ActionType.REFRESH_TOKEN), eq(user), anyString(), anyString());
        verify(refreshTokenService, never()).rotate(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("refresh: cuenta valida rota el token y audita REFRESH_TOKEN")
    void refresh_happyPath() {
        AuthUserEntity user = user();
        CustomUserDetails details = userDetails(user);
        RefreshTokenEntity current = RefreshTokenEntity.builder().user(user).build();
        when(refreshTokenService.validateAndConsume("raw")).thenReturn(current);
        when(authUserRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userDetailsService.loadUserDetailsFor(user)).thenReturn(details);
        RefreshTokenEntity rotated = RefreshTokenEntity.builder().user(user).build();
        when(refreshTokenService.rotate(eq(current), anyString(), anyString()))
                .thenReturn(new RefreshTokenService.IssuedToken("new-refresh", rotated));
        when(jwtUtils.generateAccessToken(details)).thenReturn("new-access");

        ResponseRefreshDto response = service.refresh(new RequestRefreshDto("raw"), "ip", "ua");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(auditLogService).recordSuccess(
                eq(AuditLogService.ActionType.REFRESH_TOKEN), eq(user), anyString());
    }

    @Test
    @DisplayName("logout: revoca el token y audita LOGOUT")
    void logout_audits() {
        AuthUserEntity user = user();
        when(refreshTokenService.revokeAndReturnUser("raw")).thenReturn(user);

        service.logout("raw");

        verify(auditLogService).recordSuccess(
                eq(AuditLogService.ActionType.LOGOUT), eq(user), anyString());
    }

    @Test
    @DisplayName("forgotPassword: email no registrado no emite token ni audita (sin enumeracion)")
    void forgotPassword_unknownEmail() {
        when(authUserRepository.findByEmailIgnoreCase("x@x.com")).thenReturn(Optional.empty());

        service.forgotPassword(new RequestForgotPasswordDto("x@x.com"));

        verify(passwordTokenService, never()).issueResetPasswordToken(any());
        verify(auditLogService, never()).recordSuccess(any(), any(), anyString());
    }

    @Test
    @DisplayName("forgotPassword: usuario inactivo no emite token")
    void forgotPassword_inactiveUser() {
        AuthUserEntity user = user();
        user.setActive(false);
        when(authUserRepository.findByEmailIgnoreCase("u@x.com")).thenReturn(Optional.of(user));

        service.forgotPassword(new RequestForgotPasswordDto("u@x.com"));

        verify(passwordTokenService, never()).issueResetPasswordToken(any());
    }

    @Test
    @DisplayName("forgotPassword: usuario activo emite token RESET_PASSWORD y audita")
    void forgotPassword_activeUser() {
        AuthUserEntity user = user();
        when(authUserRepository.findByEmailIgnoreCase("u@x.com")).thenReturn(Optional.of(user));
        when(passwordTokenService.issueResetPasswordToken(user)).thenReturn("reset-token");

        service.forgotPassword(new RequestForgotPasswordDto("u@x.com"));

        verify(passwordTokenService).issueResetPasswordToken(user);
        verify(auditLogService).recordSuccess(
                eq(AuditLogService.ActionType.RESET_PASSWORD), eq(user), anyString());
    }

    @Test
    @DisplayName("setPassword: delega en passwordTokenService.consumeAndChangePassword")
    void setPassword_delegates() {
        service.setPassword(new RequestSetPasswordDto("tok", "newpass"));

        verify(passwordTokenService).consumeAndChangePassword("tok", "newpass");
    }
}
