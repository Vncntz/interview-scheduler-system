package com.company.iss.notification.entity;

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
@Table(name = "notification_settings_audits")
@Getter
@Immutable
public class NotificationSettingsAudit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settings_id", nullable = false, updatable = false)
    private NotificationSettings settings;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private NotificationSettingsAuditAction action;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(length = 500, updatable = false)
    private String changedFields;

    protected NotificationSettingsAudit() {
    }

    private NotificationSettingsAudit(
            NotificationSettings settings,
            User actor,
            LocalDateTime occurredAt,
            String changedFields
    ) {
        this.settings = Objects.requireNonNull(settings, "settings is required");
        this.actor = Objects.requireNonNull(actor, "actor is required");
        this.action = NotificationSettingsAuditAction.SETTINGS_UPDATED;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        this.changedFields = changedFields;
    }

    public static NotificationSettingsAudit settingsUpdated(
            NotificationSettings settings,
            User actor,
            LocalDateTime occurredAt,
            String changedFields
    ) {
        return new NotificationSettingsAudit(settings, actor, occurredAt, changedFields);
    }
}
