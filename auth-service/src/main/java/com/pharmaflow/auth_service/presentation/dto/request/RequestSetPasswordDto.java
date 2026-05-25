package com.pharmaflow.auth_service.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestSetPasswordDto(

        @NotBlank(message = "El token es obligatorio")
        String token,

        @NotBlank(message = "La nueva contrasena es obligatoria")
        @Size(min = 8, max = 100, message = "La contrasena debe tener entre 8 y 100 caracteres")
        String newPassword

) {
}
