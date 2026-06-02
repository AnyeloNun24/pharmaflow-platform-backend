package com.pharmaflow.auth_service.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Token de contraseña y nueva contraseña a establecer")
public record RequestSetPasswordDto(

        @Schema(description = "Token opaco recibido por email (SET_PASSWORD o RESET_PASSWORD)", example = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2")
        @NotBlank(message = "El token es obligatorio")
        String token,

        @Schema(description = "Nueva contraseña (mínimo 8 caracteres)", example = "NuevaSegura456!")
        @NotBlank(message = "La nueva contrasena es obligatoria")
        @Size(min = 8, max = 100, message = "La contrasena debe tener entre 8 y 100 caracteres")
        String newPassword

) {}
