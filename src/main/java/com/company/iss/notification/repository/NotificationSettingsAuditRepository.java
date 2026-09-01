package com.company.iss.notification.repository;

import com.company.iss.notification.entity.NotificationSettingsAudit;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface NotificationSettingsAuditRepository
        extends Repository<NotificationSettingsAudit, Long>, NotificationSettingsAuditAppender {

    List<NotificationSettingsAudit> findBySettingsIdOrderByOccurredAtAscIdAsc(Long settingsId);

    long count();
}
