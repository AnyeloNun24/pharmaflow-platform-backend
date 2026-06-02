package com.pharmaflow.auth_service.persistence.repository;

import com.pharmaflow.auth_service.persistence.entity.AuthUserRoleEntity;
import com.pharmaflow.auth_service.persistence.entity.embeddable_id.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Set;

@Repository
public interface AuthUserRoleRepository extends JpaRepository<AuthUserRoleEntity, UserRoleId> {

    @Query("""
           SELECT ur
           FROM AuthUserRoleEntity ur
           JOIN FETCH ur.role r
           WHERE ur.userRoleId.idUser = :idUser
             AND ur.active = TRUE
             AND r.active = TRUE
             AND (ur.expiresAt IS NULL OR ur.expiresAt > :now)
           """)
    Set<AuthUserRoleEntity> findActiveRolesByUserId(@Param("idUser") Long idUser,
                                                    @Param("now") Instant now);

}
