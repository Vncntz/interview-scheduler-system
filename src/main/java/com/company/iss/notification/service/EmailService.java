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
        NotificationSettings settings = notificationSettingsService.getSettings();
        String recipient = maskRecipient(to);

        if (!Boolean.TRUE.equals(settings.getEmailEnabled())) {
            log.debug("[EMAIL] Email delivery skipped recipient={} reason=EMAIL_DISABLED", recipient);
            return;
        }

        try {
            validator.requireDeliveryReady(settings);
            validator.validateRecipient(to);
            sendMimeMessage(settings, to, subject, body);
            log.info("[EMAIL] Email sent recipient={}", recipient);
        } catch (SmtpConfigurationException exception) {
            logConfigurationFailure(recipient, exception);
        } catch (MailAuthenticationException exception) {
            logKnownFailure(
                    recipient,
                    "SMTP_AUTHENTICATION_FAILED",
                    "SMTP credentials are missing or invalid"
            );
        } catch (MailParseException | MailPreparationException exception) {
            logKnownFailure(recipient, "INVALID_MESSAGE", "Recipient address or message is invalid");
        } catch (MailSendException exception) {
            if (isConnectionFailure(exception)) {
                logKnownFailure(recipient, "SMTP_CONNECTION_FAILED", "SMTP server is unavailable");
            } else {
                logKnownFailure(recipient, "SMTP_SEND_FAILED", "SMTP server could not deliver the message");
            }
        } catch (MailException exception) {
            logKnownFailure(recipient, "EMAIL_DELIVERY_FAILED", "Email provider rejected the delivery request");
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

    private void logConfigurationFailure(String recipient, SmtpConfigurationException exception) {
        String reason;
        String detail;
        switch (exception.getFailure()) {
            case USERNAME_MISSING, PASSWORD_MISSING -> {
                reason = "SMTP_AUTHENTICATION_FAILED";
                detail = exception.getFailure() == SmtpConfigurationValidator.Failure.PASSWORD_MISSING
                        ? "SMTP password is missing"
                        : "SMTP username is missing";
            }
            case RECIPIENT_INVALID -> {
                reason = "INVALID_RECIPIENT";
                detail = "Recipient email is missing or invalid";
            }
            default -> {
                reason = "SMTP_CONFIGURATION_INVALID";
                detail = "SMTP configuration is incomplete or invalid";
            }
        }
        logKnownFailure(recipient, reason, detail);
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
