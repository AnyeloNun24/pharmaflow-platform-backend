package com.pharmaflow.auth_service.presentation.dto.response;

import com.pharmaflow.auth_service.config.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.Set;

@Schema(description = "Tokens y datos básicos del usuario autenticado")
@Builder
public record ResponseLoginDto(

        @Schema(description = "JWT de acceso (TTL 15 min). Enviar en Authorization: Bearer <token>",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBwaGFybWFmbG93LmNvbSJ9.abc123")
        String accessToken,

        @Schema(description = "Token opaco de renovación (TTL deslizante 1 día / absoluto 30 días)",
                example = "v9Tqz3mXkL8rWpYdNcBaEfGhJsUiOeAz1234567890abc")
        String refreshToken,

        @Schema(description = "Esquema de autenticación", example = "Bearer")
        String tokenType,

        @Schema(description = "ID único del usuario autenticado", example = "1")
        Long userId,

        @Schema(description = "Email del usuario autenticado", example = "admin@pharmaflow.com")
        String email,

        @Schema(description = "Roles asignados al usuario", example = "[\"ADMIN\"]")
        Set<String> roles

) {
    public static ResponseLoginDto of(CustomUserDetails user, String accessToken, String refreshToken) {
        return ResponseLoginDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getUserId())
                .email(user.getUsername())
                .roles(user.getRoleNames())
                .build();
    }
}
