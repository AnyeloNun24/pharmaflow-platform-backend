package com.pharmaflow.auth_service.presentation.controller;

import com.pharmaflow.auth_service.config.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.presentation.dto.request.RequestCreateUserDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseCreateUserDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseMessageDto;
import com.pharmaflow.auth_service.service.interfaces.FailedAttemptService;
import com.pharmaflow.auth_service.service.interfaces.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final FailedAttemptService failedAttemptService;
    private final UserManagementService userManagementService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ResponseCreateUserDto> create(@Valid @RequestBody RequestCreateUserDto request) {
        AuthUserEntity created = this.userManagementService.createUser(request, resolveActorId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ResponseCreateUserDto.of(created.getIdUser(), created.getEmail(), request.roleNames()));
    }

    @PostMapping("/{idUser}/unlock")
    public ResponseEntity<ResponseMessageDto> unlock(@PathVariable Long idUser) {
        this.failedAttemptService.forceUnlock(idUser, resolveActorEmail());
        return ResponseEntity.ok(ResponseMessageDto.of("Cuenta desbloqueada"));
    }

    private static Long resolveActorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        return principal instanceof AuthenticatedPrincipal ap ? ap.userId() : null;
    }

    private static String resolveActorEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthenticatedPrincipal ap) {
            return ap.username();
        }
        return auth.getName();
    }
}
