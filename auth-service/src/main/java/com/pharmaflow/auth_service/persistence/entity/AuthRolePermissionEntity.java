package com.pharmaflow.auth_service.persistence.entity;

import com.pharmaflow.auth_service.persistence.entity.embeddable_id.RolePermissionId;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Table(
        name = "auth_role_permission",
        schema = "iam"
)
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AuthRolePermissionEntity {

    @EmbeddedId
    private RolePermissionId rolePermissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idRole")
    @JoinColumn(name = "id_role")
    private AuthRoleEntity role;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idPermission")
    @JoinColumn(name = "id_permission")
    private AuthPermissionEntity permission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    private AuthUserEntity grantedBy;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @PrePersist
    protected void onCreate() {
        if (this.grantedAt == null) {
            this.grantedAt = Instant.now();
        }
    }

}
