package com.pharmaflow.auth_service.util;

import com.pharmaflow.auth_service.config.properties.JwtProperties;
import com.pharmaflow.auth_service.config.security.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PERMISSIONS = "permissions";
    public static final String CLAIM_USER_ID = "userId";

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;

    private SecretKey signingKey;
    private JwtParser parser;

    @PostConstruct
    void init() {
        byte[] keyBytes = this.jwtProperties.hmacSecret().getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.parser = Jwts.parser()
                .verifyWith(this.signingKey)
                .requireIssuer(this.jwtProperties.issuer())
                .build();
    }

    public String generateAccessToken(CustomUserDetails userDetails) {

        Set<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .collect(Collectors.toSet());

        Set<String> permissions = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .collect(Collectors.toSet());

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, TOKEN_TYPE_ACCESS);
        claims.put(CLAIM_ROLES, roles);
        claims.put(CLAIM_PERMISSIONS, permissions);
        claims.put(CLAIM_USER_ID, userDetails.getUserId());

        return this.buildToken(
                userDetails.getUsername(),
                claims,
                this.jwtProperties.getAccessTtlMs()
        );
    }

    /**
     * Unico punto de entrada para validar y decodificar un JWT.
     * Verifica firma + issuer + expiracion en una sola pasada y devuelve los Claims.
     * Los demas helpers (extractUsername, extractRoles, etc.) operan sobre el Claims ya parseado
     * para evitar repetir la verificacion HMAC.
     */
    public Optional<Claims> parseAndValidate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            return Optional.of(this.parser.parseSignedClaims(token).getPayload());
        } catch (ExpiredJwtException e) {
            log.debug("Token expirado: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token invalido: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public static String extractUsername(Claims claims) {
        return claims.getSubject();
    }

    public static Long extractUserId(Claims claims) {
        return claims.get(CLAIM_USER_ID, Long.class);
    }

    @SuppressWarnings("unchecked")
    public static List<String> extractRoles(Claims claims) {
        List<String> roles = claims.get(CLAIM_ROLES, List.class);
        return roles != null ? roles : List.of();
    }

    @SuppressWarnings("unchecked")
    public static List<String> extractPermissions(Claims claims) {
        List<String> perms = claims.get(CLAIM_PERMISSIONS, List.class);
        return perms != null ? perms : List.of();
    }

    public static String extractTokenType(Claims claims) {
        return claims.get(CLAIM_TYPE, String.class);
    }

    private String buildToken(String username, Map<String, Object> extraClaims, long expirationMs) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        JwtBuilder jwtBuilder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(this.jwtProperties.issuer())
                .subject(username)
                .issuedAt(now)
                .expiration(expiration)
                .claims(extraClaims)
                .signWith(this.signingKey);

        return jwtBuilder.compact();
    }
}
