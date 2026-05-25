package com.pharmaflow.auth_service.service.implementation;

import com.pharmaflow.auth_service.config.properties.JwtProperties;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.entity.RefreshTokenEntity;
import com.pharmaflow.auth_service.persistence.repository.RefreshTokenRepository;
import com.pharmaflow.auth_service.service.interfaces.RefreshTokenService;
import com.pharmaflow.auth_service.util.TokenHasherUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHasherUtils tokenHasherUtils;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional
    public IssuedToken issueForUser(AuthUserEntity user, String ipAddress, String userAgent) {
        return this.persist(user, UUID.randomUUID(), ipAddress, userAgent);
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshTokenEntity validateAndConsume(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new BadCredentialsException("Refresh token requerido");
        }

        String hash = this.tokenHasherUtils.sha256Hex(rawToken);
        RefreshTokenEntity entity = this.refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token invalido"));

        OffsetDateTime now = OffsetDateTime.now();

        if (Boolean.TRUE.equals(entity.getRevoked())) {
            log.warn("Reuso detectado del refresh token (family={}). Revocando familia.", entity.getTokenFamily());
            this.revokeFamily(entity.getTokenFamily());
            throw new BadCredentialsException("Refresh token revocado");
        }

        if (entity.getExpiryAt().isBefore(now)) {
            throw new BadCredentialsException("Refresh token expirado");
        }

        return entity;
    }

    @Override
    @Transactional
    public IssuedToken rotate(RefreshTokenEntity current, String ipAddress, String userAgent) {

        IssuedToken next = this.persist(current.getUser(), current.getTokenFamily(), ipAddress, userAgent);

        OffsetDateTime now = OffsetDateTime.now();
        current.setRevoked(true);
        current.setRevokedAt(now);
        current.setReplacedByHash(next.entity().getTokenHash());
        this.refreshTokenRepository.save(current);

        return next;
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        String hash = this.tokenHasherUtils.sha256Hex(rawToken);
        this.refreshTokenRepository.findByTokenHash(hash).ifPresent(entity -> {
            if (Boolean.FALSE.equals(entity.getRevoked())) {
                entity.setRevoked(true);
                entity.setRevokedAt(OffsetDateTime.now());
                this.refreshTokenRepository.save(entity);
            }
        });
    }

    @Override
    @Transactional
    public void revokeFamily(UUID family) {
        int updated = this.refreshTokenRepository.revokeAllByFamily(family, OffsetDateTime.now());
        log.info("Revocados {} tokens de la familia {}", updated, family);
    }

    @Override
    @Transactional
    public void revokeAllForUser(Long idUser) {
        int updated = this.refreshTokenRepository.revokeAllByUser(idUser, OffsetDateTime.now());
        log.info("Revocados {} tokens del usuario {}", updated, idUser);
    }

    private IssuedToken persist(AuthUserEntity user, UUID family, String ipAddress, String userAgent) {

        String raw = this.tokenHasherUtils.generateRawToken();
        String hash = this.tokenHasherUtils.sha256Hex(raw);

        OffsetDateTime expiryAt = OffsetDateTime.now().plusDays(this.jwtProperties.refreshTtlDays());

        RefreshTokenEntity entity = RefreshTokenEntity.builder()
                .user(user)
                .tokenHash(hash)
                .tokenFamily(family)
                .ipAddress(ipAddress)
                .userAgent(truncate(userAgent, 512))
                .expiryAt(expiryAt)
                .build();

        entity = this.refreshTokenRepository.save(entity);
        return new IssuedToken(raw, entity);
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}
