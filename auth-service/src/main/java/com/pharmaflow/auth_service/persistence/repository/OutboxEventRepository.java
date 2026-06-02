package com.pharmaflow.auth_service.persistence.repository;

import com.pharmaflow.auth_service.persistence.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    /** Eventos pendientes de publicar, mas antiguos primero. El relay los procesa por lotes. */
    List<OutboxEventEntity> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

}
