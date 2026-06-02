package com.pharmaflow.auth_service.service.implementation;

import com.pharmaflow.auth_service.persistence.entity.AuthRoleEntity;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.entity.AuthUserRoleEntity;
import com.pharmaflow.auth_service.persistence.repository.AuthRoleRepository;
import com.pharmaflow.auth_service.persistence.repository.AuthUserRepository;
import com.pharmaflow.auth_service.persistence.repository.AuthUserRoleRepository;
import com.pharmaflow.auth_service.presentation.dto.request.RequestCreateUserDto;
import com.pharmaflow.auth_service.service.exception.EmailAlreadyRegisteredException;
import com.pharmaflow.auth_service.service.interfaces.AuditLogService;
import com.pharmaflow.auth_service.service.interfaces.PasswordTokenService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserManagementServiceImpl")
class UserManagementServiceImplTest {

    @Mock
    private AuthUserRepository authUserRepository;
    @Mock
    private AuthRoleRepository authRoleRepository;
    @Mock
    private AuthUserRoleRepository authUserRoleRepository;
    @Mock
    private PasswordTokenService passwordTokenService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private UserManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserManagementServiceImpl(
                authUserRepository, authRoleRepository, authUserRoleRepository,
                passwordTokenService, auditLogService, eventPublisher);
    }

    private RequestCreateUserDto request(Set<String> roleNames) {
        return new RequestCreateUserDto(
                "new@x.com", "Nombre", "Apellido", null, null, null, roleNames);
    }

    private AuthRoleEntity role(long id, String name) {
        return AuthRoleEntity.builder().idRole(id).roleName(name).active(true).build();
    }

    @Test
    @DisplayName("email duplicado lanza EmailAlreadyRegisteredException")
    void duplicateEmail() {
        when(authUserRepository.findByEmailIgnoreCase("new@x.com"))
                .thenReturn(Optional.of(AuthUserEntity.builder().idUser(1L).build()));

        assertThatThrownBy(() -> service.createUser(request(Set.of("ADMIN")), 1L))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(authUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("rol inexistente o inactivo lanza EntityNotFoundException listando los faltantes")
    void missingRoles() {
        when(authUserRepository.findByEmailIgnoreCase("new@x.com")).thenReturn(Optional.empty());
        // Se piden 2 roles pero solo existe 1 activo.
        when(authRoleRepository.findActiveByNames(Set.of("ADMIN", "GHOST")))
                .thenReturn(Set.of(role(1L, "ADMIN")));

        assertThatThrownBy(() -> service.createUser(request(Set.of("ADMIN", "GHOST")), 1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("GHOST");

        verify(authUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("creacion valida persiste usuario sin password, asigna roles, emite SET_PASSWORD y audita")
    void happyPath() {
        when(authUserRepository.findByEmailIgnoreCase("new@x.com")).thenReturn(Optional.empty());
        when(authRoleRepository.findActiveByNames(Set.of("ADMIN", "SELLER")))
                .thenReturn(Set.of(role(1L, "ADMIN"), role(2L, "SELLER")));
        when(authUserRepository.findById(99L))
                .thenReturn(Optional.of(AuthUserEntity.builder().idUser(99L).build()));
        when(authUserRepository.save(any())).thenAnswer(inv -> {
            AuthUserEntity u = inv.getArgument(0);
            u.setIdUser(10L);
            return u;
        });
        when(passwordTokenService.issueSetPasswordToken(any())).thenReturn("set-token");

        AuthUserEntity created = service.createUser(request(Set.of("ADMIN", "SELLER")), 99L);

        assertThat(created.getIdUser()).isEqualTo(10L);
        assertThat(created.getPasswordHash()).isNull();
        assertThat(created.getForcePasswordChange()).isTrue();
        // Una fila user_role por rol solicitado.
        verify(authUserRoleRepository, times(2)).save(any(AuthUserRoleEntity.class));
        verify(passwordTokenService).issueSetPasswordToken(created);
        verify(auditLogService, times(2)).recordSuccess(
                eq(AuditLogService.ActionType.ROLE_ASSIGNED), eq(created), anyString());
        verify(auditLogService).recordSuccess(
                eq(AuditLogService.ActionType.USER_CREATED), eq(created), anyString());
    }

    @Test
    @DisplayName("createdByUserId nulo crea el usuario sin actor (creador anonimo)")
    void nullActor() {
        when(authUserRepository.findByEmailIgnoreCase("new@x.com")).thenReturn(Optional.empty());
        when(authRoleRepository.findActiveByNames(Set.of("ADMIN")))
                .thenReturn(Set.of(role(1L, "ADMIN")));
        when(authUserRepository.save(any())).thenAnswer(inv -> {
            AuthUserEntity u = inv.getArgument(0);
            u.setIdUser(10L);
            return u;
        });
        when(passwordTokenService.issueSetPasswordToken(any())).thenReturn("set-token");

        service.createUser(request(Set.of("ADMIN")), null);

        verify(authUserRepository, never()).findById(any());
        verify(authUserRepository).save(any());
    }
}
