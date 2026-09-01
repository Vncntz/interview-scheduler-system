package com.company.iss.notification.repository;

import com.company.iss.notification.entity.NotificationSettingsAudit;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

public class NotificationSettingsAuditAppenderImpl implements NotificationSettingsAuditAppender {

    private final EntityManager entityManager;

    public NotificationSettingsAuditAppenderImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public NotificationSettingsAudit append(NotificationSettingsAudit audit) {
        if (audit.getId() != null) {
            throw new IllegalArgumentException("A persisted notification settings audit cannot be appended again.");
        }
        entityManager.persist(audit);
        return audit;
    }
}
