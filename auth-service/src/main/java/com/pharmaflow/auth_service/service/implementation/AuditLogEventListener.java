package com.pharmaflow.auth_service.service.implementation;

import com.pharmaflow.auth_service.persistence.entity.AuthAuditLogEntity;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.repository.AuthAuditLogRepository;
import com.pharmaflow.auth_service.service.event.AuditLogEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Persiste los audit logs en su propia transaccion.
 *
 * Hay dos fases distintas para no perder audits:
 *  - Eventos de exito  -> AFTER_COMMIT: solo escriben si la transaccion padre commitea.
 *                         Evita FK violations al referenciar entidades recien creadas en la misma tx.
 *  - Eventos de falla  -> AFTER_COMPLETION: escriben tambien si la tx padre hace rollback.
 *                         Critico para compliance: un BadCredentialsException en login NO debe
 *                         dejar de auditar el intento fallido.
 *
 * Ambos handlers reciben todos los AuditLogEvent y filtran por {@code event.success()}, asi que
 * cada evento es persistido por exactamente un handler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogEventListener {

    private final AuthAuditLogRepository auditLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Async("auditExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSuccessAudit(AuditLogEvent event) {
        if (!event.success()) return;
        this.persist(event);
    }

    @Async("auditExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFailureAudit(AuditLogEvent event) {
        if (event.success()) return;
        this.persist(event);
    }

    private void persist(AuditLogEvent event) {
        try {
            // getReference evita un SELECT a auth_user; solo necesitamos el FK.
            AuthUserEntity userRef = event.userId() != null
                    ? this.entityManager.getReference(AuthUserEntity.class, event.userId())
                    : null;

            AuthAuditLogEntity entity = AuthAuditLogEntity.builder()
                    .user(userRef)
                    .requestId(event.requestId())
                    .actionType(event.action().name())
                    .description(event.description())
                    .success(event.success())
                    .ipAddress(event.ipAddress())
                    .userAgent(event.userAgent())
                    .failureReason(event.failureReason())
                    .build();

            this.auditLogRepository.save(entity);
        } catch (Exception ex) {
            log.warn("No se pudo registrar audit log (action={}, success={}): {}",
                    event.action(), event.success(), ex.getMessage());
        }
    }
}
