package com.pharmaflow.auth_service.service.interfaces;

import com.pharmaflow.auth_service.presentation.dto.request.RequestForgotPasswordDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestLoginDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestRefreshDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestSetPasswordDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseLoginDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseRefreshDto;

public interface AuthService {

    ResponseLoginDto login(RequestLoginDto request, String ipAddress, String userAgent);

    ResponseRefreshDto refresh(RequestRefreshDto request, String ipAddress, String userAgent);

    void logout(String refreshToken);

    void forgotPassword(RequestForgotPasswordDto request);

    void setPassword(RequestSetPasswordDto request);
}
