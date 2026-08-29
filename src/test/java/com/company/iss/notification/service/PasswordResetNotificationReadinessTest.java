package com.company.iss.notification.service;

import com.company.iss.notification.entity.NotificationChannel;
import com.company.iss.notification.entity.NotificationEvent;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.entity.NotificationTemplate;
import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.repository.NotificationSettingsRepository;
import com.company.iss.notification.repository.NotificationTemplateRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetNotificationReadinessTest {

    @Test
    void missingSettingsFailsWithoutCreatingDefaults() {
        NotificationSettingsRepository settings = mock(NotificationSettingsRepository.class);
        NotificationTemplateRepository templates = mock(NotificationTemplateRepository.class);
        when(settings.findByActiveTrue()).thenReturn(Optional.empty());

        assertThrows(
                BusinessRuleViolationException.class,
                () -> new PasswordResetNotificationReadiness(
                        settings, templates, runtimeProperties("unit-test-password")
                ).requireReady()
        );

        verify(settings).findByActiveTrue();
    }

    @Test
    void completeSmtpAndActiveTemplateAreReady() {
        NotificationSettingsRepository settings = mock(NotificationSettingsRepository.class);
        NotificationTemplateRepository templates = mock(NotificationTemplateRepository.class);
        NotificationSettings configured = new NotificationSettings();
        configured.setEmailEnabled(true);
        configured.setSmtpHost("smtp.example.test");
        configured.setSmtpPort(587);
        configured.setSmtpUsername("mailer@example.test");
        when(settings.findByActiveTrue()).thenReturn(Optional.of(configured));
        when(templates.findByEventAndChannelAndActiveTrue(
                NotificationEvent.PASSWORD_RESET, NotificationChannel.EMAIL
        )).thenReturn(Optional.of(new NotificationTemplate()));

        assertDoesNotThrow(() -> new PasswordResetNotificationReadiness(
                settings, templates, runtimeProperties("unit-test-password")
        ).requireReady());
    }

    @Test
    void missingRuntimePasswordIsNotReady() {
        NotificationSettingsRepository settings = mock(NotificationSettingsRepository.class);
        NotificationTemplateRepository templates = mock(NotificationTemplateRepository.class);
        NotificationSettings configured = new NotificationSettings();
        configured.setEmailEnabled(true);
        configured.setSmtpHost("smtp.example.test");
        configured.setSmtpPort(587);
        configured.setSmtpUsername("mailer@example.test");
        when(settings.findByActiveTrue()).thenReturn(Optional.of(configured));
        when(templates.findByEventAndChannelAndActiveTrue(
                NotificationEvent.PASSWORD_RESET, NotificationChannel.EMAIL
        )).thenReturn(Optional.of(new NotificationTemplate()));

        assertThrows(BusinessRuleViolationException.class, () -> new PasswordResetNotificationReadiness(
                settings, templates, runtimeProperties("")
        ).requireReady());
    }

    @Test
    void missingPasswordResetTemplateIsNotReady() {
        NotificationSettingsRepository settings = mock(NotificationSettingsRepository.class);
        NotificationTemplateRepository templates = mock(NotificationTemplateRepository.class);
        NotificationSettings configured = new NotificationSettings();
        configured.setEmailEnabled(true);
        configured.setSmtpHost("smtp.example.test");
        configured.setSmtpPort(587);
        configured.setSmtpUsername("mailer@example.test");
        when(settings.findByActiveTrue()).thenReturn(Optional.of(configured));
        when(templates.findByEventAndChannelAndActiveTrue(
                NotificationEvent.PASSWORD_RESET, NotificationChannel.EMAIL
        )).thenReturn(Optional.empty());

        assertThrows(BusinessRuleViolationException.class, () -> new PasswordResetNotificationReadiness(
                settings, templates, runtimeProperties("runtime-only-test-fixture")
        ).requireReady());
    }

    private NotificationRuntimeProperties runtimeProperties(String password) {
        NotificationRuntimeProperties properties = new NotificationRuntimeProperties();
        properties.getSmtp().setPassword(password);
        return properties;
    }
}
