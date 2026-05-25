package com.pharmaflow.auth_service.service.implementation;

import com.pharmaflow.auth_service.config.properties.PasswordTokenProperties;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.entity.PasswordTokenEntity;
import com.pharmaflow.auth_service.persistence.repository.AuthUserRepository;
import com.pharmaflow.auth_service.persistence.repository.PasswordTokenRepository;
import com.pharmaflow.auth_service.service.interfaces.PasswordTokenService;
import com.pharmaflow.auth_service.service.interfaces.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordTokenServiceImpl implements PasswordTokenService {

    private final PasswordTokenRepository passwordTokenRepository;
    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordTokenProperties properties;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional
    public String issueSetPasswordToken(AuthUserEntity user) {
        return this.issue(user, PasswordTokenEntity.TYPE_SET_PASSWORD, this.properties.setTtlMinutes());
    }

    @Override
    @Transactional
    public String issueResetPasswordToken(AuthUserEntity user) {
        return this.issue(user, PasswordTokenEntity.TYPE_RESET_PASSWORD, this.properties.resetTtlMinutes());
    }

    @Override
    @Transactional
    public void consumeAndChangePassword(String token, String newRawPassword) {

        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("Token requerido");
        }

        PasswordTokenEntity entity = this.passwordTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadCredentialsException("Token invalido"));

        OffsetDateTime now = OffsetDateTime.now();

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

        log.info("Password actualizada para usuario id={} (token type={})", user.getIdUser(), entity.getType());
    }

    private String issue(AuthUserEntity user, String type, int ttlMinutes) {

        this.passwordTokenRepository.invalidatePreviousTokens(user.getIdUser(), type, OffsetDateTime.now());

        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");

        PasswordTokenEntity entity = PasswordTokenEntity.builder()
                .user(user)
                .token(token)
                .type(type)
                .expiryAt(OffsetDateTime.now().plusMinutes(ttlMinutes))
                .build();

        this.passwordTokenRepository.save(entity);
        log.info("Emitido password token (type={}) para usuario id={}", type, user.getIdUser());
        return token;
    }
}
