package com.pharmaflow.auth_service.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credenciales de acceso")
public record RequestLoginDto(

        @Schema(description = "Email del usuario registrado", example = "admin@pharmaflow.com")
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato valido")
        @Size(max = 100, message = "El email no puede exceder 100 caracteres")
        String email,

        @Schema(description = "Contraseña del usuario (mínimo 8 caracteres)", example = "Segura123!")
        @NotBlank(message = "La contrasena es obligatoria")
        @Size(min = 8, max = 100, message = "La contrasena debe tener entre 8 y 100 caracteres")
        String password

) {}
