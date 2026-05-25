package com.company.iss.notification.service;

import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.repository.NotificationSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NotificationSettingsService {

    @Autowired
    private NotificationSettingsRepository notificationSettingsRepository;

    public NotificationSettings getSettings() {
        return notificationSettingsRepository.findByActiveTrue().orElseGet(this::createDefaultSettings);
    }

    public NotificationSettings save(NotificationSettings settings) {
        validate(settings);

        return notificationSettingsRepository.save(settings);
    }

    private NotificationSettings createDefaultSettings() {
        NotificationSettings settings = new NotificationSettings();

        settings.setCompanyName("ISS Notifications");

        return notificationSettingsRepository.save(settings);
    }

    private void validate(NotificationSettings settings) {
        if (settings.getCompanyName() == null || settings.getCompanyName().isBlank()) {
            throw new RuntimeException("Company name is required.");
        }

        if (Boolean.TRUE.equals(settings.getEmailEnabled())) {

            if (settings.getSmtpHost() == null || settings.getSmtpHost().isBlank()) {
                throw new RuntimeException("SMTP host is required.");
            }

            if (settings.getSmtpPort() == null) {
                throw new RuntimeException("SMTP port is required.");
            }

            if (settings.getSmtpUsername() == null || settings.getSmtpUsername().isBlank()) {
                throw new RuntimeException("SMTP username is required.");
            }

            if (settings.getSmtpPassword() == null || settings.getSmtpPassword().isBlank()) {
                throw new RuntimeException("SMTP password is required.");
            }
        }
    }
}