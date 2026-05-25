package com.pharmaflow.auth_service.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RequestRefreshDto(
        @NotBlank(message = "El refresh token es obligatorio")
        String refreshToken
) {
}
