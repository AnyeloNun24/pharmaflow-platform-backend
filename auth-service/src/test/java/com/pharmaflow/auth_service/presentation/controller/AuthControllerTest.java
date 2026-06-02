package com.pharmaflow.auth_service.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmaflow.auth_service.config.filter.FilterConfig;
import com.pharmaflow.auth_service.config.security.RestAccessDeniedHandler;
import com.pharmaflow.auth_service.config.security.RestAuthenticationEntryPoint;
import com.pharmaflow.auth_service.config.security.SecurityConfig;
import com.pharmaflow.auth_service.presentation.advice.GlobalExceptionHandler;
import com.pharmaflow.auth_service.presentation.dto.request.RequestForgotPasswordDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestLoginDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestLogoutDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestRefreshDto;
import com.pharmaflow.auth_service.presentation.dto.request.RequestSetPasswordDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseLoginDto;
import com.pharmaflow.auth_service.presentation.dto.response.ResponseRefreshDto;
import com.pharmaflow.auth_service.service.interfaces.AuthService;
import com.pharmaflow.auth_service.util.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, FilterConfig.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
@DisplayName("AuthController (@WebMvcTest)")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;
    // Colaboradores requeridos por la cadena de seguridad importada (no por el controller).
    @MockitoBean
    private JwtUtils jwtUtils;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    @DisplayName("POST /auth/login es publico, delega y devuelve 200 con los tokens")
    void login_ok() throws Exception {
        ResponseLoginDto dto = ResponseLoginDto.builder()
                .accessToken("access").refreshToken("refresh").tokenType("Bearer")
                .userId(7L).email("u@x.com").roles(Set.of("ADMIN")).build();
        when(authService.login(any(), anyString(), any())).thenReturn(dto);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RequestLoginDto("u@x.com", "password1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.userId").value(7));
    }

    @Test
    @DisplayName("POST /auth/login con body invalido devuelve 400 con mapa de errores")
    void login_validationError() throws Exception {
        // email vacio + password corta -> dos violaciones.
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RequestLoginDto("", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Datos invalidos"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("POST /auth/login con credenciales invalidas se traduce a 401 generico")
    void login_badCredentials() throws Exception {
        when(authService.login(any(), anyString(), any()))
                .thenThrow(new BadCredentialsException("wrong"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RequestLoginDto("u@x.com", "password1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Credenciales invalidas"));
    }

    @Test
    @DisplayName("POST /auth/refresh con cuenta deshabilitada se traduce a 403")
    void refresh_disabled() throws Exception {
        when(authService.refresh(any(), anyString(), any()))
                .thenThrow(new DisabledException("disabled"));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RequestRefreshDto("raw"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Usuario desactivado"));
    }

    @Test
    @DisplayName("POST /auth/refresh valido devuelve 200 con tokens rotados")
    void refresh_ok() throws Exception {
        when(authService.refresh(any(), anyString(), any()))
                .thenReturn(ResponseRefreshDto.of("new-access", "new-refresh"));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RequestRefreshDto("raw"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
    }

    @Test
    @DisplayName("POST /auth/refresh con token vacio devuelve 400")
    void refresh_validationError() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RequestRefreshDto("  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.refreshToken").exists());
    }

    @Test
    @DisplayName("POST /auth/logout devuelve 200 y delega al servicio")
    void logout_ok() throws Exception {
        doNothing().when(authService).logout(anyString());

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RequestLogoutDto("raw"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sesion cerrada"));

        verify(authService).logout("raw");
    }

    @Test
    @DisplayName("POST /auth/forgot-password siempre devuelve 200 con mensaje generico (sin enumeracion)")
    void forgotPassword_genericResponse() throws Exception {
        doNothing().when(authService).forgotPassword(any());

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RequestForgotPasswordDto("u@x.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(authService).forgotPassword(any());
    }

    @Test
    @DisplayName("POST /auth/set-password con token invalido se traduce a 401")
    void setPassword_invalidToken() throws Exception {
        doThrow(new BadCredentialsException("Token invalido"))
                .when(authService).setPassword(any());

        mockMvc.perform(post("/auth/set-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RequestSetPasswordDto("tok", "password1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciales invalidas"));
    }

    @Test
    @DisplayName("POST /auth/set-password valido devuelve 200")
    void setPassword_ok() throws Exception {
        doNothing().when(authService).setPassword(any());

        mockMvc.perform(post("/auth/set-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RequestSetPasswordDto("tok", "password1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Contrasena actualizada"));

        verify(authService).setPassword(eq(new RequestSetPasswordDto("tok", "password1")));
    }
}
