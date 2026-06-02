package com.pharmaflow.auth_service.messaging.producer;

import com.pharmaflow.auth_service.messaging.event.AuthDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEventKafkaPublisher {

    private final KafkaTemplate<String, AuthDomainEvent> authEventKafkaTemplate;

    @Value("${auth.kafka.topics.user-events:auth.user.events}")
    private String userEventsTopic;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(AuthDomainEvent event) {
        String key = String.valueOf(event.userId());

        this.authEventKafkaTemplate.send(this.userEventsTopic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Fallo al publicar {} (userId={}) en topic={}: {}",
                                event.getClass().getSimpleName(), event.userId(), this.userEventsTopic, ex.getMessage());
                    } else {
                        log.info("Evento publicado: {} userId={} topic={} particion={} offset={}",
                                event.getClass().getSimpleName(), event.userId(), this.userEventsTopic,
                                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }
}
