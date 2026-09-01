package com.company.iss.notification.service;

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
    private final SmtpConfigurationValidator smtpValidator;

    public PasswordResetNotificationReadiness(
            NotificationSettingsRepository settingsRepository,
            NotificationTemplateRepository templateRepository,
            SmtpConfigurationValidator smtpValidator
    ) {
        this.settingsRepository = settingsRepository;
        this.templateRepository = templateRepository;
        this.smtpValidator = smtpValidator;
    }

    @Transactional(readOnly = true)
    public void requireReady() {
        NotificationSettings settings = settingsRepository.findByActiveTrue().orElse(null);
        boolean smtpComplete = smtpValidator.isDeliveryReady(settings);
        boolean templateReady = templateRepository
                .findByEventAndChannelAndActiveTrue(NotificationEvent.PASSWORD_RESET, NotificationChannel.EMAIL)
                .isPresent();
        if (!smtpComplete || !templateReady) {
            throw new BusinessRuleViolationException("Password reset email is not configured.");
        }
    }
}
