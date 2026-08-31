package com.company.iss.notification.service;

import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.config.SmtpProvider;
import com.company.iss.notification.config.SmtpSecurity;
import com.company.iss.notification.entity.NotificationSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmtpConfigurationValidatorTest {

    @Test
    void completeConfigurationIsReady() {
        assertTrue(validator("password").isDeliveryReady(complete()));
    }

    @Test
    void disabledEmailMayRemainIncompleteButIsNotDeliveryReady() {
        NotificationSettings settings = new NotificationSettings();
        settings.setEmailEnabled(false);
        assertDoesNotThrow(() -> validator("").validateForSave(settings));
        assertFalse(validator("").isDeliveryReady(settings));
    }

    @Test
    void missingPasswordHostUsernameAndInvalidPortAreRejected() {
        assertFailure(complete(), "", SmtpConfigurationValidator.Failure.PASSWORD_MISSING);

        NotificationSettings missingHost = complete();
        missingHost.setSmtpHost(" ");
        assertFailure(missingHost, "password", SmtpConfigurationValidator.Failure.HOST_MISSING);

        NotificationSettings missingUsername = complete();
        missingUsername.setSmtpUsername(null);
        assertFailure(missingUsername, "password", SmtpConfigurationValidator.Failure.USERNAME_MISSING);

        NotificationSettings invalidPort = complete();
        invalidPort.setSmtpPort(65_536);
        assertFailure(invalidPort, "password", SmtpConfigurationValidator.Failure.PORT_INVALID);
    }

    @Test
    void senderAndRecipientMustBeStrictHeaderSafeMailboxes() {
        NotificationSettings missingSender = complete();
        missingSender.setSmtpFromAddress(null);
        assertFailure(missingSender, "password", SmtpConfigurationValidator.Failure.FROM_ADDRESS_MISSING);

        NotificationSettings malformed = complete();
        malformed.setSmtpFromAddress("not-an-email");
        assertFailure(malformed, "password", SmtpConfigurationValidator.Failure.FROM_ADDRESS_INVALID);

        NotificationSettings injected = complete();
        injected.setSmtpFromAddress("sender@example.test\r\nBcc: attacker@example.test");
        assertFailure(injected, "password", SmtpConfigurationValidator.Failure.FROM_ADDRESS_INVALID);

        SmtpConfigurationValidator validator = validator("password");
        assertThrows(SmtpConfigurationException.class,
                () -> validator.validateRecipient("admin@example.test\nBcc: attacker@example.test"));
        assertDoesNotThrow(() -> validator.validateRecipient("admin@example.test"));
    }

    private void assertFailure(
            NotificationSettings settings,
            String password,
            SmtpConfigurationValidator.Failure expected
    ) {
        SmtpConfigurationException exception = assertThrows(
                SmtpConfigurationException.class,
                () -> validator(password).requireDeliveryReady(settings)
        );
        org.junit.jupiter.api.Assertions.assertEquals(expected, exception.getFailure());
    }

    private SmtpConfigurationValidator validator(String password) {
        NotificationRuntimeProperties properties = new NotificationRuntimeProperties();
        properties.getSmtp().setPassword(password);
        return new SmtpConfigurationValidator(properties);
    }

    private NotificationSettings complete() {
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
}
