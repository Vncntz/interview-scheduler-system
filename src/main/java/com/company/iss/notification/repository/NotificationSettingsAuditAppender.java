package com.company.iss.notification.repository;

import com.company.iss.notification.entity.NotificationSettingsAudit;

public interface NotificationSettingsAuditAppender {

    NotificationSettingsAudit append(NotificationSettingsAudit audit);
}
