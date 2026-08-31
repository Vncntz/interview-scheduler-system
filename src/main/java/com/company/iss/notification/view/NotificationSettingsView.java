package com.company.iss.notification.view;

import com.company.iss.notification.config.SmtpProvider;
import com.company.iss.notification.config.SmtpSecurity;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.service.NotificationSettingsService;
import com.company.iss.notification.service.SmtpDiagnosticResult;
import com.company.iss.notification.service.SmtpDiagnosticsService;
import com.company.iss.shared.view.MainLayout;
import com.company.iss.shared.view.UserSafeNotifier;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasEnabled;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import java.util.List;

@Route(value = "notification-settings", layout = MainLayout.class)
@PageTitle("Notification Settings")
@RolesAllowed("ADMIN")
public class NotificationSettingsView extends VerticalLayout {

    private final NotificationSettingsService notificationSettingsService;
    private final SmtpDiagnosticsService diagnosticsService;
    private final Binder<NotificationSettings> binder = new Binder<>(NotificationSettings.class);

    private NotificationSettings persistedSettings;
    private boolean rebinding;
    private boolean passwordConfigured;

    private final TextField companyNameField = new TextField("Organization / Company");
    private final Checkbox emailEnabledField = new Checkbox("Enable Email Notifications");
    private final Select<SmtpProvider> smtpProviderField = new Select<>();
    private final TextField smtpHostField = new TextField("Host");
    private final IntegerField smtpPortField = new IntegerField("Port");
    private final Select<SmtpSecurity> smtpSecurityField = new Select<>();
    private final TextField smtpUsernameField = new TextField("Username");
    private final TextField smtpFromNameField = new TextField("Sender Name");
    private final TextField smtpFromAddressField = new TextField("Sender Email");
    private final TextField testRecipientField = new TextField("Test recipient");
    private final Span enabledBadge = new Span("Disabled");
    private final Span smtpPasswordStatus = new Span();
    private final Span connectionStatus = new Span("Not tested");
    private final Button testConnectionButton = new Button("Test Connection");
    private final Button sendTestEmailButton = new Button("Send Test Email");
    private final Button saveButton = new Button("Save Changes");
    private final Button discardButton = new Button("Discard Changes");

    public NotificationSettingsView(
            NotificationSettingsService notificationSettingsService,
            SmtpDiagnosticsService diagnosticsService
    ) {
        this.notificationSettingsService = notificationSettingsService;
        this.diagnosticsService = diagnosticsService;

        addClassName("notification-settings");
        setWidthFull();
        setPadding(true);

        configureFields();
        configureBinder();
        add(createHeader(), createSettingsCard(), createActions());
    }

    @PostConstruct
    private void init() {
        passwordConfigured = notificationSettingsService.isSmtpPasswordConfigured();
        persistedSettings = notificationSettingsService.getSettings();
        bindPersistedSettings();
    }

    private Component createHeader() {
        H1 title = new H1("Notification Settings");
        title.addClassName("notification-settings__title");
        Paragraph description = new Paragraph(
                "Configure how the Interview Scheduler sends email notifications."
        );
        description.addClassName("notification-settings__description");
        return new Div(title, description);
    }

    private Component createSettingsCard() {
        Div card = new Div();
        card.addClassName("notification-settings__card");

        H3 emailTitle = new H3("Email Notifications");
        emailTitle.addClassName("notification-settings__section-title");
        enabledBadge.addClassNames(
                "notification-settings__badge",
                "notification-settings__badge--disabled"
        );
        HorizontalLayout heading = new HorizontalLayout(emailTitle, enabledBadge);
        heading.addClassName("notification-settings__card-heading");
        heading.setAlignItems(Alignment.CENTER);
        heading.setJustifyContentMode(JustifyContentMode.BETWEEN);
        heading.setWidthFull();

        card.add(
                heading,
                emailEnabledField,
                section("Organization", oneColumn(companyNameField)),
                section("SMTP Provider", oneColumn(smtpProviderField)),
                section("SMTP Server", twoColumns(smtpHostField, smtpPortField)),
                section("Security", oneColumn(smtpSecurityField)),
                section("Authentication", authenticationContent()),
                section("Sender", twoColumns(smtpFromNameField, smtpFromAddressField)),
                section("Connection Status", connectionContent())
        );
        return card;
    }

    private Component authenticationContent() {
        Div passwordPanel = new Div(smtpPasswordStatus);
        passwordPanel.addClassName("notification-settings__password-status");
        FormLayout form = twoColumns(smtpUsernameField, passwordPanel);
        form.setColspan(passwordPanel, 1);
        return form;
    }

    private Component connectionContent() {
        connectionStatus.addClassNames(
                "notification-settings__connection-status",
                "notification-settings__connection-status--neutral"
        );
        testRecipientField.setHelperText("Used only when you explicitly send a test email.");
        HorizontalLayout buttons = new HorizontalLayout(testConnectionButton, sendTestEmailButton);
        buttons.setWrap(true);
        VerticalLayout content = new VerticalLayout(connectionStatus, testRecipientField, buttons);
        content.setPadding(false);
        content.setSpacing(true);
        return content;
    }

    private Div section(String title, Component content) {
        H3 heading = new H3(title);
        heading.addClassName("notification-settings__section-title");
        Div section = new Div(heading, content);
        section.addClassName("notification-settings__section");
        return section;
    }

    private FormLayout oneColumn(Component component) {
        FormLayout form = new FormLayout(component);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        return form;
    }

    private FormLayout twoColumns(Component first, Component second) {
        FormLayout form = new FormLayout(first, second);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );
        return form;
    }

    private Component createActions() {
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout actions = new HorizontalLayout(discardButton, saveButton);
        actions.addClassName("notification-settings__actions");
        actions.setJustifyContentMode(JustifyContentMode.END);
        actions.setWidthFull();
        return actions;
    }

    private void configureFields() {
        List.of(
                companyNameField,
                smtpHostField,
                smtpUsernameField,
                smtpFromNameField,
                smtpFromAddressField,
                testRecipientField
        ).forEach(field -> field.setWidthFull());
        smtpPortField.setWidthFull();
        smtpPortField.setMin(1);
        smtpPortField.setMax(65_535);

        smtpProviderField.setLabel("Provider");
        smtpProviderField.setItems(SmtpProvider.values());
        smtpProviderField.setItemLabelGenerator(SmtpProvider::getDisplayName);
        smtpProviderField.setWidthFull();

        smtpSecurityField.setLabel("Security mode");
        smtpSecurityField.setItems(SmtpSecurity.values());
        smtpSecurityField.setItemLabelGenerator(SmtpSecurity::getDisplayName);
        smtpSecurityField.setWidthFull();

        emailEnabledField.addValueChangeListener(event -> {
            updateEnabledState(Boolean.TRUE.equals(event.getValue()));
            resetConnectionStatus();
            updateActionState();
        });
        smtpProviderField.addValueChangeListener(event -> {
            if (!rebinding) {
                applyProviderPreset(event.getValue());
            }
        });

        List.of(smtpHostField, smtpUsernameField, smtpFromNameField, smtpFromAddressField)
                .forEach(field -> field.addValueChangeListener(event -> resetConnectionStatus()));
        smtpPortField.addValueChangeListener(event -> resetConnectionStatus());
        smtpSecurityField.addValueChangeListener(event -> resetConnectionStatus());

        testConnectionButton.addClickListener(event -> testConnection());
        sendTestEmailButton.addClickListener(event -> sendTestEmail());
        saveButton.addClickListener(event -> save());
        discardButton.addClickListener(event -> bindPersistedSettings());
    }

    private void configureBinder() {
        binder.forField(companyNameField)
                .asRequired("Organization / company is required.")
                .bind(NotificationSettings::getCompanyName, NotificationSettings::setCompanyName);
        binder.forField(emailEnabledField)
                .bind(NotificationSettings::getEmailEnabled, NotificationSettings::setEmailEnabled);
        binder.forField(smtpProviderField)
                .withValidator(value -> !emailEnabled() || value != null, "SMTP provider is required.")
                .bind(NotificationSettings::getSmtpProvider, NotificationSettings::setSmtpProvider);
        binder.forField(smtpHostField)
                .withValidator(value -> !emailEnabled() || hasText(value), "SMTP host is required.")
                .bind(NotificationSettings::getSmtpHost, NotificationSettings::setSmtpHost);
        binder.forField(smtpPortField)
                .withValidator(value -> !emailEnabled() || value != null, "SMTP port is required.")
                .withValidator(
                        value -> !emailEnabled() || (value != null && value >= 1 && value <= 65_535),
                        "SMTP port must be between 1 and 65535."
                )
                .bind(NotificationSettings::getSmtpPort, NotificationSettings::setSmtpPort);
        binder.forField(smtpSecurityField)
                .withValidator(value -> !emailEnabled() || value != null, "Security mode is required.")
                .bind(NotificationSettings::getSmtpSecurity, NotificationSettings::setSmtpSecurity);
        binder.forField(smtpUsernameField)
                .withValidator(value -> !emailEnabled() || hasText(value), "SMTP username is required.")
                .bind(NotificationSettings::getSmtpUsername, NotificationSettings::setSmtpUsername);
        binder.forField(smtpFromNameField)
                .withValidator(value -> !containsHeaderControl(value), "Sender name contains invalid characters.")
                .bind(NotificationSettings::getSmtpFromName, NotificationSettings::setSmtpFromName);
        binder.forField(smtpFromAddressField)
                .withValidator(value -> !emailEnabled() || isEmail(value), "Enter a valid sender email.")
                .bind(NotificationSettings::getSmtpFromAddress, NotificationSettings::setSmtpFromAddress);
        binder.addValueChangeListener(event -> updateActionState());
    }

    void applyProviderPreset(SmtpProvider provider) {
        if (provider == SmtpProvider.GMAIL) {
            smtpHostField.setValue("smtp.gmail.com");
            smtpPortField.setValue(587);
            smtpSecurityField.setValue(SmtpSecurity.STARTTLS);
        } else if (provider == SmtpProvider.MICROSOFT_365) {
            smtpHostField.setValue("smtp.office365.com");
            smtpPortField.setValue(587);
            smtpSecurityField.setValue(SmtpSecurity.STARTTLS);
        }
    }

    private void bindPersistedSettings() {
        if (persistedSettings == null) {
            return;
        }
        rebinding = true;
        try {
            binder.readBean(persistedSettings);
        } finally {
            rebinding = false;
        }
        testRecipientField.clear();
        smtpPasswordStatus.setText(passwordConfigured
                ? "✓ SMTP password configured securely via the runtime environment."
                : "⚠ SMTP password not configured. Set SMTP_PASSWORD in the runtime environment.");
        smtpPasswordStatus.getElement().setAttribute("data-configured", passwordConfigured);
        updateEnabledState(Boolean.TRUE.equals(persistedSettings.getEmailEnabled()));
        resetConnectionStatus();
        updateActionState();
    }

    private void updateEnabledState(boolean enabled) {
        enabledBadge.setText(enabled ? "Enabled" : "Disabled");
        enabledBadge.removeClassNames(
                "notification-settings__badge--enabled",
                "notification-settings__badge--disabled"
        );
        enabledBadge.addClassName(enabled
                ? "notification-settings__badge--enabled"
                : "notification-settings__badge--disabled");

        List.<HasEnabled>of(
                smtpProviderField,
                smtpHostField,
                smtpPortField,
                smtpSecurityField,
                smtpUsernameField,
                smtpFromNameField,
                smtpFromAddressField,
                testRecipientField
        ).forEach(field -> field.setEnabled(enabled));
        testConnectionButton.setEnabled(enabled && passwordConfigured);
        sendTestEmailButton.setEnabled(enabled && passwordConfigured);
    }

    private void updateActionState() {
        if (persistedSettings == null) {
            saveButton.setEnabled(false);
            discardButton.setEnabled(false);
            return;
        }
        boolean dirty = binder.hasChanges();
        boolean valid = binder.isValid() && (!emailEnabled() || passwordConfigured);
        saveButton.setEnabled(dirty && valid);
        discardButton.setEnabled(dirty);
    }

    private NotificationSettings bufferedCandidate() throws ValidationException {
        NotificationSettings candidate = copyOf(persistedSettings);
        binder.writeBean(candidate);
        candidate.setSmsEnabled(false);
        return candidate;
    }

    private void save() {
        try {
            NotificationSettings saved = notificationSettingsService.save(bufferedCandidate());
            persistedSettings = saved;
            bindPersistedSettings();
            showFeedback("Notification settings saved.", true);
        } catch (Exception exception) {
            UserSafeNotifier.showError(exception);
        }
    }

    private void testConnection() {
        try {
            showDiagnosticResult(diagnosticsService.testConnection(bufferedCandidate()));
        } catch (Exception exception) {
            UserSafeNotifier.showError(exception);
        }
    }

    private void sendTestEmail() {
        try {
            showDiagnosticResult(diagnosticsService.sendTestEmail(
                    bufferedCandidate(),
                    testRecipientField.getValue()
            ));
        } catch (Exception exception) {
            UserSafeNotifier.showError(exception);
        }
    }

    private void showDiagnosticResult(SmtpDiagnosticResult result) {
        connectionStatus.setText(result.message());
        connectionStatus.removeClassNames(
                "notification-settings__connection-status--neutral",
                "notification-settings__connection-status--success",
                "notification-settings__connection-status--error"
        );
        connectionStatus.addClassName(result.success()
                ? "notification-settings__connection-status--success"
                : "notification-settings__connection-status--error");
        showFeedback(result.message(), result.success());
    }

    private void resetConnectionStatus() {
        connectionStatus.setText("Not tested");
        connectionStatus.removeClassNames(
                "notification-settings__connection-status--success",
                "notification-settings__connection-status--error"
        );
        connectionStatus.addClassName("notification-settings__connection-status--neutral");
    }

    private void showFeedback(String message, boolean success) {
        Notification notification = Notification.show(
                message,
                3500,
                Notification.Position.TOP_CENTER
        );
        notification.addThemeVariants(success
                ? NotificationVariant.LUMO_SUCCESS
                : NotificationVariant.LUMO_ERROR);
    }

    private NotificationSettings copyOf(NotificationSettings source) {
        NotificationSettings copy = new NotificationSettings();
        copy.setId(source.getId());
        copy.setVersion(source.getVersion());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setCompanyName(source.getCompanyName());
        copy.setEmailEnabled(source.getEmailEnabled());
        copy.setSmsEnabled(false);
        copy.setSmtpProvider(source.getSmtpProvider());
        copy.setSmtpHost(source.getSmtpHost());
        copy.setSmtpPort(source.getSmtpPort());
        copy.setSmtpSecurity(source.getSmtpSecurity());
        copy.setSmtpUsername(source.getSmtpUsername());
        copy.setSmtpFromName(source.getSmtpFromName());
        copy.setSmtpFromAddress(source.getSmtpFromAddress());
        copy.setSmsProvider(source.getSmsProvider());
        copy.setSmsSenderName(source.getSmsSenderName());
        copy.setActive(source.getActive());
        return copy;
    }

    private boolean emailEnabled() {
        return Boolean.TRUE.equals(emailEnabledField.getValue());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean containsHeaderControl(String value) {
        return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
    }

    private boolean isEmail(String value) {
        if (!hasText(value) || containsHeaderControl(value)) {
            return false;
        }
        try {
            InternetAddress address = new InternetAddress(value.trim(), true);
            address.validate();
            return address.getPersonal() == null && value.trim().equals(address.getAddress());
        } catch (AddressException exception) {
            return false;
        }
    }
}
