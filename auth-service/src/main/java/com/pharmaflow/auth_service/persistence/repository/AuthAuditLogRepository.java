package com.pharmaflow.auth_service.persistence.repository;

import com.pharmaflow.auth_service.persistence.entity.AuthAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLogEntity, Long> {
}
