package com.pharmaflow.auth_service.service.implementation;

import com.pharmaflow.auth_service.persistence.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Revoca una familia completa de refresh tokens en una transaccion independiente.
 *
 * <p>Vive en un bean aparte de {@link RefreshTokenServiceImpl} a proposito: cuando se
 * detecta reuso, el flujo llamante revoca la familia y acto seguido lanza una excepcion
 * que hace rollback de su propia transaccion. Si la revocacion corriera en esa misma
 * transaccion, el rollback la desharia y la medida de seguridad no tendria efecto.
 *
 * <p>Al cruzar el limite de bean, el proxy AOP de Spring intercepta la llamada y, gracias a
 * {@link Propagation#REQUIRES_NEW}, abre una transaccion propia que commitea de forma
 * independiente del rollback del llamante.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenFamilyRevoker {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revoke(UUID tokenFamily) {
        int revoked = this.refreshTokenRepository.revokeAllByFamily(tokenFamily, Instant.now());
        log.warn("Familia de refresh tokens {} revocada por completo ({} tokens activos invalidados).",
                tokenFamily, revoked);
    }
}
