package com.company.iss.notification.service;

import com.company.iss.auth.service.SecurityService;
import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.repository.NotificationSettingsRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationSettingsService {

    private final NotificationSettingsRepository notificationSettingsRepository;
    private final NotificationRuntimeProperties runtimeProperties;
    private final SecurityService securityService;

    public NotificationSettingsService(
            NotificationSettingsRepository notificationSettingsRepository,
            NotificationRuntimeProperties runtimeProperties,
            SecurityService securityService
    ) {
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.runtimeProperties = runtimeProperties;
        this.securityService = securityService;
    }

    @Transactional
    public NotificationSettings getSettings() {
        return notificationSettingsRepository.findByActiveTrue().orElseGet(this::createDefaultSettings);
    }

    @Transactional
    public NotificationSettings save(NotificationSettings settings) {
        securityService.requireAdmin();
        validate(settings);

        return notificationSettingsRepository.save(settings);
    }

    public boolean isSmtpPasswordConfigured() {
        return runtimeProperties.getSmtp().isPasswordConfigured();
    }

    private NotificationSettings createDefaultSettings() {
        NotificationSettings settings = new NotificationSettings();

        settings.setCompanyName("ISS Notifications");

        return notificationSettingsRepository.save(settings);
    }

    private void validate(NotificationSettings settings) {
        if (settings.getCompanyName() == null || settings.getCompanyName().isBlank()) {
            throw new BusinessRuleViolationException("Company name is required.");
        }

        if (Boolean.TRUE.equals(settings.getEmailEnabled())) {

            if (settings.getSmtpHost() == null || settings.getSmtpHost().isBlank()) {
                throw new BusinessRuleViolationException("SMTP host is required.");
            }

            if (settings.getSmtpPort() == null) {
                throw new BusinessRuleViolationException("SMTP port is required.");
            }

            if (settings.getSmtpUsername() == null || settings.getSmtpUsername().isBlank()) {
                throw new BusinessRuleViolationException("SMTP username is required.");
            }

            if (!runtimeProperties.getSmtp().isPasswordConfigured()) {
                throw new BusinessRuleViolationException("SMTP password is not configured in the runtime environment.");
            }
        }

        if (Boolean.TRUE.equals(settings.getSmsEnabled())) {
            throw new BusinessRuleViolationException("SMS notifications are not supported.");
        }
    }
}
