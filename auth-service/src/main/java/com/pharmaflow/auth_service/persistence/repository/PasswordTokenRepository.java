package com.pharmaflow.auth_service.persistence.repository;

import com.pharmaflow.auth_service.persistence.entity.PasswordTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordTokenRepository extends JpaRepository<PasswordTokenEntity, Long> {

    Optional<PasswordTokenEntity> findByToken(String token);

    @Modifying
    @Query("""
           UPDATE PasswordTokenEntity pt
              SET pt.used = TRUE,
                  pt.usedAt = :now
            WHERE pt.user.idUser = :idUser
              AND pt.used = FALSE
              AND pt.type = :type
           """)
    int invalidatePreviousTokens(@Param("idUser") Long idUser,
                                 @Param("type") String type,
                                 @Param("now") Instant now);
}
