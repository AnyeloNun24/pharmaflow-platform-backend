package com.pharmaflow.auth_service.service.interfaces;

import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.presentation.dto.request.RequestCreateUserDto;

public interface UserManagementService {

    AuthUserEntity createUser(RequestCreateUserDto request, Long createdByUserId);
}
