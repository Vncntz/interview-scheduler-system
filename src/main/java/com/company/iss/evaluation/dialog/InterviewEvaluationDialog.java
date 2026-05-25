package com.company.iss.evaluation.dialog;

import com.company.iss.auth.entity.User;
import com.company.iss.booking.entity.Booking;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.evaluation.service.InterviewEvaluationService;
import com.company.iss.auth.service.SecurityService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import org.springframework.beans.factory.annotation.Autowired;

public class InterviewEvaluationDialog extends Dialog {

    private final Booking booking;
    private final InterviewEvaluationService interviewEvaluationService;
    private final SecurityService securityService;
    private final Runnable onSave;

    private IntegerField communicationScoreField;
    private IntegerField technicalScoreField;
    private IntegerField attitudeScoreField;
    private ComboBox<InterviewResult> resultField;
    private TextArea remarksField;

    public InterviewEvaluationDialog(Booking booking, InterviewEvaluationService interviewEvaluationService, SecurityService securityService, Runnable onSave) {
        this.booking = booking;
        this.interviewEvaluationService = interviewEvaluationService;
        this.securityService = securityService;
        this.onSave = onSave;

        setHeaderTitle("Interview Evaluation");
        setWidth("700px");
        setCloseOnOutsideClick(false);
        setCloseOnEsc(false);

        initFields();

        add(buildForm());

        getFooter().add(buildFooter());
    }

    private void initFields() {
        communicationScoreField = new IntegerField("Communication Score");
        communicationScoreField.setMin(1);
        communicationScoreField.setMax(10);
        communicationScoreField.setWidthFull();

        technicalScoreField = new IntegerField("Technical Score");
        technicalScoreField.setMin(1);
        technicalScoreField.setMax(10);
        technicalScoreField.setWidthFull();

        attitudeScoreField = new IntegerField("Attitude Score");
        attitudeScoreField.setMin(1);
        attitudeScoreField.setMax(10);
        attitudeScoreField.setWidthFull();

        resultField = new ComboBox<>("Result");
        resultField.setItems(InterviewResult.values());
        resultField.setWidthFull();

        remarksField = new TextArea("Remarks");
        remarksField.setWidthFull();
    }

    private FormLayout buildForm() {
        FormLayout form = new FormLayout();

        form.add(communicationScoreField, technicalScoreField, attitudeScoreField, resultField, remarksField);

        form.setColspan(remarksField, 2);

        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("600px", 2));

        return form;
    }

    private Button[] buildFooter() {
        Button cancelButton = new Button("Cancel", e -> close());

        Button saveButton = new Button("Save Evaluation", e -> save());

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        return new Button[]{cancelButton, saveButton};
    }

    private void save() {
        try {
            User currentUser = securityService.getCurrentUser();

            InterviewEvaluation evaluation = new InterviewEvaluation();

            evaluation.setBooking(booking);
            evaluation.setApplicant(booking.getApplicant());
            evaluation.setEvaluator(currentUser);
            evaluation.setCommunicationScore(communicationScoreField.getValue());
            evaluation.setTechnicalScore(technicalScoreField.getValue());
            evaluation.setAttitudeScore(attitudeScoreField.getValue());
            evaluation.setResult(resultField.getValue());
            evaluation.setRemarks(remarksField.getValue());

            interviewEvaluationService.save(evaluation);

            Notification.show("Evaluation saved successfully.", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            onSave.run();

            close();

        } catch (Exception ex) {
            Notification.show(ex.getMessage(), 4000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}