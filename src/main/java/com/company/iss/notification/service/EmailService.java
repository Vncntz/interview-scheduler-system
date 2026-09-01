package com.company.iss.notification.service;

import com.company.iss.notification.entity.NotificationSettings;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final NotificationSettingsService notificationSettingsService;
    private final SmtpConfigurationValidator validator;
    private final SmtpClientFactory clientFactory;

    public EmailService(
            NotificationSettingsService notificationSettingsService,
            SmtpConfigurationValidator validator,
            SmtpClientFactory clientFactory
    ) {
        this.notificationSettingsService = notificationSettingsService;
        this.validator = validator;
        this.clientFactory = clientFactory;
    }

    @Async
    public void send(String to, String subject, String body) {
        String recipient = maskRecipient(to);
        ReminderNotificationResult result = sendSynchronously(to, subject, body);
        if (result.disposition() == ReminderNotificationResult.Disposition.SENT) {
            log.info("[EMAIL] Email sent recipient={}", recipient);
        } else if ("EMAIL_DISABLED".equals(result.reason())) {
            log.debug("[EMAIL] Email delivery skipped recipient={} reason=EMAIL_DISABLED", recipient);
        } else {
            logKnownFailure(recipient, result.reason(), detail(result.detailCode()));
        }
    }

    ReminderNotificationResult sendSynchronously(String to, String subject, String body) {
        NotificationSettings settings = notificationSettingsService.getSettings();
        if (!Boolean.TRUE.equals(settings.getEmailEnabled())) {
            return ReminderNotificationResult.skipped("EMAIL_DISABLED");
        }
        try {
            validator.requireDeliveryReady(settings);
            validator.validateRecipient(to);
            sendMimeMessage(settings, to, subject, body);
            return ReminderNotificationResult.sent();
        } catch (SmtpConfigurationException exception) {
            return configurationResult(exception);
        } catch (MailAuthenticationException exception) {
            return ReminderNotificationResult.retryable("SMTP_AUTHENTICATION_FAILED");
        } catch (MailParseException | MailPreparationException exception) {
            return ReminderNotificationResult.skipped("INVALID_MESSAGE");
        } catch (MailSendException exception) {
            return ReminderNotificationResult.retryable(
                    isConnectionFailure(exception) ? "SMTP_CONNECTION_FAILED" : "SMTP_SEND_FAILED"
            );
        } catch (MailException exception) {
            return ReminderNotificationResult.retryable("EMAIL_DELIVERY_FAILED");
        }
    }

    void sendTestEmail(NotificationSettings settings, String recipient) {
        validator.requireDiagnosticReady(settings);
        validator.validateRecipient(recipient);
        sendMimeMessage(
                settings,
                recipient,
                "Interview Scheduler SMTP configuration test",
                "This is a test email from Interview Scheduler. Your SMTP configuration can send email successfully."
        );
    }

    void sendMimeMessage(NotificationSettings settings, String to, String subject, String body) {
        JavaMailSenderImpl mailSender = clientFactory.create(settings);
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    StandardCharsets.UTF_8.name()
            );
            if (settings.getSmtpFromName() == null || settings.getSmtpFromName().isBlank()) {
                helper.setFrom(settings.getSmtpFromAddress());
            } else {
                helper.setFrom(settings.getSmtpFromAddress(), settings.getSmtpFromName().trim());
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
        } catch (MessagingException | UnsupportedEncodingException exception) {
            throw new MailPreparationException("Could not prepare email message", exception);
        }
        mailSender.send(message);
    }

    static String maskRecipient(String email) {
        if (email == null || email.isBlank()) {
            return "<missing>";
        }

        String sanitized = email.replaceAll("[\\r\\n\\t]", "_").trim();
        int separator = sanitized.indexOf('@');
        if (separator <= 0
                || separator != sanitized.lastIndexOf('@')
                || separator == sanitized.length() - 1) {
            return sanitized.substring(0, Math.min(2, sanitized.length())) + "***";
        }

        String localPart = sanitized.substring(0, separator);
        String domain = sanitized.substring(separator + 1);
        if (!domain.matches("[A-Za-z0-9.-]+")) {
            return localPart.substring(0, Math.min(2, localPart.length())) + "***";
        }
        return localPart.substring(0, Math.min(2, localPart.length())) + "***@" + domain;
    }

    private ReminderNotificationResult configurationResult(SmtpConfigurationException exception) {
        return switch (exception.getFailure()) {
            case EMAIL_DISABLED -> ReminderNotificationResult.skipped("EMAIL_DISABLED");
            case RECIPIENT_INVALID -> ReminderNotificationResult.skipped("INVALID_RECIPIENT");
            case USERNAME_MISSING -> ReminderNotificationResult.retryable(
                    "SMTP_AUTHENTICATION_FAILED", "SMTP_USERNAME_MISSING"
            );
            case PASSWORD_MISSING -> ReminderNotificationResult.retryable(
                    "SMTP_AUTHENTICATION_FAILED", "SMTP_PASSWORD_MISSING"
            );
            default -> ReminderNotificationResult.retryable("SMTP_CONFIGURATION_INVALID");
        };
    }

    private String detail(String reason) {
        return switch (reason) {
            case "SMTP_USERNAME_MISSING" -> "SMTP username is missing";
            case "SMTP_PASSWORD_MISSING" -> "SMTP password is missing";
            case "SMTP_AUTHENTICATION_FAILED" -> "SMTP credentials are missing or invalid";
            case "SMTP_CONNECTION_FAILED" -> "SMTP server is unavailable";
            case "SMTP_SEND_FAILED" -> "SMTP server could not deliver the message";
            case "INVALID_RECIPIENT" -> "Recipient email is missing or invalid";
            case "INVALID_MESSAGE" -> "Recipient address or message is invalid";
            case "SMTP_CONFIGURATION_INVALID" -> "SMTP configuration is incomplete or invalid";
            default -> "Email provider rejected the delivery request";
        };
    }

    private boolean isConnectionFailure(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConnectException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof UnknownHostException
                    || cause.getClass().getSimpleName().equals("MailConnectException")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void logKnownFailure(String recipient, String reason, String detail) {
        log.error(
                "[EMAIL] Email delivery failed recipient={} reason={} detail=\"{}\"",
                recipient,
                reason,
                detail
        );
    }
}
