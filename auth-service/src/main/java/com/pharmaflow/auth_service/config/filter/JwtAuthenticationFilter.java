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

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null
                && current.isAuthenticated()
                && !(current instanceof AnonymousAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwtToken = authHeader.substring(BEARER_PREFIX.length()).trim();

        Optional<Claims> claimsOpt = this.jwtUtils.parseAndValidate(jwtToken);
        if (claimsOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Claims claims = claimsOpt.get();

        if (!JwtUtils.TOKEN_TYPE_ACCESS.equalsIgnoreCase(JwtUtils.extractTokenType(claims))) {
            log.debug("Token recibido no es de tipo 'access'. Se descarta.");
            filterChain.doFilter(request, response);
            return;
        }

        String username = JwtUtils.extractUsername(claims);
        Long userId = JwtUtils.extractUserId(claims);
        List<String> roles = JwtUtils.extractRoles(claims);
        List<String> permissions = JwtUtils.extractPermissions(claims);

        List<SimpleGrantedAuthority> authorities = Stream.concat(roles.stream(), permissions.stream())
                .map(SimpleGrantedAuthority::new)
                .toList();

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, username);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    public record AuthenticatedPrincipal(Long userId, String username) {
        @Override
        public String toString() {
            return username;
        }
    }
}
