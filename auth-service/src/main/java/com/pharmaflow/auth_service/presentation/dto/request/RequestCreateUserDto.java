package com.pharmaflow.auth_service.presentation.dto.request;

import com.pharmaflow.auth_service.persistence.entity.type.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record RequestCreateUserDto(

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato valido")
        @Size(max = 100, message = "El email no puede exceder 100 caracteres")
        String email,

        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 80, message = "Los nombres no pueden exceder 80 caracteres")
        String names,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 80, message = "Los apellidos no pueden exceder 80 caracteres")
        String surnames,

        @Size(max = 20, message = "El telefono no puede exceder 20 caracteres")
        @Pattern(
                regexp = "^$|^\\+?[0-9\\s\\-()]{7,20}$",
                message = "El telefono no tiene un formato valido"
        )
        String phoneNumber,

        @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
        LocalDate birthDate,

        Gender gender,

        @NotEmpty(message = "Debe asignar al menos un rol")
        Set<@NotBlank(message = "El nombre del rol no puede estar vacio") String> roleNames

) {
}
