package com.company.iss.notification.dialog;

import com.company.iss.notification.entity.NotificationTemplate;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

public class NotificationTemplateFormDialog extends Dialog {

    private final NotificationTemplate template;
    private final SaveListener saveListener;

    private TextField subjectField;
    private TextArea bodyField;
    private Checkbox activeField;

    public interface SaveListener {
        void onSave(NotificationTemplate template);
    }

    public NotificationTemplateFormDialog(NotificationTemplate template, SaveListener saveListener) {
        this.template = template;
        this.saveListener = saveListener;

        setHeaderTitle("Edit Notification Template");
        setWidth("900px");
        setCloseOnOutsideClick(false);
        setCloseOnEsc(false);

        initFields();

        add(buildForm());

        getFooter().add(buildFooter());
    }

    private void initFields() {
        subjectField = new TextField("Subject");
        subjectField.setWidthFull();
        subjectField.setValue(safe(template.getSubject()));

        bodyField = new TextArea("Body");
        bodyField.setWidthFull();
        bodyField.setHeight("350px");
        bodyField.setValue(safe(template.getBody()));

        activeField = new Checkbox("Active");
        activeField.setValue(template.getActive());
    }

    private FormLayout buildForm() {
        FormLayout form = new FormLayout();

        TextArea placeholders = new TextArea("Available Placeholders");

        placeholders.setValue("""
                {{applicantName}}
                {{bookingReference}}
                {{position}}
                {{client}}
                {{workLocation}}
                {{date}}
                {{time}}
                {{recruiter}}
                {{interviewMode}}
                """);

        placeholders.setReadOnly(true);
        placeholders.setHeight("250px");
        placeholders.setWidthFull();

        form.add(subjectField, activeField, bodyField, placeholders);

        form.setColspan(bodyField, 2);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("900px", 2));

        return form;
    }

    private Button[] buildFooter() {
        Button cancelButton = new Button("Cancel", e -> close());

        Button saveButton = new Button("Save", e -> save());

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        return new Button[]{cancelButton, saveButton};
    }

    private void save() {
        template.setSubject(subjectField.getValue());

        template.setBody(bodyField.getValue());

        template.setActive(activeField.getValue());

        saveListener.onSave(template);

        close();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}