package com.company.iss.position.dialog;

import com.company.iss.client.entity.Client;
import com.company.iss.client.service.ClientService;
import com.company.iss.position.entity.EmploymentType;
import com.company.iss.position.entity.PositionOpening;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;

public class PositionOpeningFormDialog extends Dialog {

    private final Binder<PositionOpening> binder = new Binder<>(PositionOpening.class);

    private final PositionOpening positionOpening;
    private final SaveListener saveListener;

    private TextField titleField;
    private ComboBox<Client> clientField;
    private TextField workLocationField;
    private ComboBox<EmploymentType> employmentTypeField;
    private IntegerField requiredHeadcountField;
    private TextArea descriptionField;

    public interface SaveListener {
        void onSave(PositionOpening positionOpening);
    }

    public PositionOpeningFormDialog(PositionOpening positionOpening, ClientService clientService, SaveListener saveListener) {
        this.positionOpening = positionOpening;
        this.saveListener = saveListener;

        setHeaderTitle(positionOpening.getId() == null ? "Create Position Opening" : "Edit Position Opening");

        setWidth("750px");
        setCloseOnOutsideClick(false);
        setCloseOnEsc(false);

        initFields(clientService);
        bindFields();

        add(buildForm());

        getFooter().add(buildFooter());
    }

    private void initFields(ClientService clientService) {

        titleField = new TextField("Position Title");
        titleField.setWidthFull();

        clientField = new ComboBox<>("Client");
        clientField.setItems(clientService.findActive());
        clientField.setItemLabelGenerator(Client::getCompanyName);
        clientField.setWidthFull();

        workLocationField = new TextField("Work Location");
        workLocationField.setPlaceholder("e.g. Tanza, Cavite");
        workLocationField.setWidthFull();

        employmentTypeField = new ComboBox<>("Employment Type");
        employmentTypeField.setItems(EmploymentType.values());
        employmentTypeField.setWidthFull();

        requiredHeadcountField = new IntegerField("Required Headcount");
        requiredHeadcountField.setMin(1);
        requiredHeadcountField.setValue(1);
        requiredHeadcountField.setWidthFull();

        descriptionField = new TextArea("Description");
        descriptionField.setWidthFull();
    }

    private void bindFields() {

        binder.forField(titleField).asRequired("Position title is required").bind(PositionOpening::getTitle, PositionOpening::setTitle);

        binder.forField(clientField).asRequired("Client is required").bind(PositionOpening::getClient, PositionOpening::setClient);

        binder.forField(workLocationField).asRequired("Work location is required").bind(PositionOpening::getWorkLocation, PositionOpening::setWorkLocation);

        binder.forField(employmentTypeField).asRequired("Employment type is required").bind(PositionOpening::getEmploymentType, PositionOpening::setEmploymentType);

        binder.forField(requiredHeadcountField).asRequired("Required headcount is required").bind(PositionOpening::getRequiredHeadcount, PositionOpening::setRequiredHeadcount);

        binder.forField(descriptionField).bind(PositionOpening::getDescription, PositionOpening::setDescription);

        binder.readBean(positionOpening);
    }

    private FormLayout buildForm() {
        FormLayout form = new FormLayout();

        form.add(titleField, clientField, workLocationField, employmentTypeField, requiredHeadcountField, descriptionField);

        form.setColspan(descriptionField, 2);

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
            binder.writeBean(positionOpening);

            saveListener.onSave(positionOpening);

            close();

        } catch (ValidationException e) {

            binder.validate();

            Notification.show("Please fix validation errors.", 4000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);

        } catch (Exception e) {

            Notification.show(e.getMessage(), 4000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}