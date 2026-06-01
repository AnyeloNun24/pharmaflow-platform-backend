package com.pharmaflow.auth_service.service.interfaces;

import com.pharmaflow.auth_service.presentation.dto.request.RequestForgotPasswordDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestLoginDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestRefreshDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestSetPasswordDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseLoginDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseRefreshDto;

public interface AuthService {

    /** Valida credenciales, emite access token JWT y refresh token opaco; registra IP y User-Agent. */
    ResponseLoginDto login(RequestLoginDto request, String ipAddress, String userAgent);

    /** Valida y consume el refresh token, rota la familia y emite nuevos tokens; revoca toda la familia si detecta reuse. */
    ResponseRefreshDto refresh(RequestRefreshDto request, String ipAddress, String userAgent);

    /** Revoca el refresh token del dispositivo actual; trata el token ya revocado como reuse. */
    void logout(String refreshToken);

    /** Emite un token RESET_PASSWORD; retorna 200 aunque el email no exista (anti-enumeración). */
    void forgotPassword(RequestForgotPasswordDto request);

    /** Consume el token SET_PASSWORD o RESET_PASSWORD, actualiza la contraseña y revoca todos los refresh tokens del usuario. */
    void setPassword(RequestSetPasswordDto request);
}
