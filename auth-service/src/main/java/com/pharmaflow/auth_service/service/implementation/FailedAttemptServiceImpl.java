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
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FailedAttemptServiceImpl implements FailedAttemptService {

    private final AuthUserRepository authUserRepository;
    private final SecurityPolicyProperties policyProperties;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AuthUserEntity> onLoginSuccess(String email) {
        Optional<AuthUserEntity> userOpt = this.authUserRepository.findByEmailIgnoreCase(email);
        userOpt.ifPresent(user ->
                this.authUserRepository.resetFailedAttemptsAndStampLogin(user.getIdUser(), Instant.now()));
        return userOpt;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AuthUserEntity> onLoginFailure(String email) {
        Optional<AuthUserEntity> userOpt = this.authUserRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) return userOpt;

        AuthUserEntity user = userOpt.get();
        if (Boolean.TRUE.equals(user.getAccountLocked())) return userOpt;

        Instant now = Instant.now();

        Integer newCount = this.authUserRepository.incrementFailedAttemptsAndReturn(user.getIdUser(), now);
        if (newCount == null) {
            return userOpt;
        }

        int max = this.policyProperties.resolvedMaxFailedAttempts();
        if (newCount >= max) {
            int locked = this.authUserRepository.lockAccount(user.getIdUser(), now);
            if (locked > 0) {
                log.warn("Cuenta bloqueada por superar el limite de intentos fallidos: id={}, attempts={}",
                        user.getIdUser(), newCount);
                this.auditLogService.recordSuccess(
                        AuditLogService.ActionType.ACCOUNT_LOCKED,
                        user,
                        "Cuenta bloqueada tras " + newCount + " intentos fallidos"
                );
            }
        }
        return userOpt;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tryAutoUnlock(String email) {
        if (email == null || email.isBlank()) return;
        this.authUserRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            // Si cuenta no esta bloqueada o su fecha de bloqueo es nula, no hace nada
            if (!Boolean.TRUE.equals(user.getAccountLocked()) || user.getLockedAt() == null) {
                return;
            }
            int durationMinutes = this.policyProperties.resolvedLockoutDurationMinutes();
            Instant unlockAt = user.getLockedAt().plus(durationMinutes, ChronoUnit.MINUTES);

            if (Instant.now().isBefore(unlockAt)) {
                return;
            }

            this.authUserRepository.unlockAccount(user.getIdUser(), Instant.now());

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
        this.authUserRepository.unlockAccount(idUser, Instant.now());

        String actor = actorEmail != null ? actorEmail : "desconocido";
        log.info("Desbloqueo manual de cuenta id={} por actor={} (estabaBloqueada={})",
                idUser, actor, wasLocked);
        this.auditLogService.recordSuccess(
                AuditLogService.ActionType.ACCOUNT_UNLOCKED,
                user,
                "Desbloqueo manual por admin=" + actor + " (estabaBloqueada=" + wasLocked + ")"
        );
    }
}
