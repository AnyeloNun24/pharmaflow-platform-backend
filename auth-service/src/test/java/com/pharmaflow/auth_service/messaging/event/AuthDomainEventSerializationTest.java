package com.pharmaflow.auth_service.messaging.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Blinda el CONTRATO de eventos que comparten auth-service (productor) y notification-service
 * (consumidor): el JSON debe llevar el discriminador {@code "type"} y los campos esperados.
 * Si alguien cambia un nombre de campo o el discriminador, este test falla antes de romper la
 * integracion real. No necesita Kafka ni base de datos: solo verifica la serializacion.
 */
class AuthDomainEventSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void userCreated_serializa_con_discriminador_y_campos() throws Exception {
        AuthDomainEvent event = new UserCreatedEvent(7L, "ada@pharmaflow.test", "Ada", "Lovelace", "set-tok");

        String json = mapper.writeValueAsString(event);

        assertThat(json)
                .contains("\"type\":\"USER_CREATED\"")
                .contains("\"userId\":7")
                .contains("\"email\":\"ada@pharmaflow.test\"")
                .contains("\"names\":\"Ada\"")
                .contains("\"surnames\":\"Lovelace\"")
                .contains("\"setPasswordToken\":\"set-tok\"");
    }

    @Test
    void passwordReset_serializa_con_discriminador_y_campos() throws Exception {
        AuthDomainEvent event = new PasswordResetRequestedEvent(7L, "ada@pharmaflow.test", "Ada", "reset-tok");

        String json = mapper.writeValueAsString(event);

        assertThat(json)
                .contains("\"type\":\"PASSWORD_RESET_REQUESTED\"")
                .contains("\"userId\":7")
                .contains("\"email\":\"ada@pharmaflow.test\"")
                .contains("\"resetToken\":\"reset-tok\"");
    }
}
