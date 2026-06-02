package com.pharmaflow.auth_service.persistence.repository;

import com.pharmaflow.auth_service.persistence.entity.AuthRolePermissionEntity;
import com.pharmaflow.auth_service.persistence.entity.embeddable_id.RolePermissionId;
import com.pharmaflow.auth_service.persistence.repository.base.ReadOnlyRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public interface AuthRolePermissionRepository extends ReadOnlyRepository<AuthRolePermissionEntity, RolePermissionId> {

    @Query("""
           SELECT rp
           FROM AuthRolePermissionEntity rp
           JOIN FETCH rp.permission p
           WHERE rp.rolePermissionId.idRole IN :idsRoles
             AND p.active = TRUE
           """)
    Set<AuthRolePermissionEntity> findActivePermissionsByRoleIds(@Param("idsRoles") Set<Long> idsRoles);

}
