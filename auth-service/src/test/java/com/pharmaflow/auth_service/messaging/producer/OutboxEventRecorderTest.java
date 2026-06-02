package com.pharmaflow.auth_service.messaging.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmaflow.auth_service.messaging.event.UserCreatedEvent;
import com.pharmaflow.auth_service.persistence.entity.OutboxEventEntity;
import com.pharmaflow.auth_service.persistence.repository.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Verifica que el recorder traduce un evento de dominio a una fila de outbox correcta:
 * payload JSON con el discriminador "type", topic, clave (userId) y la correlacion del MDC.
 * No necesita base de datos: el repositorio es un mock.
 */
@ExtendWith(MockitoExtension.class)
class OutboxEventRecorderTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OutboxEventRecorder recorder;

    @Captor
    private ArgumentCaptor<OutboxEventEntity> outboxCaptor;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void registra_userCreated_como_fila_de_outbox_con_correlacion() {
        ReflectionTestUtils.setField(recorder, "userEventsTopic", "auth.user.events");
        MDC.put("requestId", "req-123");

        recorder.record(new UserCreatedEvent(7L, "ada@pharmaflow.test", "Ada", "Lovelace", "set-tok"));

        verify(outboxRepository).save(outboxCaptor.capture());
        OutboxEventEntity saved = outboxCaptor.getValue();

        assertThat(saved.getEventType()).isEqualTo("UserCreatedEvent");
        assertThat(saved.getTopic()).isEqualTo("auth.user.events");
        assertThat(saved.getMessageKey()).isEqualTo("7");
        assertThat(saved.getRequestId()).isEqualTo("req-123");
        assertThat(saved.getPayload())
                .contains("\"type\":\"USER_CREATED\"")
                .contains("\"setPasswordToken\":\"set-tok\"");
    }
}
