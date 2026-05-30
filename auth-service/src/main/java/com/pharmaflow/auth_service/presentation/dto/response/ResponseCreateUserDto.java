package com.pharmaflow.auth_service.presentation.dto.response;

import java.util.Set;

public record ResponseCreateUserDto(
        Long idUser,
        String email,
        Set<String> roles,
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
