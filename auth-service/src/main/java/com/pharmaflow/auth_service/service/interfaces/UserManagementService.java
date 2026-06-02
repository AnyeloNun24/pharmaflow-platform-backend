package com.pharmaflow.auth_service.service.interfaces;

import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.presentation.dto.request.RequestCreateUserDto;

public interface UserManagementService {

    /**
     * Crea el usuario con {@code password_hash=null}, asigna los roles solicitados y emite un token SET_PASSWORD.
     * Lanza {@code EmailAlreadyRegisteredException} (409) si el email ya existe,
     * o {@code EntityNotFoundException} (404) si algún rol solicitado no existe o está inactivo.
     */
    AuthUserEntity createUser(RequestCreateUserDto request, Long createdByUserId);
}
