package com.company.iss.notification.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.company.iss.config.AsyncConfig;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.repository.NotificationSettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {AsyncConfig.class, EmailServiceAsyncTest.TestConfiguration.class})
class EmailServiceAsyncTest {

    @Autowired
    private EmailService emailService;

    @Autowired
    private NotificationSettingsRepository notificationSettingsRepository;

    private Logger emailLogger;
    private Logger asyncLogger;
    private boolean emailLoggerAdditive;
    private boolean asyncLoggerAdditive;
    private LatchingAppender emailAppender;
    private LatchingAppender asyncAppender;

    @BeforeEach
    void attachLogAppenders() {
        emailLogger = (Logger) LoggerFactory.getLogger(EmailService.class);
        asyncLogger = (Logger) LoggerFactory.getLogger(AsyncConfig.class);
        emailLoggerAdditive = emailLogger.isAdditive();
        asyncLoggerAdditive = asyncLogger.isAdditive();
        emailLogger.setAdditive(false);
        asyncLogger.setAdditive(false);

        emailAppender = new LatchingAppender(1);
        asyncAppender = new LatchingAppender(1);
        emailAppender.start();
        asyncAppender.start();
        emailLogger.addAppender(emailAppender);
        asyncLogger.addAppender(asyncAppender);
    }

    @AfterEach
    void detachLogAppenders() {
        emailLogger.detachAppender(emailAppender);
        asyncLogger.detachAppender(asyncAppender);
        emailLogger.setAdditive(emailLoggerAdditive);
        asyncLogger.setAdditive(asyncLoggerAdditive);
        emailAppender.stop();
        asyncAppender.stop();
    }

    @Test
    void handledAuthenticationConfigurationFailureIsNotLoggedAgainByAsyncHandler()
            throws InterruptedException {
        NotificationSettings settings = new NotificationSettings();
        settings.setEmailEnabled(true);
        settings.setSmtpHost("smtp.example.com");
        settings.setSmtpPort(587);
        settings.setSmtpUsername("mailer@example.com");
        settings.setSmtpPassword(" ");
        when(notificationSettingsRepository.findByActiveTrue()).thenReturn(Optional.of(settings));

        emailService.send("admin@example.com", "Subject", "Body");

        assertTrue(emailAppender.await(Duration.ofSeconds(2)));
        assertEquals(1, emailAppender.events().size());
        assertTrue(emailAppender.events().getFirst().getFormattedMessage()
                .contains("reason=SMTP_AUTHENTICATION_FAILED"));
        assertFalse(asyncAppender.await(Duration.ofMillis(200)));
        assertTrue(asyncAppender.events().isEmpty());
    }

    @Configuration
    static class TestConfiguration {

        @Bean
        NotificationSettingsRepository notificationSettingsRepository() {
            return mock(NotificationSettingsRepository.class);
        }

        @Bean
        NotificationSettingsService notificationSettingsService() {
            return new NotificationSettingsService();
        }

        @Bean
        EmailService emailService(NotificationSettingsService notificationSettingsService) {
            return new EmailService(notificationSettingsService);
        }
    }

    private static final class LatchingAppender extends AppenderBase<ILoggingEvent> {

        private final CountDownLatch latch;
        private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();

        private LatchingAppender(int expectedEvents) {
            latch = new CountDownLatch(expectedEvents);
        }

        @Override
        protected void append(ILoggingEvent event) {
            events.add(event);
            latch.countDown();
        }

        private boolean await(Duration timeout) throws InterruptedException {
            return latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private List<ILoggingEvent> events() {
            return events;
        }
    }
}
