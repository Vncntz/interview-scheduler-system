package com.company.iss.notification.view;

import com.company.iss.notification.config.SmtpProvider;
import com.company.iss.notification.config.SmtpSecurity;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.service.NotificationSettingsService;
import com.company.iss.notification.service.SmtpDiagnosticsService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationSettingsViewTest {

    @Test
    void viewContainsNoSmsOrSecretInputFields() {
        NotificationSettingsView view = viewWithoutInitialization();

        assertEquals(0, descendants(view).filter(PasswordField.class::isInstance).count());
        assertEquals(0, descendants(view)
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .filter(field -> hasSecretLabel(field.getLabel()))
                .count());
        assertFalse(descendants(view).anyMatch(component -> visibleText(component).contains("SMS")));
    }

    @Test
    void providerPresetsOverrideServerDefaultsButCustomRetainsValues() {
        NotificationSettingsView view = viewWithoutInitialization();
        TextField host = textField(view, "Host");

        view.applyProviderPreset(SmtpProvider.GMAIL);
        assertEquals("smtp.gmail.com", host.getValue());

        view.applyProviderPreset(SmtpProvider.MICROSOFT_365);
        assertEquals("smtp.office365.com", host.getValue());

        view.applyProviderPreset(SmtpProvider.CUSTOM);
        assertEquals("smtp.office365.com", host.getValue());
    }

    @Test
    void dirtyStateEnablesSaveAndDiscardRestoresPersistedSnapshot() throws Exception {
        NotificationSettingsView view = initializedView(false, true);
        Button save = button(view, "Save Changes");
        Button discard = button(view, "Discard Changes");
        TextField company = textField(view, "Organization / Company");

        assertFalse(save.isEnabled());
        assertFalse(discard.isEnabled());

        company.setValue("Changed Organization");
        assertTrue(save.isEnabled());
        assertTrue(discard.isEnabled());

        discard.click();
        assertEquals("ISS Notifications", company.getValue());
        assertFalse(save.isEnabled());
    }

    @Test
    void disabledEmailDisablesDiagnosticsAndEnablingRestoresThemWhenPasswordExists() throws Exception {
        NotificationSettingsView view = initializedView(false, true);
        Button connection = button(view, "Test Connection");
        Button send = button(view, "Send Test Email");

        assertFalse(connection.isEnabled());
        assertFalse(send.isEnabled());

        checkbox(view, "Enable Email Notifications").setValue(true);
        assertTrue(connection.isEnabled());
        assertTrue(send.isEnabled());
    }

    @Test
    void runtimePasswordStatusNeverContainsSecretValue() throws Exception {
        NotificationSettingsView view = initializedView(false, true);

        String allText = descendants(view).map(this::visibleText).reduce("", String::concat);
        assertTrue(allText.contains("SMTP password configured"));
        assertFalse(allText.contains("runtime-only-test-fixture"));
    }

    private NotificationSettingsView initializedView(boolean emailEnabled, boolean passwordConfigured)
            throws Exception {
        NotificationSettingsService settingsService = mock(NotificationSettingsService.class);
        when(settingsService.isSmtpPasswordConfigured()).thenReturn(passwordConfigured);
        when(settingsService.getSettings()).thenReturn(settings(emailEnabled));
        NotificationSettingsView view = new NotificationSettingsView(
                settingsService,
                mock(SmtpDiagnosticsService.class)
        );
        Method init = NotificationSettingsView.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(view);
        return view;
    }

    private NotificationSettingsView viewWithoutInitialization() {
        return new NotificationSettingsView(
                mock(NotificationSettingsService.class),
                mock(SmtpDiagnosticsService.class)
        );
    }

    private NotificationSettings settings(boolean enabled) {
        NotificationSettings settings = new NotificationSettings();
        settings.setId(1L);
        settings.setVersion(0L);
        settings.setCompanyName("ISS Notifications");
        settings.setEmailEnabled(enabled);
        settings.setSmsEnabled(false);
        settings.setSmtpProvider(SmtpProvider.CUSTOM);
        settings.setSmtpHost("smtp.example.test");
        settings.setSmtpPort(587);
        settings.setSmtpSecurity(SmtpSecurity.STARTTLS);
        settings.setSmtpUsername("mailer@example.test");
        settings.setSmtpFromName("Interview Scheduler");
        settings.setSmtpFromAddress("notifications@example.test");
        settings.setActive(true);
        return settings;
    }

    private TextField textField(NotificationSettingsView view, String label) {
        return descendants(view)
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .filter(field -> label.equals(field.getLabel()))
                .findFirst()
                .orElseThrow();
    }

    private Checkbox checkbox(NotificationSettingsView view, String label) {
        return descendants(view)
                .filter(Checkbox.class::isInstance)
                .map(Checkbox.class::cast)
                .filter(field -> label.equals(field.getLabel()))
                .findFirst()
                .orElseThrow();
    }

    private Button button(NotificationSettingsView view, String text) {
        return descendants(view)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }

    private String visibleText(Component component) {
        if (component instanceof Button button) {
            return button.getText();
        }
        if (component instanceof com.vaadin.flow.component.html.Span span) {
            return span.getText();
        }
        if (component instanceof com.vaadin.flow.component.html.H1 heading) {
            return heading.getText();
        }
        if (component instanceof com.vaadin.flow.component.html.H3 heading) {
            return heading.getText();
        }
        return "";
    }

    private boolean hasSecretLabel(String label) {
        String normalized = label == null ? "" : label.toLowerCase();
        return normalized.contains("password") || normalized.contains("api key");
    }
}
