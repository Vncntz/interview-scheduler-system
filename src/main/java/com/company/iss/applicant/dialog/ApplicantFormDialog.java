package com.company.iss.applicant.dialog;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.service.PositionOpeningService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;

public class ApplicantFormDialog extends Dialog {

    private final Binder<Applicant> binder = new Binder<>(Applicant.class);
    private final Applicant applicant;
    private final SaveListener saveListener;

    private TextField firstNameField;
    private TextField middleNameField;
    private TextField lastNameField;
    private EmailField emailField;
    private TextField mobileNumberField;
    private ComboBox<PositionOpening> positionOpeningField;
    private TextField sourceField;
    private TextArea remarksField;

    public interface SaveListener {
        void onSave(Applicant applicant);
    }

    public ApplicantFormDialog(Applicant applicant, PositionOpeningService positionOpeningService, SaveListener saveListener) {
        this.applicant = applicant;
        this.saveListener = saveListener;

        setHeaderTitle(applicant.getId() == null ? "Create Applicant" : "Edit Applicant");

        setWidth("700px");
        setCloseOnOutsideClick(false);
        setCloseOnEsc(false);

        initFields(positionOpeningService);
        bindFields();

        add(buildForm());

        getFooter().add(buildFooter());
    }

    private void initFields(PositionOpeningService positionOpeningService) {

        firstNameField = new TextField("First Name");
        firstNameField.setWidthFull();

        middleNameField = new TextField("Middle Name");
        middleNameField.setWidthFull();

        lastNameField = new TextField("Last Name");
        lastNameField.setWidthFull();

        emailField = new EmailField("Email");
        emailField.setWidthFull();

        mobileNumberField = new TextField("Mobile Number");
        mobileNumberField.setWidthFull();

        positionOpeningField = new ComboBox<>("Position Opening");
        positionOpeningField.setItems(positionOpeningService.findActive());
        positionOpeningField.setItemLabelGenerator(o -> o.getTitle() + " | " + o.getClient().getCompanyName() + " | " + o.getWorkLocation());
        positionOpeningField.setWidthFull();

        sourceField = new TextField("Source");
        sourceField.setWidthFull();

        remarksField = new TextArea("Remarks");
        remarksField.setWidthFull();
    }

    private void bindFields() {

        binder.forField(firstNameField).asRequired("First name is required").bind(Applicant::getFirstName, Applicant::setFirstName);

        binder.forField(middleNameField).bind(Applicant::getMiddleName, Applicant::setMiddleName);

        binder.forField(lastNameField).asRequired("Last name is required").bind(Applicant::getLastName, Applicant::setLastName);

        binder.forField(emailField).asRequired("Email is required").bind(Applicant::getEmail, Applicant::setEmail);

        binder.forField(mobileNumberField).asRequired("Mobile number is required").bind(Applicant::getMobileNumber, Applicant::setMobileNumber);

        binder.forField(positionOpeningField).asRequired("Position opening is required").bind(Applicant::getPositionOpening, Applicant::setPositionOpening);

        binder.forField(sourceField).bind(Applicant::getSource, Applicant::setSource);

        binder.forField(remarksField).bind(Applicant::getRemarks, Applicant::setRemarks);

        binder.readBean(applicant);
    }

    private FormLayout buildForm() {
        FormLayout form = new FormLayout();

        form.add(firstNameField, middleNameField, lastNameField, emailField, mobileNumberField, positionOpeningField, sourceField, remarksField);

        form.setColspan(remarksField, 2);

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
            binder.writeBean(applicant);

            saveListener.onSave(applicant);

            close();

        } catch (ValidationException e) {

            binder.validate();

            Notification.show("Please fix validation errors.", 4000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);

        } catch (Exception e) {

            Notification.show(e.getMessage(), 4000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}