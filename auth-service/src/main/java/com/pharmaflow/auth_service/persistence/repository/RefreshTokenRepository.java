package com.pharmaflow.auth_service.persistence.repository;

import com.pharmaflow.auth_service.persistence.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
           UPDATE RefreshTokenEntity rt
              SET rt.revoked = TRUE,
                  rt.revokedAt = :now
            WHERE rt.tokenFamily = :family
              AND rt.revoked = FALSE
           """)
    int revokeAllByFamily(@Param("family") UUID family, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("""
           UPDATE RefreshTokenEntity rt
              SET rt.revoked = TRUE,
                  rt.revokedAt = :now
            WHERE rt.user.idUser = :idUser
              AND rt.revoked = FALSE
           """)
    int revokeAllByUser(@Param("idUser") Long idUser, @Param("now") OffsetDateTime now);
}
