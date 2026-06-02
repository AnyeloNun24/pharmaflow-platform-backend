package com.pharmaflow.auth_service.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

@Schema(description = "Datos del usuario creado")
public record ResponseCreateUserDto(

        @Schema(description = "ID único asignado al nuevo usuario", example = "42")
        Long idUser,

        @Schema(description = "Email del usuario creado", example = "farmaceutico@pharmaflow.com")
        String email,

        @Schema(description = "Roles asignados al usuario", example = "[\"PHARMACIST\"]")
        Set<String> roles,

        @Schema(description = "Mensaje informativo sobre el siguiente paso", example = "Usuario creado. Se enviara un correo con el enlace para definir su contrasena.")
        String message

) {
    public static ResponseCreateUserDto of(Long idUser, String email, Set<String> roles) {
        return new ResponseCreateUserDto(
                idUser,
                email,
                roles,
                "Usuario creado. Se enviara un correo con el enlace para definir su contrasena."
        );
    }
}
