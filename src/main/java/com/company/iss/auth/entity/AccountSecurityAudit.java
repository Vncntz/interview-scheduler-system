package com.company.iss.auth.entity;

import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "account_security_audits")
@Getter
@Immutable
public class AccountSecurityAudit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_user_id", nullable = false, updatable = false)
    private User targetUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", updatable = false)
    private User actorUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private AccountSecurityEvent event;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(length = 80, updatable = false)
    private String reasonCode;

    protected AccountSecurityAudit() {
    }

    private AccountSecurityAudit(
            User targetUser,
            User actorUser,
            AccountSecurityEvent event,
            LocalDateTime occurredAt,
            String reasonCode
    ) {
        this.targetUser = Objects.requireNonNull(targetUser, "targetUser is required");
        this.actorUser = actorUser;
        this.event = Objects.requireNonNull(event, "event is required");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        this.reasonCode = reasonCode;
    }

    public static AccountSecurityAudit record(
            User targetUser,
            User actorUser,
            AccountSecurityEvent event,
            LocalDateTime occurredAt,
            String reasonCode
    ) {
        return new AccountSecurityAudit(targetUser, actorUser, event, occurredAt, reasonCode);
    }
}
