package com.pharmaflow.auth_service.service.implementation;

import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.service.event.AuditLogEvent;
import com.pharmaflow.auth_service.service.interfaces.AuditLogService;
import com.pharmaflow.auth_service.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
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
    private static final int MAX_DESCRIPTION = 300;
    private static final int MAX_USER_AGENT = 255;
    private static final int MAX_FAILURE_REASON = 255;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void recordSuccess(ActionType action, AuthUserEntity user, String description) {
        this.publish(action, user, description, true, null);
    }

    @Override
    public void recordFailure(ActionType action, AuthUserEntity user, String description, String failureReason) {
        this.publish(action, user, description, false, failureReason);
    }

    private void publish(ActionType action, AuthUserEntity user, String description, boolean success, String failureReason) {
        HttpServletRequest request = currentRequest();
        String ip = request != null ? RequestUtils.resolveClientIp(request) : FALLBACK_IP;
        String ua = request != null ? RequestUtils.resolveUserAgent(request) : null;

        AuditLogEvent event = new AuditLogEvent(
                action,
                user != null ? user.getIdUser() : null,
                truncate(description, MAX_DESCRIPTION),
                success,
                truncate(failureReason, MAX_FAILURE_REASON),
                resolveRequestId(),
                ip,
                truncate(ua, MAX_USER_AGENT)
        );

        this.eventPublisher.publishEvent(event);
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
