package com.company.iss.notification.view;

import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.service.NotificationSettingsService;
import com.company.iss.shared.view.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "notification-settings", layout = MainLayout.class)
@PageTitle("Notification Settings")
@RolesAllowed("ADMIN")
public class NotificationSettingsView extends VerticalLayout {

    @Autowired
    private NotificationSettingsService notificationSettingsService;

    private NotificationSettings settings;

    private TextField companyNameField;
    private Checkbox emailEnabledField;
    private Checkbox smsEnabledField;

    private TextField smtpHostField;
    private IntegerField smtpPortField;
    private TextField smtpUsernameField;
    private PasswordField smtpPasswordField;
    private TextField smtpFromNameField;

    private TextField smsProviderField;
    private PasswordField smsApiKeyField;
    private TextField smsSenderNameField;

    public NotificationSettingsView() {
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

        form.add(emailEnabledField, smtpHostField, smtpPortField, smtpUsernameField, smtpPasswordField, smtpFromNameField);

        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("700px", 2));

        return form;
    }

    private FormLayout createSmsForm() {
        FormLayout form = new FormLayout();

        form.add(smsEnabledField, smsProviderField, smsApiKeyField, smsSenderNameField);

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

        smtpHostField = new TextField("SMTP Host");
        smtpHostField.setWidthFull();

        smtpPortField = new IntegerField("SMTP Port");
        smtpPortField.setWidthFull();

        smtpUsernameField = new TextField("SMTP Username");
        smtpUsernameField.setWidthFull();

        smtpPasswordField = new PasswordField("SMTP Password");
        smtpPasswordField.setWidthFull();

        smtpFromNameField = new TextField("SMTP Sender Name");
        smtpFromNameField.setWidthFull();

        smsProviderField = new TextField("SMS Provider");
        smsProviderField.setWidthFull();

        smsApiKeyField = new PasswordField("SMS API Key");
        smsApiKeyField.setWidthFull();

        smsSenderNameField = new TextField("SMS Sender Name");
        smsSenderNameField.setWidthFull();

        emailEnabledField.addValueChangeListener(e -> toggleEmailFields(e.getValue()));

        smsEnabledField.addValueChangeListener(e -> toggleSmsFields(e.getValue()));
    }

    @PostConstruct
    private void init() {
        settings = notificationSettingsService.getSettings();

        companyNameField.setValue(safe(settings.getCompanyName()));

        emailEnabledField.setValue(settings.getEmailEnabled());

        smsEnabledField.setValue(settings.getSmsEnabled());

        smtpHostField.setValue(safe(settings.getSmtpHost()));

        smtpPortField.setValue(settings.getSmtpPort() == null ? 587 : settings.getSmtpPort());

        smtpUsernameField.setValue(safe(settings.getSmtpUsername()));

        smtpPasswordField.setValue(safe(settings.getSmtpPassword()));

        smtpFromNameField.setValue(safe(settings.getSmtpFromName()));

        smsProviderField.setValue(safe(settings.getSmsProvider()));

        smsApiKeyField.setValue(safe(settings.getSmsApiKey()));

        smsSenderNameField.setValue(safe(settings.getSmsSenderName()));

        toggleEmailFields(settings.getEmailEnabled());

        toggleSmsFields(settings.getSmsEnabled());
    }

    private void toggleEmailFields(boolean enabled) {
        smtpHostField.setEnabled(enabled);
        smtpPortField.setEnabled(enabled);
        smtpUsernameField.setEnabled(enabled);
        smtpPasswordField.setEnabled(enabled);
        smtpFromNameField.setEnabled(enabled);
    }

    private void toggleSmsFields(boolean enabled) {
        smsProviderField.setEnabled(enabled);
        smsApiKeyField.setEnabled(enabled);
        smsSenderNameField.setEnabled(enabled);
    }

    private void save() {
        try {
            settings.setCompanyName(companyNameField.getValue());

            settings.setEmailEnabled(emailEnabledField.getValue());

            settings.setSmsEnabled(smsEnabledField.getValue());

            settings.setSmtpHost(smtpHostField.getValue());

            settings.setSmtpPort(smtpPortField.getValue());

            settings.setSmtpUsername(smtpUsernameField.getValue());

            settings.setSmtpPassword(smtpPasswordField.getValue());

            settings.setSmtpFromName(smtpFromNameField.getValue());

            settings.setSmsProvider(smsProviderField.getValue());

            settings.setSmsApiKey(smsApiKeyField.getValue());

            settings.setSmsSenderName(smsSenderNameField.getValue());

            notificationSettingsService.save(settings);

            Notification.show("Settings saved successfully.", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        } catch (Exception ex) {
            Notification.show(ex.getMessage(), 4000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}