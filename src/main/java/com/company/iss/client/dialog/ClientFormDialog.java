package com.company.iss.client.dialog;

import com.company.iss.client.entity.Client;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;

public class ClientFormDialog extends Dialog {

    private final Binder<Client> binder = new Binder<>(Client.class);
    private final Client client;
    private final SaveListener saveListener;

    private TextField companyNameField;
    private TextField addressField;
    private TextField contactPersonField;
    private TextField contactNumberField;
    private EmailField emailField;
    private TextArea notesField;

    public interface SaveListener {
        void onSave(Client client);
    }

    public ClientFormDialog(Client client, SaveListener saveListener) {
        this.client = client;
        this.saveListener = saveListener;

        setHeaderTitle(client.getId() == null ? "Create Client" : "Edit Client");

        setWidth("700px");
        setCloseOnOutsideClick(false);
        setCloseOnEsc(false);

        initFields();
        bindFields();

        add(buildForm());

        getFooter().add(buildFooter());
    }

    private void initFields() {

        companyNameField = new TextField("Company Name");
        companyNameField.setWidthFull();

        addressField = new TextField("Address");
        addressField.setWidthFull();

        contactPersonField = new TextField("Contact Person");
        contactPersonField.setWidthFull();

        contactNumberField = new TextField("Contact Number");
        contactNumberField.setWidthFull();

        emailField = new EmailField("Email");
        emailField.setWidthFull();

        notesField = new TextArea("Notes");
        notesField.setWidthFull();
    }

    private void bindFields() {

        binder.forField(companyNameField).asRequired("Company name is required").bind(Client::getCompanyName, Client::setCompanyName);

        binder.forField(addressField).asRequired("Address is required").bind(Client::getAddress, Client::setAddress);

        binder.forField(contactPersonField).bind(Client::getContactPerson, Client::setContactPerson);

        binder.forField(contactNumberField).bind(Client::getContactNumber, Client::setContactNumber);

        binder.forField(emailField).bind(Client::getEmail, Client::setEmail);

        binder.forField(notesField).bind(Client::getNotes, Client::setNotes);

        binder.readBean(client);
    }

    private FormLayout buildForm() {
        FormLayout form = new FormLayout();

        form.add(companyNameField, addressField, contactPersonField, contactNumberField, emailField, notesField);

        form.setColspan(notesField, 2);

        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("600px", 2));

        return form;
    }

    private Button[] buildFooter() {
        Button cancelButton = new Button("Cancel", e -> close());

        Button saveButton = new Button("Save", e -> save());

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        return new Button[]{cancelButton, saveButton};
    }

    private void save() {
        try {
            binder.writeBean(client);

            saveListener.onSave(client);

            close();

        } catch (ValidationException e) {

            binder.validate();

            Notification.show("Please fix validation errors.", 4000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);

        } catch (Exception e) {

            Notification.show(e.getMessage(), 4000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}