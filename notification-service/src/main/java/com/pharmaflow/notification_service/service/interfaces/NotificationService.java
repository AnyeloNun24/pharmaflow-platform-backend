package com.pharmaflow.notification_service.service.interfaces;

import com.pharmaflow.notification_service.messaging.event.AuthDomainEvent;

public interface NotificationService {

    /**
     * Notifica al usuario el evento recibido.
     */
    void sendMessage(AuthDomainEvent authDomainEvent);

}
