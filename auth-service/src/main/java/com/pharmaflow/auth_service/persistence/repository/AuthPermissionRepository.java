package com.pharmaflow.auth_service.persistence.repository;

import com.pharmaflow.auth_service.persistence.entity.AuthPermissionEntity;
import com.pharmaflow.auth_service.persistence.repository.base.ReadOnlyRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthPermissionRepository extends ReadOnlyRepository<AuthPermissionEntity, Long> {

    Optional<AuthPermissionEntity> findByResourceAndAction(
            String resource,
            String action
    );

}
