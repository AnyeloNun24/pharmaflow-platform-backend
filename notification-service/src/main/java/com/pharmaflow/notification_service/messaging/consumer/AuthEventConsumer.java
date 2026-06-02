package com.pharmaflow.notification_service.messaging.consumer;

import com.pharmaflow.notification_service.messaging.event.AuthDomainEvent;
import com.pharmaflow.notification_service.service.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics    = "${notification.kafka.topics.auth-events:auth.user.events}",
            groupId   = "${spring.kafka.consumer.group-id:notification-service}",
            containerFactory = "authListenerContainerFactory"
    )
    public void consume(AuthDomainEvent event) {
        log.info("Evento de auth recibido: {}", event.getClass().getSimpleName());
        notificationService.sendMessage(event);
    }
}
