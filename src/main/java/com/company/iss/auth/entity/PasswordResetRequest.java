package com.company.iss.auth.entity;

import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "password_reset_requests",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_password_reset_public_id", columnNames = "public_request_id"),
                @UniqueConstraint(name = "uk_password_reset_token_hash", columnNames = "token_hash")
        }
)
@Getter
public class PasswordResetRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_user_id", nullable = false, updatable = false)
    private User targetUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requesting_admin_id", nullable = false, updatable = false)
    private User requestingAdmin;

    @Column(name = "public_request_id", nullable = false, length = 32, updatable = false)
    private String publicRequestId;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime consumedAt;

    private LocalDateTime invalidatedAt;

    protected PasswordResetRequest() {
    }

    private PasswordResetRequest(
            User targetUser,
            User requestingAdmin,
            String publicRequestId,
            String tokenHash,
            LocalDateTime expiresAt
    ) {
        this.targetUser = Objects.requireNonNull(targetUser, "targetUser is required");
        this.requestingAdmin = Objects.requireNonNull(requestingAdmin, "requestingAdmin is required");
        this.publicRequestId = Objects.requireNonNull(publicRequestId, "publicRequestId is required");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash is required");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
    }

    public static PasswordResetRequest issue(
            User targetUser,
            User requestingAdmin,
            String publicRequestId,
            String tokenHash,
            LocalDateTime expiresAt
    ) {
        return new PasswordResetRequest(targetUser, requestingAdmin, publicRequestId, tokenHash, expiresAt);
    }

    public boolean isUsableAt(LocalDateTime now) {
        return consumedAt == null && invalidatedAt == null && expiresAt.isAfter(now);
    }

    public void consume(LocalDateTime when) {
        if (consumedAt != null || invalidatedAt != null) {
            throw new IllegalStateException("Password reset request is no longer active.");
        }
        consumedAt = Objects.requireNonNull(when, "when is required");
    }

    public void invalidate(LocalDateTime when) {
        if (consumedAt == null && invalidatedAt == null) {
            invalidatedAt = Objects.requireNonNull(when, "when is required");
        }
    }
}
