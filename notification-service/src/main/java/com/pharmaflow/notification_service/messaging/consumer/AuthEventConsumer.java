package com.pharmaflow.notification_service.messaging.consumer;

import com.pharmaflow.notification_service.messaging.event.AuthDomainEvent;
import com.pharmaflow.notification_service.service.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consume los eventos de autenticacion y los delega al canal de notificacion activo.
 * <p>
 * Depende de la abstraccion {@link NotificationService}, no de una implementacion concreta:
 * el canal real (email, SMS, push...) se resuelve por configuracion sin tocar este consumer.
 * <p>
 * Propaga la correlacion: el {@code X-Request-Id} que viaja en el header del mensaje se coloca en el
 * MDC, de modo que los logs del envio del correo comparten el mismo id que la peticion HTTP original
 * en auth-service. Asi se puede seguir el flujo completo (HTTP -> auth -> Kafka -> correo).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEventConsumer {

    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final String MDC_REQUEST_ID = "requestId";

    private final NotificationService notificationService;

    @KafkaListener(
            topics    = "${notification.kafka.topics.auth-events:auth.user.events}",
            groupId   = "${spring.kafka.consumer.group-id:notification-service}",
            containerFactory = "authListenerContainerFactory"
    )
    public void consume(
            AuthDomainEvent event,
            @Header(name = HEADER_REQUEST_ID, required = false) String requestId) {

        try {
            if (requestId != null && !requestId.isBlank()) {
                MDC.put(MDC_REQUEST_ID, requestId);
            }
            log.info("Evento de auth recibido: {}", event.getClass().getSimpleName());
            notificationService.sendMessage(event);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }
}
