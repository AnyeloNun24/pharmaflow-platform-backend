package com.pharmaflow.auth_service.service.implementation;

import com.pharmaflow.auth_service.config.properties.PasswordTokenProperties;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.entity.PasswordTokenEntity;
import com.pharmaflow.auth_service.persistence.entity.type.PasswordTokenType;
import com.pharmaflow.auth_service.persistence.repository.AuthUserRepository;
import com.pharmaflow.auth_service.persistence.repository.PasswordTokenRepository;
import com.pharmaflow.auth_service.service.interfaces.AuditLogService;
import com.pharmaflow.auth_service.service.interfaces.PasswordTokenService;
import com.pharmaflow.auth_service.service.interfaces.RefreshTokenService;
import com.pharmaflow.auth_service.util.TokenHasherUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordTokenServiceImpl implements PasswordTokenService {

    private final PasswordTokenRepository passwordTokenRepository;
    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenHasherUtils tokenHasherUtils;
    private final PasswordTokenProperties properties;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public String issueSetPasswordToken(AuthUserEntity user) {
        return this.issue(user, PasswordTokenType.SET_PASSWORD, this.properties.resolvedSetTtlMinutes());
    }

    @Override
    @Transactional
    public String issueResetPasswordToken(AuthUserEntity user) {
        return this.issue(user, PasswordTokenType.RESET_PASSWORD, this.properties.resolvedResetTtlMinutes());
    }

    @Override
    @Transactional
    public void consumeAndChangePassword(String rawToken, String newRawPassword) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new BadCredentialsException("Token requerido");
        }

        String hash = this.tokenHasherUtils.sha256Hex(rawToken);
        PasswordTokenEntity entity = this.passwordTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Token invalido"));

        Instant now = Instant.now();

        if (Boolean.TRUE.equals(entity.getUsed())) {
            throw new BadCredentialsException("Token ya consumido");
        }
        if (entity.getExpiryAt().isBefore(now)) {
            throw new BadCredentialsException("Token expirado");
        }

        AuthUserEntity user = entity.getUser();
        user.setPasswordHash(this.passwordEncoder.encode(newRawPassword));
        user.setPasswordChangedAt(now);
        user.setForcePasswordChange(false);
        user.setCredentialsExpired(false);
        user.setFailedAttempts((short) 0);
        if (Boolean.TRUE.equals(user.getAccountLocked())) {
            user.setAccountLocked(false);
            user.setLockedAt(null);
        }
        this.authUserRepository.save(user);

        entity.setUsed(true);
        entity.setUsedAt(now);
        this.passwordTokenRepository.save(entity);

        this.refreshTokenService.revokeAllForUser(user.getIdUser());

        AuditLogService.ActionType action = entity.getType() == PasswordTokenType.SET_PASSWORD
                ? AuditLogService.ActionType.SET_PASSWORD
                : AuditLogService.ActionType.PASSWORD_CHANGED;
        this.auditLogService.recordSuccess(action, user,
                "Password actualizada via token tipo " + entity.getType());

        log.info("Password actualizada para usuario id={} (token type={})", user.getIdUser(), entity.getType());
    }

    private String issue(AuthUserEntity user, PasswordTokenType type, int ttlMinutes) {

        this.passwordTokenRepository.invalidatePreviousTokens(user.getIdUser(), type, Instant.now());

        String raw = this.tokenHasherUtils.generateRawToken();
        String hash = this.tokenHasherUtils.sha256Hex(raw);

        PasswordTokenEntity entity = PasswordTokenEntity.builder()
                .user(user)
                .tokenHash(hash)
                .type(type)
                .expiryAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES))
                .build();

        this.passwordTokenRepository.save(entity);
        log.info("Emitido password token (type={}) para usuario id={}", type, user.getIdUser());
        return raw;
    }
}
