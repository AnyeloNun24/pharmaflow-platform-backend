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
import com.pharmaflow.auth_service.service.interfaces.AuthService;
import com.pharmaflow.auth_service.service.interfaces.FailedAttemptService;
import com.pharmaflow.auth_service.service.interfaces.PasswordTokenService;
import com.pharmaflow.auth_service.service.interfaces.RefreshTokenService;
import com.pharmaflow.auth_service.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;
    private final AuthUserRepository authUserRepository;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final PasswordTokenService passwordTokenService;
    private final AuditLogService auditLogService;
    private final FailedAttemptService failedAttemptService;

    @Override
    @Transactional
    public ResponseLoginDto login(RequestLoginDto request, String ipAddress, String userAgent) {

        this.failedAttemptService.tryAutoUnlock(request.email());

        Authentication authentication = this.authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        AuthUserEntity user = this.authUserRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado"));

        String accessToken = this.jwtUtils.generateAccessToken(userDetails);
        RefreshTokenService.IssuedToken refresh = this.refreshTokenService.issueForUser(user, ipAddress, userAgent);

        log.info("Login exitoso para usuario id={}", user.getIdUser());

        return ResponseLoginDto.of(userDetails, accessToken, refresh.rawToken());
    }

    @Override
    @Transactional
    public ResponseRefreshDto refresh(RequestRefreshDto request, String ipAddress, String userAgent) {

        RefreshTokenEntity current = this.refreshTokenService.validateAndConsume(request.refreshToken());
        AuthUserEntity user = current.getUser();

        this.failedAttemptService.tryAutoUnlock(user.getEmail());

        user = this.authUserRepository.findById(user.getIdUser()).orElse(user);

        if (!Boolean.TRUE.equals(user.getActive())
                || Boolean.TRUE.equals(user.getAccountLocked())
                || Boolean.TRUE.equals(user.getAccountExpired())) {
            this.refreshTokenService.revokeAllForUser(user.getIdUser());
            this.auditLogService.recordFailure(
                    AuditLogService.ActionType.REFRESH_TOKEN, user,
                    "Refresh rechazado: cuenta no disponible", "Cuenta no disponible");
            throw new DisabledException("Cuenta no disponible");
        }

        CustomUserDetails userDetails = this.userDetailsService.loadUserDetailsFor(user);
        RefreshTokenService.IssuedToken rotated = this.refreshTokenService.rotate(current, ipAddress, userAgent);
        String accessToken = this.jwtUtils.generateAccessToken(userDetails);

        this.auditLogService.recordSuccess(
                AuditLogService.ActionType.REFRESH_TOKEN, user, "Refresh token rotado");

        log.debug("Refresh exitoso para usuario id={}", user.getIdUser());
        return ResponseRefreshDto.of(accessToken, rotated.rawToken());
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        AuthUserEntity user = this.refreshTokenService.revokeAndReturnUser(refreshToken);
        this.auditLogService.recordSuccess(
                AuditLogService.ActionType.LOGOUT, user, "Sesion cerrada");
    }

    @Override
    @Transactional
    public void forgotPassword(RequestForgotPasswordDto request) {

        this.authUserRepository.findByEmailIgnoreCase(request.email())
                .ifPresentOrElse(user -> {
                    if (!Boolean.TRUE.equals(user.getActive())) {
                        log.info("forgot-password: usuario inactivo para email={}", request.email());
                        return;
                    }
                    String token = this.passwordTokenService.issueResetPasswordToken(user);
                    this.auditLogService.recordSuccess(
                            AuditLogService.ActionType.RESET_PASSWORD, user,
                            "Token RESET_PASSWORD emitido");
                    log.info("forgot-password: token emitido para email={} token={}", request.email(), token);
                    // TODO: publicar evento PasswordResetRequested al topic iam.user.events para que
                    //       notification-service envie el correo. No loguear el token en plano en prod.
                }, () -> log.info("forgot-password: email no registrado={}", request.email()));
    }

    @Override
    @Transactional
    public void setPassword(RequestSetPasswordDto request) {
        this.passwordTokenService.consumeAndChangePassword(request.token(), request.newPassword());
    }
}
