package com.company.iss.notification.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.config.SmtpProvider;
import com.company.iss.notification.config.SmtpSecurity;
import com.company.iss.notification.entity.NotificationSettings;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.net.ConnectException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private boolean loggerAdditive;

    @BeforeEach
    void attachLogAppender() {
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
        Fixture fixture = fixture("");

        fixture.service().send("john.smith@example.com", "Subject", "Body");

        String message = appender.list.getFirst().getFormattedMessage();
        assertTrue(message.contains("recipient=jo***@example.com"));
        assertTrue(message.contains("reason=SMTP_AUTHENTICATION_FAILED"));
        assertTrue(message.contains("detail=\"SMTP password is missing\""));
        assertFalse(message.contains("john.smith@example.com"));
        verify(fixture.factory(), never()).create(any());
    }

    @Test
    void senderNameAndSeparateFromAddressAreUsedInMimeMessage() throws Exception {
        Fixture fixture = fixture("password");

        fixture.service().send("admin@example.com", "Subject", "Body");

        InternetAddress from = (InternetAddress) fixture.message().getFrom()[0];
        assertEquals("notifications@example.test", from.getAddress());
        assertEquals("Interview Scheduler", from.getPersonal());
        assertEquals("admin@example.com", ((InternetAddress) fixture.message().getAllRecipients()[0]).getAddress());
        verify(fixture.sender()).send(fixture.message());
    }

    @Test
    void testEmailUsesExplicitLabelAndIsSentExactlyOnce() throws Exception {
        Fixture fixture = fixture("password");

        fixture.service().sendTestEmail(fixture.settings(), "admin@example.test");

        assertEquals("Interview Scheduler SMTP configuration test", fixture.message().getSubject());
        verify(fixture.sender()).send(fixture.message());
    }

    @Test
    void authenticationFailureIsContainedAndSanitized() {
        Fixture fixture = fixture("password");
        doThrow(new MailAuthenticationException("raw provider secret"))
                .when(fixture.sender()).send(any(MimeMessage.class));

        fixture.service().send("admin@example.com", "Subject", "Body");

        String message = appender.list.getFirst().getFormattedMessage();
        assertTrue(message.contains("reason=SMTP_AUTHENTICATION_FAILED"));
        assertFalse(message.contains("raw provider secret"));
        assertFalse(message.contains("admin@example.com"));
        assertTrue(appender.list.getFirst().getThrowableProxy() == null);
    }

    @Test
    void connectionFailureUsesSpecificOperationalReason() {
        Fixture fixture = fixture("password");
        doThrow(new MailSendException("Connection failed", new ConnectException("Connection refused")))
                .when(fixture.sender()).send(any(MimeMessage.class));

        fixture.service().send("admin@example.com", "Subject", "Body");

        String message = appender.list.getFirst().getFormattedMessage();
        assertTrue(message.contains("reason=SMTP_CONNECTION_FAILED"));
        assertFalse(message.contains("Connection refused"));
        assertTrue(appender.list.getFirst().getThrowableProxy() == null);
    }

    @Test
    void emailDisabledSkipsFactoryAndUnexpectedProgrammingFailureEscapes() {
        Fixture disabled = fixture("password");
        disabled.settings().setEmailEnabled(false);
        disabled.service().send("admin@example.com", "Subject", "Body");
        verify(disabled.factory(), never()).create(any());

        Fixture unexpected = fixture("password");
        when(unexpected.factory().create(unexpected.settings()))
                .thenThrow(new IllegalStateException("Unexpected failure"));
        assertThrows(IllegalStateException.class,
                () -> unexpected.service().send("admin@example.com", "Subject", "Body"));
    }

    @Test
    void malformedRecipientMaskNeverLeaksInjectedSecondAddress() {
        String masked = EmailService.maskRecipient(
                "admin@example.test\r\nBcc: attacker@example.test"
        );

        assertEquals("ad***", masked);
        assertFalse(masked.contains("attacker"));
        assertFalse(masked.contains("Bcc"));
    }

    @Test
    void synchronousReminderSeamClassifiesSuccessAndKnownProviderFailure() {
        Fixture success = fixture("password");
        assertEquals(
                ReminderNotificationResult.Disposition.SENT,
                success.service().sendSynchronously("admin@example.com", "Subject", "Body").disposition()
        );

        Fixture failure = fixture("password");
        doThrow(new MailAuthenticationException("raw provider response"))
                .when(failure.sender()).send(any(MimeMessage.class));
        ReminderNotificationResult result = failure.service()
                .sendSynchronously("admin@example.com", "Subject", "Body");

        assertEquals(ReminderNotificationResult.Disposition.RETRYABLE_FAILURE, result.disposition());
        assertEquals("SMTP_AUTHENTICATION_FAILED", result.reason());
    }

    @Test
    void synchronousReminderSeamTreatsInvalidRecipientAsTerminalSkip() {
        Fixture fixture = fixture("password");

        ReminderNotificationResult result = fixture.service()
                .sendSynchronously("not-an-email", "Subject", "Body");

        assertEquals(ReminderNotificationResult.Disposition.SKIPPED, result.disposition());
        assertEquals("INVALID_RECIPIENT", result.reason());
        verify(fixture.factory(), never()).create(any());
    }

    private Fixture fixture(String password) {
        NotificationRuntimeProperties properties = new NotificationRuntimeProperties();
        properties.getSmtp().setPassword(password);
        SmtpConfigurationValidator validator = new SmtpConfigurationValidator(properties);
        NotificationSettingsService settingsService = mock(NotificationSettingsService.class);
        NotificationSettings settings = enabledSettings();
        when(settingsService.getSettings()).thenReturn(settings);

        SmtpClientFactory factory = mock(SmtpClientFactory.class);
        JavaMailSenderImpl sender = mock(JavaMailSenderImpl.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(factory.create(settings)).thenReturn(sender);
        when(sender.createMimeMessage()).thenReturn(message);
        return new Fixture(
                settings,
                sender,
                factory,
                message,
                new EmailService(settingsService, validator, factory)
        );
    }

    private NotificationSettings enabledSettings() {
        NotificationSettings settings = new NotificationSettings();
        settings.setEmailEnabled(true);
        settings.setSmtpProvider(SmtpProvider.CUSTOM);
        settings.setSmtpHost("smtp.example.test");
        settings.setSmtpPort(587);
        settings.setSmtpSecurity(SmtpSecurity.STARTTLS);
        settings.setSmtpUsername("mailer@example.test");
        settings.setSmtpFromName("Interview Scheduler");
        settings.setSmtpFromAddress("notifications@example.test");
        return settings;
    }

    private record Fixture(
            NotificationSettings settings,
            JavaMailSenderImpl sender,
            SmtpClientFactory factory,
            MimeMessage message,
            EmailService service
    ) {
    }
}
