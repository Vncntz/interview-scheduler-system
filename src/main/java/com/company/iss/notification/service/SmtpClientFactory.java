package com.company.iss.notification.service;

import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.config.SmtpSecurity;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Properties;

@Component
public class SmtpClientFactory {

    private final NotificationRuntimeProperties runtimeProperties;

    public SmtpClientFactory(NotificationRuntimeProperties runtimeProperties) {
        this.runtimeProperties = runtimeProperties;
    }

    public JavaMailSenderImpl create(NotificationSettings settings) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(settings.getSmtpHost());
        mailSender.setPort(settings.getSmtpPort());
        mailSender.setUsername(settings.getSmtpUsername());
        mailSender.setPassword(runtimeProperties.getSmtp().getPassword());

        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.connectiontimeout", timeoutMillis(
                runtimeProperties.getSmtp().getConnectionTimeout(), "connection"
        ));
        properties.put("mail.smtp.timeout", timeoutMillis(
                runtimeProperties.getSmtp().getReadTimeout(), "read"
        ));
        properties.put("mail.smtp.writetimeout", timeoutMillis(
                runtimeProperties.getSmtp().getWriteTimeout(), "write"
        ));
        properties.put("mail.smtp.ssl.checkserveridentity", "true");

        boolean startTls = settings.getSmtpSecurity() == SmtpSecurity.STARTTLS;
        properties.put("mail.smtp.starttls.enable", Boolean.toString(startTls));
        properties.put("mail.smtp.starttls.required", Boolean.toString(startTls));
        properties.put("mail.smtp.ssl.enable", Boolean.toString(!startTls));

        return mailSender;
    }

    private String timeoutMillis(Duration duration, String timeoutName) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new BusinessRuleViolationException(
                    "SMTP " + timeoutName + " timeout must be greater than zero."
            );
        }
        long millis;
        try {
            millis = duration.toMillis();
        } catch (ArithmeticException exception) {
            throw invalidTimeout(timeoutName);
        }
        if (millis <= 0 || millis > Integer.MAX_VALUE) {
            throw invalidTimeout(timeoutName);
        }
        return Long.toString(millis);
    }

    private BusinessRuleViolationException invalidTimeout(String timeoutName) {
        return new BusinessRuleViolationException(
                "SMTP " + timeoutName + " timeout is outside the supported range."
        );
    }
}
