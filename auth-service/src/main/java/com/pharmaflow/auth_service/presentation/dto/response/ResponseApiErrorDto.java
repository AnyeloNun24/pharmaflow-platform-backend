package com.pharmaflow.auth_service.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta estándar de error")
public record ResponseApiErrorDto(

        @Schema(description = "Fecha y hora del error en UTC (ISO-8601)", example = "2026-05-31T14:23:00.000Z")
        String timestamp,

        @Schema(description = "Código HTTP del error", example = "401")
        int status,

        @Schema(description = "Descripción estándar del código HTTP", example = "Unauthorized")
        String error,

        @Schema(description = "Mensaje descriptivo del error", example = "Credenciales invalidas")
        String message,

        @Schema(description = "Ruta del endpoint que generó el error", example = "/auth/login")
        String path
) {}
