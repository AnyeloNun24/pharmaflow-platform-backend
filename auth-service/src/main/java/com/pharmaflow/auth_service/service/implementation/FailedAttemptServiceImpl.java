package com.pharmaflow.auth_service.service.implementation;

import com.pharmaflow.auth_service.config.properties.SecurityPolicyProperties;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.repository.AuthUserRepository;
import com.pharmaflow.auth_service.service.interfaces.AuditLogService;
import com.pharmaflow.auth_service.service.interfaces.FailedAttemptService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FailedAttemptServiceImpl implements FailedAttemptService {

    private final AuthUserRepository authUserRepository;
    private final SecurityPolicyProperties policyProperties;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLoginSuccess(String email) {
        this.authUserRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            user.setFailedAttempts((short) 0);
            user.setLastLoginAt(Instant.now());
            this.authUserRepository.save(user);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLoginFailure(String email) {
        this.authUserRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (Boolean.TRUE.equals(user.getAccountLocked())) {
                return;
            }
            short next = (short) (user.getFailedAttempts() + 1);
            user.setFailedAttempts(next);

            int max = this.policyProperties.resolvedMaxFailedAttempts();
            if (next >= max) {
                user.setAccountLocked(true);
                user.setLockedAt(Instant.now());
                log.warn("Cuenta bloqueada por superar el limite de intentos fallidos: id={}, attempts={}",
                        user.getIdUser(), next);
                this.authUserRepository.save(user);
                this.auditLogService.recordSuccess(
                        AuditLogService.ActionType.ACCOUNT_LOCKED,
                        user,
                        "Cuenta bloqueada tras " + next + " intentos fallidos"
                );
                return;
            }
            this.authUserRepository.save(user);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tryAutoUnlock(String email) {
        if (email == null || email.isBlank()) return;
        this.authUserRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (!Boolean.TRUE.equals(user.getAccountLocked()) || user.getLockedAt() == null) {
                return;
            }
            int durationMinutes = this.policyProperties.resolvedLockoutDurationMinutes();
            Instant unlockAt = user.getLockedAt().plus(durationMinutes, ChronoUnit.MINUTES);

            if (Instant.now().isBefore(unlockAt)) {
                return;
            }

            user.setAccountLocked(false);
            user.setLockedAt(null);
            user.setFailedAttempts((short) 0);
            this.authUserRepository.save(user);

            log.info("Desbloqueo automatico de cuenta id={} tras {} minutos",
                    user.getIdUser(), durationMinutes);
            this.auditLogService.recordSuccess(
                    AuditLogService.ActionType.ACCOUNT_UNLOCKED,
                    user,
                    "Desbloqueo automatico tras " + durationMinutes + " minutos"
            );
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forceUnlock(Long idUser, String actorEmail) {
        AuthUserEntity user = this.authUserRepository.findById(idUser)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: id=" + idUser));

        boolean wasLocked = Boolean.TRUE.equals(user.getAccountLocked());
        user.setAccountLocked(false);
        user.setLockedAt(null);
        user.setFailedAttempts((short) 0);
        this.authUserRepository.save(user);

        String actor = actorEmail != null ? actorEmail : "desconocido";
        log.info("Desbloqueo manual de cuenta id={} por actor={} (estabaBloqueada={})",
                user.getIdUser(), actor, wasLocked);
        this.auditLogService.recordSuccess(
                AuditLogService.ActionType.ACCOUNT_UNLOCKED,
                user,
                "Desbloqueo manual por admin=" + actor + " (estabaBloqueada=" + wasLocked + ")"
        );
    }
}