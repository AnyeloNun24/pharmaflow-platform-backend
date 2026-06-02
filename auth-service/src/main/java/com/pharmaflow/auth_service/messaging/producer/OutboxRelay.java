package com.pharmaflow.auth_service.messaging.producer;

import com.pharmaflow.auth_service.config.filter.RequestIdFilter;
import com.pharmaflow.auth_service.persistence.entity.OutboxEventEntity;
import com.pharmaflow.auth_service.persistence.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> outboxKafkaTemplate;

    @Scheduled(fixedDelayString = "${auth.outbox.poll-delay-ms:5000}")
    @Transactional
    public void publishPending() {
        List<OutboxEventEntity> pending = this.outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) {
            return;
        }

        for (OutboxEventEntity event : pending) {
            try {
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());

                if (event.getRequestId() != null) {
                    record.headers().add(
                            RequestIdFilter.HEADER_REQUEST_ID,
                            event.getRequestId().getBytes(StandardCharsets.UTF_8));
                }

                this.outboxKafkaTemplate.send(record).get();
                event.setPublishedAt(Instant.now());

            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Outbox: relay interrumpido; se reintentara en el siguiente ciclo");
                break;
            } catch (Exception ex) {
                log.error("Outbox: fallo publicando evento id={}; se reintentara. Causa: {}",
                        event.getId(), ex.getMessage());
                break;
            }
        }
    }
}
