package com.pharmaflow.auth_service.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmaflow.auth_service.config.filter.FilterConfig;
import com.pharmaflow.auth_service.config.security.RestAccessDeniedHandler;
import com.pharmaflow.auth_service.config.security.RestAuthenticationEntryPoint;
import com.pharmaflow.auth_service.config.security.SecurityConfig;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.presentation.advice.GlobalExceptionHandler;
import com.pharmaflow.auth_service.presentation.dto.request.RequestCreateUserDto;
import com.pharmaflow.auth_service.service.exception.EmailAlreadyRegisteredException;
import com.pharmaflow.auth_service.service.interfaces.FailedAttemptService;
import com.pharmaflow.auth_service.service.interfaces.UserManagementService;
import com.pharmaflow.auth_service.util.JwtUtils;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserManagementController.class)
@Import({SecurityConfig.class, FilterConfig.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
@DisplayName("UserManagementController (@WebMvcTest)")
class UserManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserManagementService userManagementService;
    @MockitoBean
    private FailedAttemptService failedAttemptService;
    // Colaboradores requeridos por la cadena de seguridad importada.
    @MockitoBean
    private JwtUtils jwtUtils;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private RequestCreateUserDto validRequest() {
        return new RequestCreateUserDto(
                "new@x.com", "Nombre", "Apellido", null, null, null, Set.of("ADMIN"));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    @DisplayName("POST /users sin autenticacion devuelve 401")
    void create_unauthenticated() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isUnauthorized());

        verify(userManagementService, never()).createUser(any(), any());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("POST /users con rol insuficiente devuelve 403")
    void create_forbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isForbidden());

        verify(userManagementService, never()).createUser(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /users como ADMIN crea el usuario y devuelve 201")
    void create_okForAdmin() throws Exception {
        AuthUserEntity created = AuthUserEntity.builder()
                .idUser(10L).email("new@x.com").build();
        when(userManagementService.createUser(any(), any())).thenReturn(created);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idUser").value(10))
                .andExpect(jsonPath("$.email").value("new@x.com"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("POST /users tambien lo permite SUPER_ADMIN")
    void create_okForSuperAdmin() throws Exception {
        when(userManagementService.createUser(any(), any()))
                .thenReturn(AuthUserEntity.builder().idUser(11L).email("new@x.com").build());

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /users con body invalido devuelve 400")
    void create_validationError() throws Exception {
        // email invalido + sin roles.
        RequestCreateUserDto invalid = new RequestCreateUserDto(
                "no-es-email", "", "Apellido", null, null, null, Set.of());

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());

        verify(userManagementService, never()).createUser(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /users con email duplicado se traduce a 409")
    void create_duplicateEmail() throws Exception {
        when(userManagementService.createUser(any(), any()))
                .thenThrow(new EmailAlreadyRegisteredException("new@x.com"));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /users con rol inexistente se traduce a 404")
    void create_missingRole() throws Exception {
        when(userManagementService.createUser(any(), any()))
                .thenThrow(new EntityNotFoundException("Roles no encontrados o inactivos: [GHOST]"));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Roles no encontrados o inactivos: [GHOST]"));
    }

    @Test
    @DisplayName("POST /users/{id}/unlock sin autenticacion devuelve 401")
    void unlock_unauthenticated() throws Exception {
        mockMvc.perform(post("/users/5/unlock"))
                .andExpect(status().isUnauthorized());

        verify(failedAttemptService, never()).forceUnlock(any(), any());
    }

    @Test
    @WithMockUser(username = "admin@x.com", roles = "USER")
    @DisplayName("POST /users/{id}/unlock con cualquier usuario autenticado devuelve 200")
    void unlock_okForAnyAuthenticated() throws Exception {
        mockMvc.perform(post("/users/5/unlock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cuenta desbloqueada"));

        verify(failedAttemptService).forceUnlock(eq(5L), anyString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /users/{id}/unlock sobre usuario inexistente se traduce a 404")
    void unlock_userNotFound() throws Exception {
        doThrow(new EntityNotFoundException("Usuario no encontrado: id=99"))
                .when(failedAttemptService).forceUnlock(eq(99L), anyString());

        mockMvc.perform(post("/users/99/unlock"))
                .andExpect(status().isNotFound());
    }
}
