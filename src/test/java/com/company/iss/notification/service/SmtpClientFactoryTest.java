package com.company.iss.notification.service;

import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.config.SmtpProvider;
import com.company.iss.notification.config.SmtpSecurity;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmtpClientFactoryTest {

    @Test
    void startTlsIsRequiredWithServerIdentityCheckingAndTimeouts() {
        NotificationRuntimeProperties properties = properties();
        properties.getSmtp().setConnectionTimeout(Duration.ofSeconds(2));
        properties.getSmtp().setReadTimeout(Duration.ofSeconds(3));
        properties.getSmtp().setWriteTimeout(Duration.ofSeconds(4));
        SmtpClientFactory factory = new SmtpClientFactory(properties);

        JavaMailSenderImpl sender = factory.create(settings(SmtpSecurity.STARTTLS));
        Properties mail = sender.getJavaMailProperties();

        assertEquals("true", mail.getProperty("mail.smtp.auth"));
        assertEquals("true", mail.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", mail.getProperty("mail.smtp.starttls.required"));
        assertEquals("false", mail.getProperty("mail.smtp.ssl.enable"));
        assertEquals("true", mail.getProperty("mail.smtp.ssl.checkserveridentity"));
        assertEquals("2000", mail.getProperty("mail.smtp.connectiontimeout"));
        assertEquals("3000", mail.getProperty("mail.smtp.timeout"));
        assertEquals("4000", mail.getProperty("mail.smtp.writetimeout"));
    }

    @Test
    void sslDisablesStartTlsWithoutDisablingIdentityChecking() {
        SmtpClientFactory factory = new SmtpClientFactory(properties());

        Properties mail = factory.create(settings(SmtpSecurity.SSL)).getJavaMailProperties();

        assertEquals("false", mail.getProperty("mail.smtp.starttls.enable"));
        assertEquals("false", mail.getProperty("mail.smtp.starttls.required"));
        assertEquals("true", mail.getProperty("mail.smtp.ssl.enable"));
        assertEquals("true", mail.getProperty("mail.smtp.ssl.checkserveridentity"));
    }

    @Test
    void eachOperationReceivesAFreshMailSender() {
        SmtpClientFactory factory = new SmtpClientFactory(properties());
        NotificationSettings settings = settings(SmtpSecurity.STARTTLS);

        assertNotSame(factory.create(settings), factory.create(settings));
    }

    @Test
    void nonPositiveAndOversizedTimeoutsAreRejected() {
        NotificationRuntimeProperties zero = properties();
        zero.getSmtp().setReadTimeout(Duration.ZERO);
        assertThrows(BusinessRuleViolationException.class,
                () -> new SmtpClientFactory(zero).create(settings(SmtpSecurity.STARTTLS)));

        NotificationRuntimeProperties oversized = properties();
        oversized.getSmtp().setWriteTimeout(Duration.ofMillis((long) Integer.MAX_VALUE + 1));
        assertThrows(BusinessRuleViolationException.class,
                () -> new SmtpClientFactory(oversized).create(settings(SmtpSecurity.STARTTLS)));
    }

    private NotificationRuntimeProperties properties() {
        NotificationRuntimeProperties properties = new NotificationRuntimeProperties();
        properties.getSmtp().setPassword("runtime-only-test-fixture");
        return properties;
    }

    private NotificationSettings settings(SmtpSecurity security) {
        NotificationSettings settings = new NotificationSettings();
        settings.setSmtpProvider(SmtpProvider.CUSTOM);
        settings.setSmtpSecurity(security);
        settings.setSmtpHost("smtp.example.test");
        settings.setSmtpPort(587);
        settings.setSmtpUsername("mailer@example.test");
        return settings;
    }
}
