package com.pharmaflow.auth_service.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Token a revocar para cerrar sesión")
public record RequestLogoutDto(

        @Schema(description = "Refresh token del dispositivo que cierra sesión", example = "v9Tqz3mXkL8rWpYdNcBaEfGhJsUiOeAz1234567890abc")
        @NotBlank(message = "El refresh token es obligatorio")
        String refreshToken

) {}
