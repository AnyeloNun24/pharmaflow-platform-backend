package com.pharmaflow.auth_service.persistence.repository;

import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthUserRepository extends JpaRepository<AuthUserEntity, Long> {

    Optional<AuthUserEntity> findByEmailIgnoreCase(String email);

}
