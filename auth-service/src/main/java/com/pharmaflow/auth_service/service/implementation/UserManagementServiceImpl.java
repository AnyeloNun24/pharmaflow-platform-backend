package com.pharmaflow.auth_service.service.implementation;

import com.pharmaflow.auth_service.persistence.entity.AuthRoleEntity;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.entity.AuthUserRoleEntity;
import com.pharmaflow.auth_service.persistence.entity.embeddable_id.UserRoleId;
import com.pharmaflow.auth_service.persistence.repository.AuthRoleRepository;
import com.pharmaflow.auth_service.persistence.repository.AuthUserRepository;
import com.pharmaflow.auth_service.persistence.repository.AuthUserRoleRepository;
import com.pharmaflow.auth_service.presentation.dto.request.RequestCreateUserDto;
import com.pharmaflow.auth_service.service.exception.EmailAlreadyRegisteredException;
import com.pharmaflow.auth_service.service.interfaces.AuditLogService;
import com.pharmaflow.auth_service.service.interfaces.PasswordTokenService;
import com.pharmaflow.auth_service.service.interfaces.UserManagementService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

    private final AuthUserRepository authUserRepository;
    private final AuthRoleRepository authRoleRepository;
    private final AuthUserRoleRepository authUserRoleRepository;
    private final PasswordTokenService passwordTokenService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public AuthUserEntity createUser(RequestCreateUserDto request, Long createdByUserId) {

        this.authUserRepository.findByEmailIgnoreCase(request.email()).ifPresent(existing -> {
            throw new EmailAlreadyRegisteredException(request.email());
        });

        Set<AuthRoleEntity> roles = this.resolveRolesOrThrow(request.roleNames());

        AuthUserEntity creator = createdByUserId != null
                ? this.authUserRepository.findById(createdByUserId).orElse(null)
                : null;

        AuthUserEntity user = AuthUserEntity.builder()
                .email(request.email())
                .names(request.names())
                .surnames(request.surnames())
                .phoneNumber(blankToNull(request.phoneNumber()))
                .birthDate(request.birthDate())
                .gender(request.gender())
                .passwordHash(null)
                .forcePasswordChange(true)
                .createdBy(creator)
                .updatedBy(creator)
                .build();

        user = this.authUserRepository.save(user);

        this.assignRoles(user, roles, creator);

        String token = this.passwordTokenService.issueSetPasswordToken(user);

        this.auditLogService.recordSuccess(
                AuditLogService.ActionType.USER_CREATED,
                user,
                "Usuario creado por actor id=" + (createdByUserId != null ? createdByUserId : "anonimo")
                        + " con roles=" + request.roleNames());

        // TODO: integrar envio de correo con el enlace https://<frontend>/set-password?token=<token>
        //       Por ahora el token se loguea para pruebas; al productivo no debe loguearse en claro.
        log.info("Usuario creado id={} email={} setPasswordToken={}",
                user.getIdUser(), user.getEmail(), token);

        return user;
    }

    private Set<AuthRoleEntity> resolveRolesOrThrow(Set<String> requestedNames) {
        Set<AuthRoleEntity> found = this.authRoleRepository.findActiveByNames(requestedNames);
        if (found.size() == requestedNames.size()) {
            return found;
        }
        Set<String> foundNames = found.stream()
                .map(AuthRoleEntity::getRoleName)
                .collect(Collectors.toSet());
        Set<String> missing = new HashSet<>(requestedNames);
        missing.removeAll(foundNames);
        throw new EntityNotFoundException("Roles no encontrados o inactivos: " + missing);
    }

    private void assignRoles(AuthUserEntity user, Set<AuthRoleEntity> roles, AuthUserEntity actor) {
        for (AuthRoleEntity role : roles) {
            AuthUserRoleEntity userRole = AuthUserRoleEntity.builder()
                    .userRoleId(new UserRoleId(user.getIdUser(), role.getIdRole()))
                    .user(user)
                    .role(role)
                    .active(true)
                    .assignedBy(actor)
                    .build();
            this.authUserRoleRepository.save(userRole);
            this.auditLogService.recordSuccess(
                    AuditLogService.ActionType.ROLE_ASSIGNED,
                    user,
                    "Rol asignado: " + role.getRoleName());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
