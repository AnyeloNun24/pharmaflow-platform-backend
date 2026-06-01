package com.pharmaflow.auth_service.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta con mensaje informativo")
public record ResponseMessageDto(

        @Schema(description = "Mensaje descriptivo del resultado", example = "Sesion cerrada")
        String message

) {
    public static ResponseMessageDto of(String message) {
        return new ResponseMessageDto(message);
    }
}
