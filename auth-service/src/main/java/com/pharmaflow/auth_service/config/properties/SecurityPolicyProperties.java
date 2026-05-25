package com.pharmaflow.auth_service.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.policy")
public record SecurityPolicyProperties(
        Integer maxFailedAttempts
) {
    public int resolvedMaxFailedAttempts() {
        return maxFailedAttempts != null ? maxFailedAttempts : 5;
    }
}
