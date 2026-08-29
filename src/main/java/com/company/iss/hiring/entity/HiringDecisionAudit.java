package com.company.iss.hiring.entity;

import com.company.iss.auth.entity.User;
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
@Table(name = "hiring_decision_audits")
@Getter
@Immutable
public class HiringDecisionAudit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false)
    private HiringDecision decision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private HiringDecisionAction action;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private HiringDecisionStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HiringDecisionStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(length = 1000)
    private String remarks;

    protected HiringDecisionAudit() {
    }

    private HiringDecisionAudit(
            HiringDecision decision,
            HiringDecisionAction action,
            HiringDecisionStatus previousStatus,
            HiringDecisionStatus newStatus,
            User actor,
            LocalDateTime occurredAt,
            String remarks
    ) {
        this.decision = Objects.requireNonNull(decision, "decision is required");
        this.action = Objects.requireNonNull(action, "action is required");
        this.previousStatus = previousStatus;
        this.newStatus = Objects.requireNonNull(newStatus, "newStatus is required");
        this.actor = Objects.requireNonNull(actor, "actor is required");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        this.remarks = remarks;
    }

    public static HiringDecisionAudit record(
            HiringDecision decision,
            HiringDecisionAction action,
            HiringDecisionStatus previousStatus,
            HiringDecisionStatus newStatus,
            User actor,
            LocalDateTime occurredAt,
            String remarks
    ) {
        return new HiringDecisionAudit(
                decision,
                action,
                previousStatus,
                newStatus,
                actor,
                occurredAt,
                remarks
        );
    }
}
