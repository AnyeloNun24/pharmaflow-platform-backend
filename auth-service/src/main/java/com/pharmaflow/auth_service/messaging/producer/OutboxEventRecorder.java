package com.pharmaflow.auth_service.messaging.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmaflow.auth_service.config.filter.RequestIdFilter;
import com.pharmaflow.auth_service.messaging.event.AuthDomainEvent;
import com.pharmaflow.auth_service.persistence.entity.OutboxEventEntity;
import com.pharmaflow.auth_service.persistence.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${auth.kafka.topics.user-events:auth.user.events}")
    private String userEventsTopic;

    @EventListener
    public void record(AuthDomainEvent event) {
        try {
            String payload = this.objectMapper.writeValueAsString(event);

            this.outboxRepository.save(OutboxEventEntity.builder()
                    .eventType(event.getClass().getSimpleName())
                    .topic(this.userEventsTopic)
                    .messageKey(String.valueOf(event.userId()))
                    .payload(payload)
                    .requestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                    .build());

            log.debug("Outbox: evento {} registrado para userId={}", event.getClass().getSimpleName(), event.userId());

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el evento " + event.getClass().getSimpleName(), ex);
        }
    }
}
