package com.pharmaflow.auth_service.presentation.controller;

import com.pharmaflow.auth_service.presentation.dto.request.RequestForgotPasswordDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestLoginDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestLogoutDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestRefreshDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestSetPasswordDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseApiErrorDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseLoginDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseMessageDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseRefreshDto;
import com.pharmaflow.auth_service.service.interfaces.AuthService;
import com.pharmaflow.auth_service.util.RequestUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticación", description = "Gestión de sesiones: login, refresh, logout y recuperación de contraseña")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Iniciar sesión",
            description = "Autentica al usuario con email y contraseña. Retorna un access token JWT (TTL 15 min) y un refresh token opaco (TTL deslizante 1 día / absoluto 30 días).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Autenticación exitosa",
                    content = @Content(schema = @Schema(implementation = ResponseLoginDto.class))),
            @ApiResponse(responseCode = "401", description = "Credenciales incorrectas o cuenta bloqueada",
                    content = @Content(schema = @Schema(implementation = ResponseApiErrorDto.class))),
            @ApiResponse(responseCode = "400", description = "Body inválido (campos requeridos faltantes o mal formateados)",
                    content = @Content(schema = @Schema(implementation = ResponseApiErrorDto.class)))
    })
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<ResponseLoginDto> login(
            @Valid @RequestBody RequestLoginDto request,
            HttpServletRequest httpRequest
    ) {
        ResponseLoginDto response = this.authService.login(
                request,
                RequestUtils.resolveClientIp(httpRequest),
                RequestUtils.resolveUserAgent(httpRequest)
        );
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Renovar access token",
            description = "Rota el refresh token y emite un nuevo par access/refresh. Si el refresh token presentado ya fue usado, se revoca toda la familia de tokens (detección de reuse).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tokens renovados exitosamente",
                    content = @Content(schema = @Schema(implementation = ResponseRefreshDto.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido, expirado, revocado o reusado",
                    content = @Content(schema = @Schema(implementation = ResponseApiErrorDto.class))),
            @ApiResponse(responseCode = "400", description = "Body inválido",
                    content = @Content(schema = @Schema(implementation = ResponseApiErrorDto.class)))
    })
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<ResponseRefreshDto> refresh(
            @Valid @RequestBody RequestRefreshDto request,
            HttpServletRequest httpRequest
    ) {
        ResponseRefreshDto response = this.authService.refresh(
                request,
                RequestUtils.resolveClientIp(httpRequest),
                RequestUtils.resolveUserAgent(httpRequest)
        );
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cerrar sesión",
            description = "Revoca el refresh token del dispositivo actual. Otros dispositivos conservan sus sesiones. Si el token ya estaba revocado se trata como reuse y se revoca toda la familia.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sesión cerrada exitosamente",
                    content = @Content(schema = @Schema(implementation = ResponseMessageDto.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido o ya revocado",
                    content = @Content(schema = @Schema(implementation = ResponseApiErrorDto.class))),
            @ApiResponse(responseCode = "400", description = "Body inválido",
                    content = @Content(schema = @Schema(implementation = ResponseApiErrorDto.class)))
    })
    @SecurityRequirements
    @PostMapping("/logout")
    public ResponseEntity<ResponseMessageDto> logout(@Valid @RequestBody RequestLogoutDto request) {
        this.authService.logout(request.refreshToken());
        return ResponseEntity.ok(ResponseMessageDto.of("Sesion cerrada"));
    }

    @Operation(summary = "Solicitar restablecimiento de contraseña",
            description = "Genera un token de reset y lo envía al email registrado. Siempre retorna 200 para no revelar si el email existe (anti-enumeración).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Solicitud procesada (respuesta genérica por seguridad)",
                    content = @Content(schema = @Schema(implementation = ResponseMessageDto.class))),
            @ApiResponse(responseCode = "400", description = "Body inválido",
                    content = @Content(schema = @Schema(implementation = ResponseApiErrorDto.class)))
    })
    @SecurityRequirements
    @PostMapping("/forgot-password")
    public ResponseEntity<ResponseMessageDto> forgotPassword(@Valid @RequestBody RequestForgotPasswordDto request) {
        this.authService.forgotPassword(request);
        return ResponseEntity.ok(ResponseMessageDto.of(
                "Si el email esta registrado, recibira un enlace para restablecer su contrasena"
        ));
    }

    @Operation(summary = "Establecer nueva contraseña",
            description = "Consume el token de SET_PASSWORD o RESET_PASSWORD y actualiza la contraseña. Revoca todos los refresh tokens activos del usuario al completar.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contraseña actualizada exitosamente",
                    content = @Content(schema = @Schema(implementation = ResponseMessageDto.class))),
            @ApiResponse(responseCode = "401", description = "Token inválido, ya consumido o expirado",
                    content = @Content(schema = @Schema(implementation = ResponseApiErrorDto.class))),
            @ApiResponse(responseCode = "400", description = "Body inválido",
                    content = @Content(schema = @Schema(implementation = ResponseApiErrorDto.class)))
    })
    @SecurityRequirements
    @PostMapping("/set-password")
    public ResponseEntity<ResponseMessageDto> setPassword(@Valid @RequestBody RequestSetPasswordDto request) {
        this.authService.setPassword(request);
        return ResponseEntity.ok(ResponseMessageDto.of("Contrasena actualizada"));
    }
}
