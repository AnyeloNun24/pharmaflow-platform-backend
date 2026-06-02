package com.pharmaflow.auth_service.presentation.dto.request;

import com.pharmaflow.auth_service.persistence.entity.type.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

@Schema(description = "Datos para crear un nuevo usuario")
public record RequestCreateUserDto(

        @Schema(description = "Email único del usuario", example = "farmaceutico@pharmaflow.com")
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato valido")
        @Size(max = 100, message = "El email no puede exceder 100 caracteres")
        String email,

        @Schema(description = "Nombres del usuario", example = "Juan Carlos")
        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 80, message = "Los nombres no pueden exceder 80 caracteres")
        String names,

        @Schema(description = "Apellidos del usuario", example = "García López")
        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 80, message = "Los apellidos no pueden exceder 80 caracteres")
        String surnames,

        @Schema(description = "Número de teléfono (opcional)", example = "+57 310 123 4567", nullable = true)
        @Size(max = 20, message = "El telefono no puede exceder 20 caracteres")
        @Pattern(regexp = "^$|^\\+?[0-9\\s\\-()]{7,20}$", message = "El telefono no tiene un formato valido")
        String phoneNumber,

        @Schema(description = "Fecha de nacimiento en formato YYYY-MM-DD (opcional)", example = "1990-05-15", nullable = true)
        @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
        LocalDate birthDate,

        @Schema(description = "Género: M (masculino), F (femenino), O (otro), N (prefiero no decir)", example = "M", nullable = true)
        Gender gender,

        @Schema(description = "Roles a asignar al usuario (deben existir y estar activos)", example = "[\"PHARMACIST\"]")
        @NotEmpty(message = "Debe asignar al menos un rol")
        Set<@NotBlank(message = "El nombre del rol no puede estar vacio") String> roleNames

) {}
