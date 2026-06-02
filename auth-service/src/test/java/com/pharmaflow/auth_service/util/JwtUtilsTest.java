package com.pharmaflow.auth_service.util;

import com.pharmaflow.auth_service.config.properties.JwtProperties;
import com.pharmaflow.auth_service.config.security.CustomUserDetails;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtUtils")
class JwtUtilsTest {

    private static final String ISSUER = "pharmaflow-auth";
    // HS256 exige una clave de al menos 256 bits (32 bytes).
    private static final String SECRET = "0123456789-0123456789-0123456789-0123456789";

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils(props(ISSUER, 15));
        jwtUtils.init();
    }

    private JwtProperties props(String issuer, int accessTtlMinutes) {
        return new JwtProperties(issuer, accessTtlMinutes, 1, 30, SECRET);
    }

    private CustomUserDetails userDetails() {
        Set<GrantedAuthority> authorities = Set.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("USER_READ"),
                new SimpleGrantedAuthority("USER_WRITE"));
        return new CustomUserDetails(
                "admin@pharmaflow.com", "irrelevant",
                true, true, true, true,
                authorities, 42L, Set.of("ADMIN"));
    }

    @Test
    @DisplayName("genera un access token que vuelve a parsearse con todos los claims")
    void generateAndParse_roundTrip() {
        String token = jwtUtils.generateAccessToken(userDetails());

        Optional<Claims> parsed = jwtUtils.parseAndValidate(token);

        assertThat(parsed).isPresent();
        Claims claims = parsed.get();
        assertThat(JwtUtils.extractUsername(claims)).isEqualTo("admin@pharmaflow.com");
        assertThat(JwtUtils.extractUserId(claims)).isEqualTo(42L);
        assertThat(JwtUtils.extractTokenType(claims)).isEqualTo(JwtUtils.TOKEN_TYPE_ACCESS);
        assertThat(JwtUtils.extractRoles(claims)).containsExactly("ROLE_ADMIN");
        assertThat(JwtUtils.extractPermissions(claims))
                .containsExactlyInAnyOrder("USER_READ", "USER_WRITE");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    @DisplayName("token nulo o en blanco devuelve Optional vacio")
    void parseAndValidate_blank() {
        assertThat(jwtUtils.parseAndValidate(null)).isEmpty();
        assertThat(jwtUtils.parseAndValidate("")).isEmpty();
        assertThat(jwtUtils.parseAndValidate("   ")).isEmpty();
    }

    @Test
    @DisplayName("token con issuer distinto es rechazado")
    void parseAndValidate_wrongIssuer() {
        JwtUtils otherIssuer = new JwtUtils(props("otro-emisor", 15));
        otherIssuer.init();
        String foreignToken = otherIssuer.generateAccessToken(userDetails());

        assertThat(jwtUtils.parseAndValidate(foreignToken)).isEmpty();
    }

    @Test
    @DisplayName("token con firma manipulada es rechazado")
    void parseAndValidate_tamperedSignature() {
        String token = jwtUtils.generateAccessToken(userDetails());
        // Alterar el ultimo caracter invalida la firma HMAC.
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThat(jwtUtils.parseAndValidate(tampered)).isEmpty();
    }

    @Test
    @DisplayName("token expirado devuelve Optional vacio")
    void parseAndValidate_expired() {
        JwtUtils expiring = new JwtUtils(props(ISSUER, -1)); // TTL negativo -> ya expirado
        expiring.init();
        String token = expiring.generateAccessToken(userDetails());

        assertThat(jwtUtils.parseAndValidate(token)).isEmpty();
    }

    @Test
    @DisplayName("token firmado con otra clave es rechazado")
    void parseAndValidate_wrongKey() {
        JwtProperties otherKey = new JwtProperties(
                ISSUER, 15, 1, 30, "AAAAAAAAAA-BBBBBBBBBB-CCCCCCCCCC-DDDD");
        JwtUtils foreign = new JwtUtils(otherKey);
        foreign.init();
        String token = foreign.generateAccessToken(userDetails());

        assertThat(jwtUtils.parseAndValidate(token)).isEmpty();
    }
}
