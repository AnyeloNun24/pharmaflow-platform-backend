package com.pharmaflow.auth_service.presentation.controller;

import com.pharmaflow.auth_service.config.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.presentation.dto.request.RequestCreateUserDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseApiErrorDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseCreateUserDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseMessageDto;
import com.pharmaflow.auth_service.service.interfaces.FailedAttemptService;
import com.pharmaflow.auth_service.service.interfaces.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Gestión de usuarios", description = "Administración de usuarios: creación y desbloqueo de cuentas")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final FailedAttemptService failedAttemptService;
    private final UserManagementService userManagementService;

    @Operation(summary = "Crear usuario",
            description = "Crea un nuevo usuario con los roles indicados. El usuario se crea sin contraseña (password_hash = null) " +
                    "y se emite un token SET_PASSWORD que se enviará por email para que defina su contraseña inicial. " +
                    "Requiere rol ADMIN o SUPER_ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Usuario creado exitosamente",
                    content = @Content(schema = @Schema(implementation = ResponseCreateUserDto.class))),
            @ApiResponse(responseCode = "409", description = "El email ya está registrado",
                    content = @Content(schema = @Schema(implementation = ResponseApiErrorDto.class))),
            @ApiResponse(responseCode = "404", description = "Uno o más roles solicitados no existen o están inactivos",
                    content = @Content(schema = @Schema(implementation = ResponseApiErrorDto.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso: se requiere rol ADMIN o SUPER_ADMIN",
                    content = @Content(schema = @Schema(implementation = ResponseApiErrorDto.class))),
            @ApiResponse(responseCode = "400", description = "Body inválido (campos requeridos faltantes o mal formateados)",
                    content = @Content(schema = @Schema(implementation = ResponseApiErrorDto.class)))
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ResponseCreateUserDto> create(@Valid @RequestBody RequestCreateUserDto request) {
        AuthUserEntity created = this.userManagementService.createUser(request, resolveActorId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ResponseCreateUserDto.of(created.getIdUser(), created.getEmail(), request.roleNames()));
    }

    @Operation(summary = "Desbloquear cuenta de usuario",
            description = "Desbloquea manualmente una cuenta bloqueada por exceso de intentos fallidos. " +
                    "Resetea el contador de intentos y registra en auditoría quién realizó el desbloqueo. " +
                    "Requiere estar autenticado; la política de rol se definirá próximamente.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cuenta desbloqueada exitosamente",
                    content = @Content(schema = @Schema(implementation = ResponseMessageDto.class))),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = ResponseApiErrorDto.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ResponseApiErrorDto.class)))
    })
    @PostMapping("/{idUser}/unlock")
    public ResponseEntity<ResponseMessageDto> unlock(
            @Parameter(description = "ID del usuario a desbloquear", required = true, example = "1")
            @PathVariable Long idUser
    ) {
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
