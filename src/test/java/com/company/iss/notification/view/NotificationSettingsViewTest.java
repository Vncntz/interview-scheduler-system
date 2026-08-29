package com.company.iss.notification.view;

import com.company.iss.notification.service.NotificationSettingsService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class NotificationSettingsViewTest {

    @Test
    void viewContainsNoSecretInputFields() {
        NotificationSettingsView view = new NotificationSettingsView(mock(NotificationSettingsService.class));

        assertEquals(0, descendants(view).filter(PasswordField.class::isInstance).count());
        assertEquals(0, descendants(view)
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .filter(field -> hasSecretLabel(field.getLabel()))
                .count());
    }

    @Test
    void unsupportedSmsControlsAreReadOnly() {
        NotificationSettingsView view = new NotificationSettingsView(mock(NotificationSettingsService.class));

        Checkbox smsEnabled = descendants(view)
                .filter(Checkbox.class::isInstance)
                .map(Checkbox.class::cast)
                .filter(checkbox -> "Enable SMS Notifications".equals(checkbox.getLabel()))
                .findFirst()
                .orElseThrow();
        assertFalse(smsEnabled.isEnabled());

        descendants(view)
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .filter(field -> field.getLabel().startsWith("SMS "))
                .forEach(field -> assertFalse(field.isEnabled()));
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }

    private boolean hasSecretLabel(String label) {
        String normalized = label == null ? "" : label.toLowerCase();
        return normalized.contains("password") || normalized.contains("api key");
    }
}
