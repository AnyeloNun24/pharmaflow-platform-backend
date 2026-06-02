package com.pharmaflow.auth_service.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Token de renovación de sesión")
public record RequestRefreshDto(

        @Schema(description = "Refresh token opaco obtenido en el último login o refresh", example = "v9Tqz3mXkL8rWpYdNcBaEfGhJsUiOeAz1234567890abc")
        @NotBlank(message = "El refresh token es obligatorio")
        String refreshToken

) {}
