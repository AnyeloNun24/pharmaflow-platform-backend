package com.pharmaflow.auth_service.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Email para solicitar restablecimiento de contraseña")
public record RequestForgotPasswordDto(

        @Schema(description = "Email asociado a la cuenta", example = "usuario@pharmaflow.com")
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato valido")
        @Size(max = 100)
        String email

) {}
