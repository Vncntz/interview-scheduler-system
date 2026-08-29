package com.company.iss.notification.service;

import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.entity.NotificationChannel;
import com.company.iss.notification.entity.NotificationEvent;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.repository.NotificationSettingsRepository;
import com.company.iss.notification.repository.NotificationTemplateRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetNotificationReadiness {

    private final NotificationSettingsRepository settingsRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationRuntimeProperties runtimeProperties;

    public PasswordResetNotificationReadiness(
            NotificationSettingsRepository settingsRepository,
            NotificationTemplateRepository templateRepository,
            NotificationRuntimeProperties runtimeProperties
    ) {
        this.settingsRepository = settingsRepository;
        this.templateRepository = templateRepository;
        this.runtimeProperties = runtimeProperties;
    }

    @Transactional(readOnly = true)
    public void requireReady() {
        NotificationSettings settings = settingsRepository.findByActiveTrue().orElse(null);
        boolean smtpComplete = settings != null
                && Boolean.TRUE.equals(settings.getEmailEnabled())
                && hasText(settings.getSmtpHost())
                && settings.getSmtpPort() != null
                && hasText(settings.getSmtpUsername())
                && runtimeProperties.getSmtp().isPasswordConfigured();
        boolean templateReady = templateRepository
                .findByEventAndChannelAndActiveTrue(NotificationEvent.PASSWORD_RESET, NotificationChannel.EMAIL)
                .isPresent();
        if (!smtpComplete || !templateReady) {
            throw new BusinessRuleViolationException("Password reset email is not configured.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
