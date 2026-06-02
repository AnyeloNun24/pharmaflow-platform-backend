package com.pharmaflow.notification_service.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "notification.mail")
public record NotificationMailProperties(

        @DefaultValue("noreply@pharmaflow.com")
        String from,

        @DefaultValue("PharmaFlow")
        String appName,

        @DefaultValue("http://localhost:4200")
        String frontendBaseUrl
) {
}
