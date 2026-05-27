package com.pharmaflow.auth_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

@Table(
        name = "password_token",
        schema = "iam",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk__password_token__token", columnNames = "token")
        }
)
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class PasswordTokenEntity implements Serializable {

    public static final String TYPE_SET_PASSWORD = "SET_PASSWORD";
    public static final String TYPE_RESET_PASSWORD = "RESET_PASSWORD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_password_token", nullable = false, updatable = false)
    private Long idPasswordToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", nullable = false)
    private AuthUserEntity user;

    @Column(name = "token", nullable = false, length = 100, updatable = false)
    private String token;

    @Column(name = "type", nullable = false, length = 20, updatable = false)
    private String type;

    @Column(name = "used", nullable = false)
    @Builder.Default
    private Boolean used = false;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "expiry_at", nullable = false)
    private Instant expiryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
