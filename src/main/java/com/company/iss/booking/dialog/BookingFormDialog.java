package com.company.iss.booking.dialog;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.booking.dto.CreateBookingCommand;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.service.ScheduleService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

import java.util.List;

public class BookingFormDialog extends Dialog {

    private final Applicant applicant;
    private final SaveListener saveListener;

    private ComboBox<Schedule> scheduleField;
    private TextField interviewStageField;
    private TextArea remarksField;

    public interface SaveListener {
        void onSave(CreateBookingCommand command);
    }

    public BookingFormDialog(
            Applicant applicant,
            InterviewStage interviewStage,
            ScheduleService scheduleService,
            SaveListener saveListener
    ) {
        this.applicant = applicant;
        this.saveListener = saveListener;

        setHeaderTitle("Book Interview");

        setWidth("750px");
        setCloseOnOutsideClick(false);
        setCloseOnEsc(false);

        initFields(interviewStage, scheduleService);

        add(buildForm());

        getFooter().add(buildFooter());
    }

    private void initFields(InterviewStage interviewStage, ScheduleService scheduleService) {

        interviewStageField = new TextField("Interview Stage");
        interviewStageField.setValue(interviewStage.name());
        interviewStageField.setReadOnly(true);
        interviewStageField.setWidthFull();

        scheduleField = new ComboBox<>("Available Schedule");

        List<Schedule> schedules = scheduleService.findAvailableForCurrentUser(
                applicant.getBranch() == null ? null : applicant.getBranch().getId()
        );

        scheduleField.setItems(schedules);

        scheduleField.setItemLabelGenerator(schedule -> schedule.getScheduleDate() + " | " + schedule.getStartTime() + " - " + schedule.getEndTime() + " | " + schedule.getRecruiter().getFullName() + " | " + schedule.getInterviewMode().name());

        scheduleField.setWidthFull();

        remarksField = new TextArea("Remarks");
        remarksField.setWidthFull();
    }

    private FormLayout buildForm() {
        FormLayout form = new FormLayout();

        form.add(interviewStageField, scheduleField, remarksField);

        form.setColspan(remarksField, 2);

        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("600px", 2));

        return form;
    }

    private Button[] buildFooter() {
        Button cancelButton = new Button("Cancel", e -> close());

        Button saveButton = new Button("Book", e -> save());

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        return new Button[]{cancelButton, saveButton};
    }

    private void save() {

        if (scheduleField.getValue() == null) {
            Notification.show("Please select a schedule.", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        Schedule schedule = scheduleField.getValue();
        saveListener.onSave(new CreateBookingCommand(
                applicant.getId(),
                schedule.getId(),
                InterviewStage.valueOf(interviewStageField.getValue()),
                remarksField.getValue()
        ));

        close();
    }
}
