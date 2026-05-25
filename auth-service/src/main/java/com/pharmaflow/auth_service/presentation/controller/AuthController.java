package com.pharmaflow.auth_service.presentation.controller;

import com.pharmaflow.auth_service.presentation.dto.request.RequestForgotPasswordDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestLoginDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestLogoutDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestRefreshDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestSetPasswordDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseLoginDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseMessageDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseRefreshDto;
import com.pharmaflow.auth_service.service.interfaces.AuthService;
import com.pharmaflow.auth_service.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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

    @PostMapping("/logout")
    public ResponseEntity<ResponseMessageDto> logout(@Valid @RequestBody RequestLogoutDto request) {
        this.authService.logout(request.refreshToken());
        return ResponseEntity.ok(ResponseMessageDto.of("Sesion cerrada"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ResponseMessageDto> forgotPassword(@Valid @RequestBody RequestForgotPasswordDto request) {
        this.authService.forgotPassword(request);
        return ResponseEntity.ok(ResponseMessageDto.of(
                "Si el email esta registrado, recibira un enlace para restablecer su contrasena"
        ));
    }

    @PostMapping("/set-password")
    public ResponseEntity<ResponseMessageDto> setPassword(@Valid @RequestBody RequestSetPasswordDto request) {
        this.authService.setPassword(request);
        return ResponseEntity.ok(ResponseMessageDto.of("Contrasena actualizada"));
    }
}
