package com.pharmaflow.auth_service.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.password-token")
public record PasswordTokenProperties(
        Integer setTtlMinutes,
        Integer resetTtlMinutes
) {
    public Integer setTtlMinutes() {
        return setTtlMinutes != null ? setTtlMinutes : 1440;
    }

    public Integer resetTtlMinutes() {
        return resetTtlMinutes != null ? resetTtlMinutes : 60;
    }
}
