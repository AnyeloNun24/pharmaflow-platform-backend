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
import java.util.*;
import java.util.function.Function;
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

    public String extractUsername(String token) {
        return this.extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        return this.extractClaim(token, claims -> claims.get(CLAIM_USER_ID, Long.class));
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        List<String> roles = this.extractClaim(token, claims -> claims.get(CLAIM_ROLES, List.class));
        return roles != null ? roles : List.of();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(String token) {
        List<String> perms = this.extractClaim(token, claims -> claims.get(CLAIM_PERMISSIONS, List.class));
        return perms != null ? perms : List.of();
    }

    public String extractTokenType(String token) {
        return this.extractClaim(token, claims -> claims.get(CLAIM_TYPE, String.class));
    }

    public String extractJti(String token) {
        return this.extractClaim(token, Claims::getId);
    }

    public Date extractExpiration(String token) {
        return this.extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(this.parseClaims(token));
    }

    private Claims parseClaims(String token) {
        return this.parser.parseSignedClaims(token).getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            this.parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("Token expirado: {}", e.getMessage());
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token invalido: {}", e.getMessage());
            return false;
        }
    }

    public long getTokenRemainingMs(String token) {
        long remaining = this.extractExpiration(token).getTime() - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }
}
