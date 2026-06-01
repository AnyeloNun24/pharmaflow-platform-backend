package com.pharmaflow.auth_service.config.filter;

import com.pharmaflow.auth_service.util.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

// Sin @Component → Spring Boot no lo registra automáticamente; se declara en SecurityConfig
// con addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class) para que corra
// dentro de la SecurityFilterChain antes de que Spring intente un login con form/basic
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;

    // Excluye rutas de documentación y assets estáticos: no necesitan validación JWT
    // y evita llenar los logs con "Sin header Bearer" por cada archivo CSS/JS de Swagger UI
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        // Normal en endpoints públicos; AuthorizationFilter decidirá si rechazar más adelante
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("[{}] {} - Sin header Bearer, se continúa sin autenticar",
                    request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // AnonymousAuthenticationToken no cuenta como autenticado real en Spring Security
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null
            && current.isAuthenticated()
            && !(current instanceof AnonymousAuthenticationToken)
        ) {
            log.debug("[{}] {} - Contexto ya autenticado como '{}', se omite el filtro JWT",
                    request.getMethod(), request.getRequestURI(), current.getName());
            filterChain.doFilter(request, response);
            return;
        }

        String jwtToken = authHeader.substring(BEARER_PREFIX.length()).trim();

        // Verifica firma HMAC, expiración e issuer; devuelve empty() sin lanzar excepción
        Optional<Claims> claimsOpt = this.jwtUtils.parseAndValidate(jwtToken);
        if (claimsOpt.isEmpty()) {
            log.warn("[{}] {} - JWT inválido o expirado, se continúa sin autenticar",
                    request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        Claims claims = claimsOpt.get();

        // Evita que un refresh token (firma válida) se use para autenticar endpoints
        if (!JwtUtils.TOKEN_TYPE_ACCESS.equalsIgnoreCase(JwtUtils.extractTokenType(claims))) {
            log.warn("[{}] {} - Token de tipo '{}' rechazado; solo se aceptan tokens 'access'",
                    request.getMethod(), request.getRequestURI(), JwtUtils.extractTokenType(claims));
            filterChain.doFilter(request, response);
            return;
        }

        String username = JwtUtils.extractUsername(claims);
        Long userId = JwtUtils.extractUserId(claims);
        List<String> roles = JwtUtils.extractRoles(claims);
        List<String> permissions = JwtUtils.extractPermissions(claims);

        // Roles y permisos en una sola lista
        List<SimpleGrantedAuthority> authorities = Stream.concat(roles.stream(), permissions.stream())
                .map(SimpleGrantedAuthority::new)
                .toList();

        // Record tipado: permite recuperar userId en controllers sin reparsear el JWT
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, username);

        // Constructor de 3 params (con authorities) marca isAuthenticated()=true; el de 2 no
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);

        // Adjunta IP y session-id; útil para auditoría y AuthenticationSuccessEvent
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("[{}] {} - Usuario '{}' (id={}) autenticado vía JWT con {} authorities",
                request.getMethod(), request.getRequestURI(), username, userId, authorities.size());

        filterChain.doFilter(request, response);
    }

    public record AuthenticatedPrincipal(Long userId, String username) {
        @Override
        public String toString() {
            return username;
        }
    }
}
