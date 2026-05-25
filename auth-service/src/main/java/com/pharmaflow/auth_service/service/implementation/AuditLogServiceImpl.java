package com.pharmaflow.auth_service.service.implementation;

import com.pharmaflow.auth_service.persistence.entity.AuthAuditLogEntity;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.repository.AuthAuditLogRepository;
import com.pharmaflow.auth_service.service.interfaces.AuditLogService;
import com.pharmaflow.auth_service.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    public static final String MDC_REQUEST_ID = "requestId";
    private static final String FALLBACK_IP = "0.0.0.0";

    private final AuthAuditLogRepository auditLogRepository;

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(ActionType action, AuthUserEntity user, String description) {
        this.persist(action, user, description, true, null);
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(ActionType action, AuthUserEntity user, String description, String failureReason) {
        this.persist(action, user, description, false, failureReason);
    }

    private void persist(ActionType action, AuthUserEntity user, String description, boolean success, String failureReason) {
        try {
            HttpServletRequest request = currentRequest();
            String ip = request != null ? RequestUtils.resolveClientIp(request) : FALLBACK_IP;
            String ua = request != null ? RequestUtils.resolveUserAgent(request) : null;

            AuthAuditLogEntity entity = AuthAuditLogEntity.builder()
                    .user(user)
                    .requestId(resolveRequestId())
                    .actionType(action.name())
                    .description(truncate(description, 300))
                    .success(success)
                    .ipAddress(ip)
                    .userAgent(truncate(ua, 255))
                    .failureReason(truncate(failureReason, 255))
                    .build();

            this.auditLogRepository.save(entity);
        } catch (Exception ex) {
            log.warn("No se pudo registrar audit log (action={}, success={}): {}",
                    action, success, ex.getMessage());
        }
    }

    private static UUID resolveRequestId() {
        String mdc = MDC.get(MDC_REQUEST_ID);
        if (mdc != null) {
            try {
                return UUID.fromString(mdc);
            } catch (IllegalArgumentException ignored) {}
        }
        return UUID.randomUUID();
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes sra) ? sra.getRequest() : null;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}
