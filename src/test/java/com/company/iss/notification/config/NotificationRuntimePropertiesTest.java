package com.company.iss.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

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
    }
}
