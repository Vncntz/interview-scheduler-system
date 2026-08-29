package com.company.iss.notification.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.config.NotificationRuntimeProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private boolean loggerAdditive;
    private NotificationRuntimeProperties runtimeProperties;

    @BeforeEach
    void attachLogAppender() {
        runtimeProperties = new NotificationRuntimeProperties();
        runtimeProperties.getSmtp().setPassword("unit-test-password");
        logger = (Logger) LoggerFactory.getLogger(EmailService.class);
        loggerAdditive = logger.isAdditive();
        logger.setAdditive(false);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(appender);
        logger.setAdditive(loggerAdditive);
        appender.stop();
    }

    @Test
    void missingPasswordIsLoggedConciseAndRecipientIsMasked() {
        NotificationSettingsService settingsService = mock(NotificationSettingsService.class);
        NotificationSettings settings = enabledSettings();
        runtimeProperties.getSmtp().setPassword(" ");
        when(settingsService.getSettings()).thenReturn(settings);
        EmailService emailService = spy(new EmailService(settingsService, runtimeProperties));

        emailService.send("john.smith@example.com", "Subject", "Body");

        assertEquals(1, appender.list.size());
        String message = appender.list.getFirst().getFormattedMessage();
        assertTrue(message.contains("[EMAIL] Email delivery failed"));
        assertTrue(message.contains("recipient=jo***@example.com"));
        assertTrue(message.contains("reason=SMTP_AUTHENTICATION_FAILED"));
        assertTrue(message.contains("detail=\"SMTP password is missing\""));
        assertFalse(message.contains("john.smith@example.com"));
        verify(emailService, never()).createMailSender(any());
    }

    @Test
    void authenticationFailureDoesNotEscapeToAsyncHandler() {
        NotificationSettingsService settingsService = mock(NotificationSettingsService.class);
        NotificationSettings settings = enabledSettings();
        when(settingsService.getSettings()).thenReturn(settings);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailAuthenticationException("Authentication failed"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));
        EmailService emailService = spy(new EmailService(settingsService, runtimeProperties));
        doReturn(mailSender).when(emailService).createMailSender(settings);

        emailService.send("admin@example.com", "Subject", "Body");

        assertEquals(1, appender.list.size());
        String message = appender.list.getFirst().getFormattedMessage();
        assertTrue(message.contains("recipient=ad***@example.com"));
        assertTrue(message.contains("reason=SMTP_AUTHENTICATION_FAILED"));
        assertFalse(message.contains("Authentication failed"));
        assertFalse(message.contains("admin@example.com"));
        assertTrue(appender.list.getFirst().getThrowableProxy() == null);
    }

    @Test
    void successfulDeliveryProducesStructuredInfoLog() {
        NotificationSettingsService settingsService = mock(NotificationSettingsService.class);
        NotificationSettings settings = enabledSettings();
        when(settingsService.getSettings()).thenReturn(settings);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailService emailService = spy(new EmailService(settingsService, runtimeProperties));
        doReturn(mailSender).when(emailService).createMailSender(settings);

        emailService.send("admin@example.com", "Subject", "Body");

        verify(mailSender).send(any(SimpleMailMessage.class));
        assertEquals("[EMAIL] Email sent recipient=ad***@example.com",
                appender.list.getFirst().getFormattedMessage());
    }

    @Test
    void connectionFailureUsesSpecificOperationalReason() {
        NotificationSettingsService settingsService = mock(NotificationSettingsService.class);
        NotificationSettings settings = enabledSettings();
        when(settingsService.getSettings()).thenReturn(settings);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("Connection failed", new ConnectException("Connection refused")))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));
        EmailService emailService = spy(new EmailService(settingsService, runtimeProperties));
        doReturn(mailSender).when(emailService).createMailSender(settings);

        emailService.send("admin@example.com", "Subject", "Body");

        assertEquals(1, appender.list.size());
        String message = appender.list.getFirst().getFormattedMessage();
        assertTrue(message.contains("reason=SMTP_CONNECTION_FAILED"));
        assertTrue(message.contains("detail=\"SMTP server is unavailable\""));
        assertFalse(message.contains("Connection refused"));
        assertTrue(appender.list.getFirst().getThrowableProxy() == null);
    }

    @Test
    void unexpectedFailureIsNotSilentlySwallowed() {
        NotificationSettingsService settingsService = mock(NotificationSettingsService.class);
        NotificationSettings settings = enabledSettings();
        when(settingsService.getSettings()).thenReturn(settings);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new IllegalStateException("Unexpected failure"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));
        EmailService emailService = spy(new EmailService(settingsService, runtimeProperties));
        doReturn(mailSender).when(emailService).createMailSender(settings);

        assertThrows(
                IllegalStateException.class,
                () -> emailService.send("admin@example.com", "Subject", "Body")
        );
    }

    private NotificationSettings enabledSettings() {
        NotificationSettings settings = new NotificationSettings();
        settings.setEmailEnabled(true);
        settings.setSmtpHost("smtp.example.com");
        settings.setSmtpPort(587);
        settings.setSmtpUsername("mailer@example.com");
        return settings;
    }
}
