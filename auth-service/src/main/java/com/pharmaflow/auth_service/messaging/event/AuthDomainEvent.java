package com.pharmaflow.auth_service.messaging.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = UserCreatedEvent.class,            name = "USER_CREATED"),
        @JsonSubTypes.Type(value = PasswordResetRequestedEvent.class, name = "PASSWORD_RESET_REQUESTED")
})
public sealed interface AuthDomainEvent permits UserCreatedEvent, PasswordResetRequestedEvent {

    // Id del usuario del evento; se usa como clave de Kafka (misma clave -> misma particion -> orden)
    Long userId();
}
