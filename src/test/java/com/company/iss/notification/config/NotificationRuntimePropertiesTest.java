package com.company.iss.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationRuntimePropertiesTest {

    @Test
    void smtpPasswordBindsWithoutCustomStringRepresentations() {
        String fixture = "runtime-only-test-fixture";
        MockEnvironment environment = new MockEnvironment()
                .withProperty("iss.notification.smtp.password", fixture);
        NotificationRuntimeProperties properties = new NotificationRuntimeProperties();

        Binder.get(environment).bind("iss.notification", Bindable.ofInstance(properties));

        assertTrue(properties.getSmtp().isPasswordConfigured());
        assertThrows(
                NoSuchMethodException.class,
                () -> NotificationRuntimeProperties.class.getDeclaredMethod("toString")
        );
        assertThrows(
                NoSuchMethodException.class,
                () -> NotificationRuntimeProperties.Smtp.class.getDeclaredMethod("toString")
        );
    }

    @Test
    void missingPasswordIsSafeAtStartup() {
        NotificationRuntimeProperties properties = new NotificationRuntimeProperties();

        assertFalse(properties.getSmtp().isPasswordConfigured());
        assertEquals(Duration.ofSeconds(5), properties.getSmtp().getConnectionTimeout());
        assertEquals(Duration.ofSeconds(5), properties.getSmtp().getReadTimeout());
        assertEquals(Duration.ofSeconds(5), properties.getSmtp().getWriteTimeout());
    }

    @Test
    void allSmtpTimeoutsBindAsDurations() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("iss.notification.smtp.connection-timeout", "2s")
                .withProperty("iss.notification.smtp.read-timeout", "3s")
                .withProperty("iss.notification.smtp.write-timeout", "4s");
        NotificationRuntimeProperties properties = new NotificationRuntimeProperties();

        Binder.get(environment).bind("iss.notification", Bindable.ofInstance(properties));

        assertEquals(Duration.ofSeconds(2), properties.getSmtp().getConnectionTimeout());
        assertEquals(Duration.ofSeconds(3), properties.getSmtp().getReadTimeout());
        assertEquals(Duration.ofSeconds(4), properties.getSmtp().getWriteTimeout());
    }
}
