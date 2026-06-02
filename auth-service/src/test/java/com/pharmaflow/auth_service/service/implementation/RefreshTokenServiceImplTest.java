package com.pharmaflow.auth_service.service.implementation;

import com.pharmaflow.auth_service.config.properties.JwtProperties;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.entity.RefreshTokenEntity;
import com.pharmaflow.auth_service.persistence.repository.RefreshTokenRepository;
import com.pharmaflow.auth_service.service.exception.RefreshTokenReusedException;
import com.pharmaflow.auth_service.service.interfaces.AuditLogService;
import com.pharmaflow.auth_service.service.interfaces.RefreshTokenService;
import com.pharmaflow.auth_service.util.TokenHasherUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenServiceImpl")
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TokenHasherUtils tokenHasherUtils;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private RefreshTokenFamilyRevoker familyRevoker;

    private JwtProperties jwtProperties;
    private RefreshTokenServiceImpl service;

    private AuthUserEntity user;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties("iss", 15, 1, 30, "0123456789-0123456789-0123456789-0123456789");
        service = new RefreshTokenServiceImpl(
                refreshTokenRepository, tokenHasherUtils, jwtProperties, auditLogService, familyRevoker);
        user = AuthUserEntity.builder().idUser(7L).email("u@x.com").build();
    }

    private RefreshTokenEntity activeToken(Instant absolute, Instant sliding) {
        return RefreshTokenEntity.builder()
                .idRefreshToken(1L)
                .user(user)
                .tokenHash("hash")
                .tokenFamily(UUID.randomUUID())
                .absoluteExpiryAt(absolute)
                .expiryAt(sliding)
                .revoked(false)
                .build();
    }

    @Nested
    @DisplayName("issueForUser")
    class IssueForUser {

        @Test
        @DisplayName("la ventana deslizante nunca supera el tope absoluto")
        void slidingNeverExceedsAbsolute() {
            // refreshTtlDays=1, absoluteTtlDays=30 -> sliding (now+1d) < absolute (now+30d).
            when(tokenHasherUtils.generateRawToken()).thenReturn("raw");
            when(tokenHasherUtils.sha256Hex("raw")).thenReturn("hash");
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RefreshTokenService.IssuedToken issued = service.issueForUser(user, "1.1.1.1", "agent");

            RefreshTokenEntity entity = issued.entity();
            assertThat(issued.rawToken()).isEqualTo("raw");
            assertThat(entity.getExpiryAt()).isBefore(entity.getAbsoluteExpiryAt());
            assertThat(entity.getExpiryAt())
                    .isBeforeOrEqualTo(Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(5));
        }

        @Test
        @DisplayName("cuando la ventana deslizante superaria el tope, se recorta al absoluto")
        void slidingCappedToAbsolute() {
            // Si refreshTtlDays >= absoluteTtlDays, expiry_at = absolute.
            jwtProperties = new JwtProperties("iss", 15, 30, 1, "0123456789-0123456789-0123456789-0123456789");
            service = new RefreshTokenServiceImpl(
                    refreshTokenRepository, tokenHasherUtils, jwtProperties, auditLogService, familyRevoker);
            when(tokenHasherUtils.generateRawToken()).thenReturn("raw");
            when(tokenHasherUtils.sha256Hex("raw")).thenReturn("hash");
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RefreshTokenEntity entity = service.issueForUser(user, null, null).entity();

            assertThat(entity.getExpiryAt()).isEqualTo(entity.getAbsoluteExpiryAt());
        }
    }

    @Nested
    @DisplayName("validateAndConsume")
    class ValidateAndConsume {

        @Test
        @DisplayName("token en blanco lanza BadCredentialsException")
        void blank() {
            assertThatThrownBy(() -> service.validateAndConsume("  "))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("token inexistente lanza BadCredentialsException")
        void notFound() {
            when(tokenHasherUtils.sha256Hex("raw")).thenReturn("hash");
            when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.validateAndConsume("raw"))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("token revocado se trata como reuso: revoca la familia y audita")
        void revokedTriggersReuse() {
            RefreshTokenEntity entity = activeToken(
                    Instant.now().plus(30, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
            entity.setRevoked(true);
            when(tokenHasherUtils.sha256Hex("raw")).thenReturn("hash");
            when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.validateAndConsume("raw"))
                    .isInstanceOf(RefreshTokenReusedException.class);

            verify(familyRevoker).revoke(entity.getTokenFamily());
            verify(auditLogService).recordFailure(
                    eq(AuditLogService.ActionType.REFRESH_TOKEN_REUSE), eq(user), anyString(), anyString());
        }

        @Test
        @DisplayName("tope absoluto vencido exige reautenticacion")
        void absoluteExpired() {
            RefreshTokenEntity entity = activeToken(
                    Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
            when(tokenHasherUtils.sha256Hex("raw")).thenReturn("hash");
            when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.validateAndConsume("raw"))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("reautenticacion requerida");
        }

        @Test
        @DisplayName("ventana deslizante vencida (con absoluto vigente) marca token expirado")
        void slidingExpired() {
            RefreshTokenEntity entity = activeToken(
                    Instant.now().plus(30, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.HOURS));
            when(tokenHasherUtils.sha256Hex("raw")).thenReturn("hash");
            when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.validateAndConsume("raw"))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("Refresh token expirado");
        }

        @Test
        @DisplayName("token vigente se devuelve sin tocar la familia")
        void validReturnsEntity() {
            RefreshTokenEntity entity = activeToken(
                    Instant.now().plus(30, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
            when(tokenHasherUtils.sha256Hex("raw")).thenReturn("hash");
            when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(entity));

            RefreshTokenEntity result = service.validateAndConsume("raw");

            assertThat(result).isSameAs(entity);
            verify(familyRevoker, never()).revoke(any());
        }
    }

    @Nested
    @DisplayName("rotate")
    class Rotate {

        @Test
        @DisplayName("emite un token nuevo en la misma familia, conserva el absoluto y revoca el anterior")
        void rotatesPreservingFamilyAndAbsolute() {
            Instant absolute = Instant.now().plus(30, ChronoUnit.DAYS);
            RefreshTokenEntity current = activeToken(absolute, Instant.now().plus(1, ChronoUnit.DAYS));
            when(tokenHasherUtils.generateRawToken()).thenReturn("newRaw");
            when(tokenHasherUtils.sha256Hex("newRaw")).thenReturn("newHash");
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RefreshTokenService.IssuedToken next = service.rotate(current, "2.2.2.2", "ua");

            assertThat(next.entity().getTokenFamily()).isEqualTo(current.getTokenFamily());
            assertThat(next.entity().getAbsoluteExpiryAt()).isEqualTo(absolute);
            assertThat(current.getRevoked()).isTrue();
            assertThat(current.getRevokedAt()).isNotNull();
            assertThat(current.getReplacedByHash()).isEqualTo("newHash");
        }
    }

    @Nested
    @DisplayName("revokeAndReturnUser")
    class RevokeAndReturnUser {

        @Test
        @DisplayName("token en blanco lanza BadCredentialsException")
        void blank() {
            assertThatThrownBy(() -> service.revokeAndReturnUser(null))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("token ya revocado en logout dispara reuso y revoca la familia")
        void alreadyRevokedTriggersReuse() {
            RefreshTokenEntity entity = activeToken(
                    Instant.now().plus(30, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
            entity.setRevoked(true);
            when(tokenHasherUtils.sha256Hex("raw")).thenReturn("hash");
            when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.revokeAndReturnUser("raw"))
                    .isInstanceOf(RefreshTokenReusedException.class);
            verify(familyRevoker).revoke(entity.getTokenFamily());
        }

        @Test
        @DisplayName("token activo se revoca (solo esa fila) y devuelve el usuario")
        void activeRevokesOnlyThisRow() {
            RefreshTokenEntity entity = activeToken(
                    Instant.now().plus(30, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
            when(tokenHasherUtils.sha256Hex("raw")).thenReturn("hash");
            when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(entity));
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AuthUserEntity result = service.revokeAndReturnUser("raw");

            assertThat(result).isSameAs(user);
            assertThat(entity.getRevoked()).isTrue();
            assertThat(entity.getRevokedAt()).isNotNull();
            verify(familyRevoker, never()).revoke(any());
        }
    }

    @Test
    @DisplayName("revokeAllForUser delega la revocacion masiva al repositorio")
    void revokeAllForUser_delegates() {
        when(refreshTokenRepository.revokeAllByUser(eq(7L), any())).thenReturn(3);

        service.revokeAllForUser(7L);

        verify(refreshTokenRepository).revokeAllByUser(eq(7L), any());
    }
}
