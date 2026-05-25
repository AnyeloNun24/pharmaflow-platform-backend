package com.pharmaflow.auth_service.persistence.repository;

import com.pharmaflow.auth_service.persistence.entity.AuthRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRoleRepository extends JpaRepository<AuthRoleEntity, Long> {



}
