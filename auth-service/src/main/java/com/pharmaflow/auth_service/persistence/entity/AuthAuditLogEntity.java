package com.pharmaflow.auth_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Table(
        name = "auth_audit_log",
        schema = "iam"
)
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AuthAuditLogEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_audit_log", nullable = false, updatable = false)
    private Long idAuditLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user")
    private AuthUserEntity user;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "action_type", nullable = false, length = 30, updatable = false)
    private String actionType;

    @Column(name = "description", nullable = false, length = 300, updatable = false)
    private String description;

    @Column(name = "success", nullable = false, updatable = false)
    private Boolean success;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", nullable = false, columnDefinition = "inet", updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    @Column(name = "failure_reason", length = 255, updatable = false)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
