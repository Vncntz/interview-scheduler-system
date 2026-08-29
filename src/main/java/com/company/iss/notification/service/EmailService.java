package com.company.iss.notification.service;

import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.entity.NotificationSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Properties;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final NotificationSettingsService notificationSettingsService;
    private final NotificationRuntimeProperties runtimeProperties;

    public EmailService(
            NotificationSettingsService notificationSettingsService,
            NotificationRuntimeProperties runtimeProperties
    ) {
        this.notificationSettingsService = notificationSettingsService;
        this.runtimeProperties = runtimeProperties;
    }

    @Async
    public void send(String to, String subject, String body) {
        NotificationSettings settings = notificationSettingsService.getSettings();
        String recipient = maskRecipient(to);

        if (!Boolean.TRUE.equals(settings.getEmailEnabled())) {
            log.debug("[EMAIL] Email delivery skipped recipient={} reason=EMAIL_DISABLED", recipient);
            return;
        }

        if (to == null || to.isBlank()) {
            logKnownFailure(recipient, "INVALID_RECIPIENT", "Recipient email is missing");
            return;
        }
        if (settings.getSmtpHost() == null || settings.getSmtpHost().isBlank()) {
            logKnownFailure(recipient, "SMTP_CONFIGURATION_INVALID", "SMTP host is missing");
            return;
        }
        if (settings.getSmtpPort() == null) {
            logKnownFailure(recipient, "SMTP_CONFIGURATION_INVALID", "SMTP port is missing");
            return;
        }
        if (settings.getSmtpUsername() == null || settings.getSmtpUsername().isBlank()) {
            logKnownFailure(recipient, "SMTP_AUTHENTICATION_FAILED", "SMTP username is missing");
            return;
        }
        if (!runtimeProperties.getSmtp().isPasswordConfigured()) {
            logKnownFailure(recipient, "SMTP_AUTHENTICATION_FAILED", "SMTP password is missing");
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(settings.getSmtpUsername());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        try {
            createMailSender(settings).send(message);
            log.info("[EMAIL] Email sent recipient={}", recipient);
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

    JavaMailSender createMailSender(NotificationSettings settings) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        mailSender.setHost(settings.getSmtpHost());
        mailSender.setPort(settings.getSmtpPort());
        mailSender.setUsername(settings.getSmtpUsername());
        mailSender.setPassword(runtimeProperties.getSmtp().getPassword());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        return mailSender;
    }

    static String maskRecipient(String email) {
        if (email == null || email.isBlank()) {
            return "<missing>";
        }

        String sanitized = email.replaceAll("[\\r\\n\\t]", "_").trim();
        int separator = sanitized.indexOf('@');
        if (separator <= 0 || separator == sanitized.length() - 1) {
            return sanitized.substring(0, Math.min(2, sanitized.length())) + "***";
        }

        String localPart = sanitized.substring(0, separator);
        String domain = sanitized.substring(separator + 1);
        return localPart.substring(0, Math.min(2, localPart.length())) + "***@" + domain;
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
