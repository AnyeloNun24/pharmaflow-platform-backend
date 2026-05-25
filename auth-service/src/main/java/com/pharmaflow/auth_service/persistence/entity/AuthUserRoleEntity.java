package com.pharmaflow.auth_service.persistence.entity;

import com.pharmaflow.auth_service.persistence.entity.embeddable_id.UserRoleId;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Table(
        name = "auth_user_role",
        schema = "iam"
)
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AuthUserRoleEntity {

    @EmbeddedId
    private UserRoleId userRoleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idUser")
    @JoinColumn(name = "id_user")
    private AuthUserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idRole")
    @JoinColumn(name = "id_role")
    private AuthRoleEntity role;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private AuthUserEntity assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private OffsetDateTime assignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by")
    private AuthUserEntity revokedBy;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @PrePersist
    protected void onCreate() {
        if (this.assignedAt == null) {
            this.assignedAt = OffsetDateTime.now();
        }
    }
}
