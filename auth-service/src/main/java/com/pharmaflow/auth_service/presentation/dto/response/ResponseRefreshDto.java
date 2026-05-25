package com.pharmaflow.auth_service.presentation.dto.response;

import lombok.Builder;

@Builder
public record ResponseRefreshDto(
        String accessToken,
        String refreshToken,
        String tokenType
) {
    public static ResponseRefreshDto of(String accessToken, String refreshToken) {
        return ResponseRefreshDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .build();
    }
}
