package com.pharmaflow.auth_service.service.implementation;

import com.pharmaflow.auth_service.config.properties.JwtProperties;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.entity.RefreshTokenEntity;
import com.pharmaflow.auth_service.persistence.repository.RefreshTokenRepository;
import com.pharmaflow.auth_service.service.exception.RefreshTokenReusedException;
import com.pharmaflow.auth_service.service.interfaces.AuditLogService;
import com.pharmaflow.auth_service.service.interfaces.RefreshTokenService;
import com.pharmaflow.auth_service.util.TokenHasherUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHasherUtils tokenHasherUtils;
    private final JwtProperties jwtProperties;
    private final AuditLogService auditLogService;
    private final RefreshTokenFamilyRevoker familyRevoker;

    @Override
    @Transactional
    public IssuedToken issueForUser(AuthUserEntity user, String ipAddress, String userAgent) {
        Instant absoluteExpiry = Instant.now().plus(this.jwtProperties.refreshAbsoluteTtlDays(), ChronoUnit.DAYS);
        return this.persist(user, UUID.randomUUID(), absoluteExpiry, ipAddress, userAgent);
    }

    @Override
    @Transactional
    public RefreshTokenEntity validateAndConsume(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new BadCredentialsException("Refresh token requerido");
        }

        String hash = this.tokenHasherUtils.sha256Hex(rawToken);
        RefreshTokenEntity entity = this.refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token invalido"));

        Instant now = Instant.now();

        if (Boolean.TRUE.equals(entity.getRevoked())) {
            log.warn("Reuso detectado del refresh token (family={}). Revocando familia.", entity.getTokenFamily());
            this.auditLogService.recordFailure(
                    AuditLogService.ActionType.REFRESH_TOKEN_REUSE,
                    entity.getUser(),
                    "Reuso de refresh token detectado, familia " + entity.getTokenFamily() + " revocada",
                    "Refresh token revocado reutilizado");
            this.familyRevoker.revoke(entity.getTokenFamily());
            throw new RefreshTokenReusedException(
                    "Refresh token reutilizado o ya revocado. La sesion fue invalidada como medida de seguridad.");
        }

        if (entity.getAbsoluteExpiryAt().isBefore(now)) {
            throw new BadCredentialsException("Sesion expirada, reautenticacion requerida");
        }

        if (entity.getExpiryAt().isBefore(now)) {
            throw new BadCredentialsException("Refresh token expirado");
        }

        return entity;
    }

    @Override
    @Transactional
    public IssuedToken rotate(RefreshTokenEntity current, String ipAddress, String userAgent) {

        IssuedToken next = this.persist(
                current.getUser(),
                current.getTokenFamily(),
                current.getAbsoluteExpiryAt(),
                ipAddress,
                userAgent);

        Instant now = Instant.now();
        current.setRevoked(true);
        current.setRevokedAt(now);
        current.setReplacedByHash(next.entity().getTokenHash());
        this.refreshTokenRepository.save(current);

        return next;
    }

    @Override
    @Transactional
    public AuthUserEntity revokeAndReturnUser(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadCredentialsException("Refresh token requerido");
        }

        String hash = this.tokenHasherUtils.sha256Hex(rawToken);
        RefreshTokenEntity entity = this.refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token invalido"));

        if (Boolean.TRUE.equals(entity.getRevoked())) {
            log.warn("Logout sobre refresh token ya revocado (family={}). Revocando familia.", entity.getTokenFamily());
            this.auditLogService.recordFailure(
                    AuditLogService.ActionType.REFRESH_TOKEN_REUSE,
                    entity.getUser(),
                    "Logout sobre refresh token revocado, familia " + entity.getTokenFamily() + " revocada",
                    "Refresh token revocado reutilizado en logout");
            this.familyRevoker.revoke(entity.getTokenFamily());
            throw new RefreshTokenReusedException(
                    "Refresh token reutilizado o ya revocado. La sesion fue invalidada como medida de seguridad.");
        }

        entity.setRevoked(true);
        entity.setRevokedAt(Instant.now());
        this.refreshTokenRepository.save(entity);
        return entity.getUser();
    }

    @Override
    @Transactional
    public void revokeAllForUser(Long idUser) {
        int updated = this.refreshTokenRepository.revokeAllByUser(idUser, Instant.now());
        log.info("Revocados {} tokens del usuario {}", updated, idUser);
    }

    private IssuedToken persist(AuthUserEntity user,
                                UUID family,
                                Instant absoluteExpiryAt,
                                String ipAddress,
                                String userAgent
    ) {

        String raw = this.tokenHasherUtils.generateRawToken();
        String hash = this.tokenHasherUtils.sha256Hex(raw);

        Instant slidingExpiry = Instant.now().plus(this.jwtProperties.refreshTtlDays(), ChronoUnit.DAYS);
        Instant expiryAt = slidingExpiry.isAfter(absoluteExpiryAt) ? absoluteExpiryAt : slidingExpiry;

        RefreshTokenEntity entity = RefreshTokenEntity.builder()
                .user(user)
                .tokenHash(hash)
                .tokenFamily(family)
                .ipAddress(ipAddress)
                .userAgent(truncate(userAgent, 512))
                .expiryAt(expiryAt)
                .absoluteExpiryAt(absoluteExpiryAt)
                .build();

        entity = this.refreshTokenRepository.save(entity);
        return new IssuedToken(raw, entity);
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}
