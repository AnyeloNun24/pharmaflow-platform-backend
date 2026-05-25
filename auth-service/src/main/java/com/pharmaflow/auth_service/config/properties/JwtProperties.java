package com.pharmaflow.auth_service.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties (
        String issuer,
        Integer accessTtlMinutes,
        Integer refreshTtlDays,
        String hmacSecret
) {

    public long getAccessTtlMs() {
        return accessTtlMinutes * 60 * 1_000L;
    }

    public long getRefreshTtlMs() {
        return refreshTtlDays * 24 * 60 * 60 * 1_000L;
    }

}
