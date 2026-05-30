package com.pharmaflow.auth_service.persistence.repository;

import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface AuthUserRepository extends JpaRepository<AuthUserEntity, Long> {

    Optional<AuthUserEntity> findByEmailIgnoreCase(String email);

    /**
     * Incrementa el contador de fallos atomicamente y devuelve el nuevo valor.
     * Evita el patron lost-update tipico de read-modify-write.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
           UPDATE iam.auth_user
              SET failed_attempts = failed_attempts + 1,
                  updated_at = :now
            WHERE id_user = :idUser
              AND account_locked = FALSE
           RETURNING failed_attempts
           """, nativeQuery = true)
    Integer incrementFailedAttemptsAndReturn(@Param("idUser") Long idUser,
                                             @Param("now") Instant now);

    /**
     * Bloquea la cuenta si todavia no esta bloqueada. Devuelve 1 si la bloqueo
     * (transicion efectiva), 0 si ya estaba bloqueada (idempotente).
     */
    @Modifying
    @Query("""
           UPDATE AuthUserEntity u
              SET u.accountLocked = TRUE,
                  u.lockedAt = :now,
                  u.updatedAt = :now
            WHERE u.idUser = :idUser
              AND u.accountLocked = FALSE
           """)
    int lockAccount(@Param("idUser") Long idUser, @Param("now") Instant now);

    @Modifying
    @Query("""
           UPDATE AuthUserEntity u
              SET u.failedAttempts = 0,
                  u.lastLoginAt = :now,
                  u.updatedAt = :now
            WHERE u.idUser = :idUser
           """)
    int resetFailedAttemptsAndStampLogin(@Param("idUser") Long idUser, @Param("now") Instant now);

    @Modifying
    @Query("""
           UPDATE AuthUserEntity u
              SET u.accountLocked = FALSE,
                  u.lockedAt = NULL,
                  u.failedAttempts = 0,
                  u.updatedAt = :now
            WHERE u.idUser = :idUser
           """)
    int unlockAccount(@Param("idUser") Long idUser, @Param("now") Instant now);

}
