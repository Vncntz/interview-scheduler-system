package com.company.iss.notification.service;

import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.config.SmtpProvider;
import com.company.iss.notification.config.SmtpSecurity;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.entity.NotificationSettingsAudit;
import com.company.iss.notification.repository.NotificationSettingsAuditRepository;
import com.company.iss.notification.repository.NotificationSettingsRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class NotificationSettingsService {

    private final NotificationSettingsRepository notificationSettingsRepository;
    private final NotificationSettingsAuditRepository auditRepository;
    private final NotificationRuntimeProperties runtimeProperties;
    private final SmtpConfigurationValidator smtpValidator;
    private final SecurityService securityService;
    private final Clock clock;

    public NotificationSettingsService(
            NotificationSettingsRepository notificationSettingsRepository,
            NotificationSettingsAuditRepository auditRepository,
            NotificationRuntimeProperties runtimeProperties,
            SmtpConfigurationValidator smtpValidator,
            SecurityService securityService,
            Clock clock
    ) {
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.auditRepository = auditRepository;
        this.runtimeProperties = runtimeProperties;
        this.smtpValidator = smtpValidator;
        this.securityService = securityService;
        this.clock = clock;
    }

    @Transactional
    public NotificationSettings getSettings() {
        return notificationSettingsRepository.findByActiveTrue().orElseGet(this::createDefaultSettings);
    }

    @Transactional
    public NotificationSettings save(NotificationSettings candidate) {
        User actor = securityService.requireAdmin();
        validate(candidate);

        NotificationSettings current = notificationSettingsRepository.findByActiveTrue().orElse(null);
        if (current != null && !Objects.equals(current.getId(), candidate.getId())) {
            throw new BusinessRuleViolationException(
                    "Notification settings changed while you were editing. Discard and try again."
            );
        }

        String changedFields = changedFields(current, candidate);
        candidate.setSmsEnabled(false);
        candidate.setActive(true);

        try {
            NotificationSettings saved = notificationSettingsRepository.saveAndFlush(candidate);
            auditRepository.append(NotificationSettingsAudit.settingsUpdated(
                    saved,
                    actor,
                    LocalDateTime.now(clock),
                    changedFields
            ));
            return saved;
        } catch (OptimisticLockingFailureException exception) {
            throw new BusinessRuleViolationException(
                    "Notification settings changed while you were editing. Discard and try again."
            );
        }
    }

    public boolean isSmtpPasswordConfigured() {
        return runtimeProperties.getSmtp().isPasswordConfigured();
    }

    private NotificationSettings createDefaultSettings() {
        NotificationSettings settings = new NotificationSettings();
        settings.setCompanyName("ISS Notifications");
        settings.setSmtpProvider(SmtpProvider.CUSTOM);
        settings.setSmtpSecurity(SmtpSecurity.STARTTLS);
        return notificationSettingsRepository.save(settings);
    }

    private void validate(NotificationSettings settings) {
        if (settings == null || settings.getCompanyName() == null || settings.getCompanyName().isBlank()) {
            throw new BusinessRuleViolationException("Company name is required.");
        }
        smtpValidator.validateForSave(settings);
        if (Boolean.TRUE.equals(settings.getSmsEnabled())) {
            throw new BusinessRuleViolationException("SMS notifications are not supported.");
        }
    }

    private String changedFields(NotificationSettings current, NotificationSettings candidate) {
        if (current == null) {
            return "INITIAL_CONFIGURATION";
        }
        List<String> fields = new ArrayList<>();
        addChanged(fields, "ORGANIZATION", current.getCompanyName(), candidate.getCompanyName());
        addChanged(fields, "EMAIL_ENABLED", current.getEmailEnabled(), candidate.getEmailEnabled());
        addChanged(fields, "SMTP_PROVIDER", current.getSmtpProvider(), candidate.getSmtpProvider());
        if (!Objects.equals(current.getSmtpHost(), candidate.getSmtpHost())
                || !Objects.equals(current.getSmtpPort(), candidate.getSmtpPort())) {
            fields.add("SMTP_SERVER");
        }
        addChanged(fields, "SMTP_SECURITY", current.getSmtpSecurity(), candidate.getSmtpSecurity());
        if (!Objects.equals(current.getSmtpUsername(), candidate.getSmtpUsername())) {
            fields.add("SMTP_AUTHENTICATION");
        }
        if (!Objects.equals(current.getSmtpFromName(), candidate.getSmtpFromName())
                || !Objects.equals(current.getSmtpFromAddress(), candidate.getSmtpFromAddress())) {
            fields.add("SENDER_IDENTITY");
        }
        return String.join(",", fields);
    }

    private void addChanged(List<String> fields, String label, Object current, Object candidate) {
        if (!Objects.equals(current, candidate)) {
            fields.add(label);
        }
    }
}
