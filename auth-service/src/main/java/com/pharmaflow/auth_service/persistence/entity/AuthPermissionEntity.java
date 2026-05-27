package com.pharmaflow.auth_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Table(
        name = "auth_permission",
        schema = "iam",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk__auth_permission__resource_action", columnNames = {"resource", "action"})
        }
)
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AuthPermissionEntity implements Serializable {

    @Id
    @Column(name = "id_permission", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idPermission;

    @Column(name = "resource", nullable = false, length = 50)
    private String resource;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthPermissionEntity that)) return false;
        return idPermission != null && Objects.equals(idPermission, that.idPermission);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
