package com.company.iss.notification.service;

import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.entity.NotificationSettings;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.stereotype.Component;

@Component
public class SmtpConfigurationValidator {

    public enum Failure {
        EMAIL_DISABLED,
        HOST_MISSING,
        PORT_MISSING,
        PORT_INVALID,
        USERNAME_MISSING,
        PASSWORD_MISSING,
        PROVIDER_MISSING,
        SECURITY_MISSING,
        FROM_ADDRESS_MISSING,
        FROM_ADDRESS_INVALID,
        SENDER_NAME_INVALID,
        RECIPIENT_INVALID
    }

    private final NotificationRuntimeProperties runtimeProperties;

    public SmtpConfigurationValidator(NotificationRuntimeProperties runtimeProperties) {
        this.runtimeProperties = runtimeProperties;
    }

    public void validateForSave(NotificationSettings settings) {
        if (settings == null) {
            throw failure(Failure.HOST_MISSING, "SMTP configuration is incomplete.");
        }
        if (Boolean.TRUE.equals(settings.getEmailEnabled())) {
            requireComplete(settings);
        }
    }

    public void requireDeliveryReady(NotificationSettings settings) {
        if (settings == null || !Boolean.TRUE.equals(settings.getEmailEnabled())) {
            throw failure(Failure.EMAIL_DISABLED, "Email notifications are disabled.");
        }
        requireComplete(settings);
    }

    public void requireDiagnosticReady(NotificationSettings settings) {
        requireDeliveryReady(settings);
    }

    public boolean isDeliveryReady(NotificationSettings settings) {
        try {
            requireDeliveryReady(settings);
            return true;
        } catch (SmtpConfigurationException exception) {
            return false;
        }
    }

    public void validateRecipient(String recipient) {
        if (!isStrictMailbox(recipient)) {
            throw failure(Failure.RECIPIENT_INVALID, "Enter a valid test recipient email address.");
        }
    }

    private void requireComplete(NotificationSettings settings) {
        if (!hasText(settings.getSmtpHost())) {
            throw failure(Failure.HOST_MISSING, "SMTP host is required.");
        }
        if (settings.getSmtpPort() == null) {
            throw failure(Failure.PORT_MISSING, "SMTP port is required.");
        }
        if (settings.getSmtpPort() < 1 || settings.getSmtpPort() > 65_535) {
            throw failure(Failure.PORT_INVALID, "SMTP port must be between 1 and 65535.");
        }
        if (!hasText(settings.getSmtpUsername())) {
            throw failure(Failure.USERNAME_MISSING, "SMTP username is required.");
        }
        if (!runtimeProperties.getSmtp().isPasswordConfigured()) {
            throw failure(
                    Failure.PASSWORD_MISSING,
                    "SMTP password is not configured in the runtime environment."
            );
        }
        if (settings.getSmtpProvider() == null) {
            throw failure(Failure.PROVIDER_MISSING, "SMTP provider is required.");
        }
        if (settings.getSmtpSecurity() == null) {
            throw failure(Failure.SECURITY_MISSING, "SMTP security mode is required.");
        }
        if (!hasText(settings.getSmtpFromAddress())) {
            throw failure(Failure.FROM_ADDRESS_MISSING, "Sender email is required.");
        }
        if (!isStrictMailbox(settings.getSmtpFromAddress())) {
            throw failure(Failure.FROM_ADDRESS_INVALID, "Sender email must be a valid email address.");
        }
        if (containsHeaderControl(settings.getSmtpFromName())) {
            throw failure(Failure.SENDER_NAME_INVALID, "Sender name contains invalid characters.");
        }
    }

    private boolean isStrictMailbox(String value) {
        if (!hasText(value) || containsHeaderControl(value)) {
            return false;
        }
        try {
            InternetAddress address = new InternetAddress(value.trim(), true);
            address.validate();
            return address.getPersonal() == null && value.trim().equals(address.getAddress());
        } catch (AddressException exception) {
            return false;
        }
    }

    private boolean containsHeaderControl(String value) {
        return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private SmtpConfigurationException failure(Failure failure, String message) {
        return new SmtpConfigurationException(failure, message);
    }
}
