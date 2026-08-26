package com.company.iss.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.company.iss.notification.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncConfigTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private boolean loggerAdditive;

    @BeforeEach
    void attachLogAppender() {
        logger = (Logger) LoggerFactory.getLogger(AsyncConfig.class);
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
    void unhandledFailureKeepsStackTraceAndRedactsSensitiveMessage() throws Exception {
        AsyncConfig asyncConfig = new AsyncConfig();
        Method method = EmailService.class.getMethod(
                "send",
                String.class,
                String.class,
                String.class
        );
        IllegalStateException exception = new IllegalStateException(
                "Unexpected provider failure password=do-not-log"
        );

        asyncConfig.getAsyncUncaughtExceptionHandler()
                .handleUncaughtException(exception, method, "recipient", "subject", "body");

        ILoggingEvent event = appender.list.getFirst();
        assertTrue(event.getFormattedMessage().contains("[ASYNC] Unhandled async failure"));
        assertTrue(event.getFormattedMessage().contains("method=send"));
        assertTrue(event.getFormattedMessage().contains("exception=IllegalStateException"));
        assertTrue(event.getFormattedMessage().contains("password=***"));
        assertFalse(event.getFormattedMessage().contains("do-not-log"));
        assertNotNull(event.getThrowableProxy());
        assertFalse(event.getThrowableProxy().getMessage().contains("do-not-log"));
    }
}
