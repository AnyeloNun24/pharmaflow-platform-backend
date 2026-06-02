package com.pharmaflow.auth_service.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "Nuevos tokens tras la rotación del refresh token")
@Builder
public record ResponseRefreshDto(

        @Schema(description = "Nuevo JWT de acceso (TTL 15 min)",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBwaGFybWFmbG93LmNvbSJ9.xyz789")
        String accessToken,

        @Schema(description = "Nuevo refresh token (el anterior queda revocado)",
                example = "mK7rNpQsLwXzVbYcTdAeJfGhUiOk1234567890def")
        String refreshToken,

        @Schema(description = "Esquema de autenticación", example = "Bearer")
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
