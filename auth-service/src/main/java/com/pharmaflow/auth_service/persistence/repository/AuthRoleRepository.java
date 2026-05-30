package com.pharmaflow.auth_service.persistence.repository;

import com.pharmaflow.auth_service.persistence.entity.AuthRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface AuthRoleRepository extends JpaRepository<AuthRoleEntity, Long> {

    @Query("""
           SELECT r
           FROM AuthRoleEntity r
           WHERE r.active = TRUE
             AND r.roleName IN :names
           """)
    Set<AuthRoleEntity> findActiveByNames(@Param("names") Set<String> names);
}
