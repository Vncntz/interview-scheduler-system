package com.company.iss.notification.view;

import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.service.NotificationSettingsService;
import com.company.iss.shared.view.MainLayout;
import com.company.iss.shared.view.UserSafeNotifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "notification-settings", layout = MainLayout.class)
@PageTitle("Notification Settings")
@RolesAllowed("ADMIN")
public class NotificationSettingsView extends VerticalLayout {

    private final NotificationSettingsService notificationSettingsService;

    private NotificationSettings settings;

    private TextField companyNameField;
    private Checkbox emailEnabledField;
    private Checkbox smsEnabledField;

    private TextField smtpHostField;
    private IntegerField smtpPortField;
    private TextField smtpUsernameField;
    private TextField smtpFromNameField;

    private TextField smsProviderField;
    private TextField smsSenderNameField;
    private Span smtpPasswordStatus;

    public NotificationSettingsView(NotificationSettingsService notificationSettingsService) {
        this.notificationSettingsService = notificationSettingsService;
        setSizeFull();

        initFields();

        add(new H3("Notification Settings"), createGeneralForm(), createEmailForm(), createSmsForm(), createActions());
    }

    private FormLayout createGeneralForm() {
        FormLayout form = new FormLayout();

        form.add(companyNameField);

        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        return form;
    }

    private FormLayout createEmailForm() {
        FormLayout form = new FormLayout();

        form.add(emailEnabledField, smtpHostField, smtpPortField, smtpUsernameField, smtpFromNameField, smtpPasswordStatus);

        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("700px", 2));

        return form;
    }

    private FormLayout createSmsForm() {
        FormLayout form = new FormLayout();

        form.add(smsEnabledField, smsProviderField, smsSenderNameField);

        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("700px", 2));

        return form;
    }

    private HorizontalLayout createActions() {
        Button saveButton = new Button("Save");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        saveButton.addClickListener(e -> save());

        HorizontalLayout actions = new HorizontalLayout(saveButton);

        actions.setWidthFull();

        return actions;
    }

    private void initFields() {
        companyNameField = new TextField("Company Name");
        companyNameField.setWidthFull();

        emailEnabledField = new Checkbox("Enable Email Notifications");

        smsEnabledField = new Checkbox("Enable SMS Notifications");
        smsEnabledField.setEnabled(false);
        smsEnabledField.setHelperText("SMS delivery is not supported.");

        smtpHostField = new TextField("SMTP Host");
        smtpHostField.setWidthFull();

        smtpPortField = new IntegerField("SMTP Port");
        smtpPortField.setWidthFull();

        smtpUsernameField = new TextField("SMTP Username");
        smtpUsernameField.setWidthFull();

        smtpFromNameField = new TextField("SMTP Sender Name");
        smtpFromNameField.setWidthFull();

        smtpPasswordStatus = new Span();

        smsProviderField = new TextField("SMS Provider");
        smsProviderField.setWidthFull();

        smsSenderNameField = new TextField("SMS Sender Name");
        smsSenderNameField.setWidthFull();

        emailEnabledField.addValueChangeListener(e -> toggleEmailFields(e.getValue()));
        disableSmsFields();
    }

    @PostConstruct
    private void init() {
        settings = notificationSettingsService.getSettings();

        companyNameField.setValue(safe(settings.getCompanyName()));

        emailEnabledField.setValue(settings.getEmailEnabled());

        smsEnabledField.setValue(false);

        smtpHostField.setValue(safe(settings.getSmtpHost()));

        smtpPortField.setValue(settings.getSmtpPort() == null ? 587 : settings.getSmtpPort());

        smtpUsernameField.setValue(safe(settings.getSmtpUsername()));

        smtpFromNameField.setValue(safe(settings.getSmtpFromName()));

        smtpPasswordStatus.setText(notificationSettingsService.isSmtpPasswordConfigured()
                ? "SMTP password: configured in the runtime environment"
                : "SMTP password: not configured in the runtime environment");

        smsProviderField.setValue(safe(settings.getSmsProvider()));

        smsSenderNameField.setValue(safe(settings.getSmsSenderName()));

        toggleEmailFields(settings.getEmailEnabled());

        disableSmsFields();
    }

    private void toggleEmailFields(boolean enabled) {
        smtpHostField.setEnabled(enabled);
        smtpPortField.setEnabled(enabled);
        smtpUsernameField.setEnabled(enabled);
        smtpFromNameField.setEnabled(enabled);
    }

    private void disableSmsFields() {
        smsEnabledField.setEnabled(false);
        smsProviderField.setEnabled(false);
        smsSenderNameField.setEnabled(false);
    }

    private void save() {
        try {
            settings.setCompanyName(companyNameField.getValue());

            settings.setEmailEnabled(emailEnabledField.getValue());

            settings.setSmsEnabled(false);

            settings.setSmtpHost(smtpHostField.getValue());

            settings.setSmtpPort(smtpPortField.getValue());

            settings.setSmtpUsername(smtpUsernameField.getValue());

            settings.setSmtpFromName(smtpFromNameField.getValue());

            settings.setSmsProvider(smsProviderField.getValue());

            settings.setSmsSenderName(smsSenderNameField.getValue());

            notificationSettingsService.save(settings);

            Notification.show("Settings saved successfully.", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        } catch (Exception ex) {
            UserSafeNotifier.showError(ex);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
