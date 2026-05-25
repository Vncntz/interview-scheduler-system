package com.company.iss.schedule.dialog;

import com.company.iss.auth.entity.User;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.service.BranchService;
import com.company.iss.recruiter.service.RecruiterService;
import com.company.iss.schedule.dto.BulkScheduleResult;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.service.ScheduleService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.timepicker.TimePicker;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public class BulkScheduleDialog extends Dialog {

    private final SaveListener saveListener;
    private final ScheduleService scheduleService;

    private ComboBox<Branch> branchField;
    private ComboBox<User> recruiterField;
    private ComboBox<InterviewMode> interviewModeField;

    private DatePicker startDateField;
    private DatePicker endDateField;

    private CheckboxGroup<DayOfWeek> daysField;

    private TimePicker startTimeField;
    private TimePicker endTimeField;

    private IntegerField intervalField;
    private IntegerField slotCapacityField;

    private TextArea notesField;

    public interface SaveListener {
        void onSave();
    }

    public BulkScheduleDialog(BranchService branchService, RecruiterService recruiterService, ScheduleService scheduleService, SaveListener saveListener) {
        this.scheduleService = scheduleService;
        this.saveListener = saveListener;

        setHeaderTitle("Bulk Schedule");
        setWidth("650px");
        setCloseOnOutsideClick(false);
        setCloseOnEsc(false);

        initFields(branchService, recruiterService);

        add(buildForm());

        getFooter().add(buildFooter());
    }

    private void initFields(BranchService branchService, RecruiterService recruiterService) {

        branchField = new ComboBox<>("Branch");
        branchField.setItems(branchService.findAll());
        branchField.setItemLabelGenerator(Branch::getBranchName);
        branchField.setWidthFull();

        recruiterField = new ComboBox<>("Recruiter");
        recruiterField.setItemLabelGenerator(User::getFullName);
        recruiterField.setEnabled(false);
        recruiterField.setWidthFull();

        interviewModeField = new ComboBox<>("Interview Mode");
        interviewModeField.setItems(InterviewMode.values());
        interviewModeField.setWidthFull();

        branchField.addValueChangeListener(e -> {
            Branch branch = e.getValue();

            if (branch == null) {
                recruiterField.clear();
                recruiterField.setEnabled(false);
                return;
            }

            recruiterField.setItems(recruiterService.findByBranch(branch));

            recruiterField.setEnabled(true);
        });

        startDateField = new DatePicker("Start Date");
        startDateField.setMin(LocalDate.now());
        startDateField.setWidthFull();

        endDateField = new DatePicker("End Date");
        endDateField.setWidthFull();

        startDateField.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                endDateField.setMin(e.getValue());
            }
        });

        daysField = new CheckboxGroup<>();
        daysField.setLabel("Working Days");
        daysField.setItems(DayOfWeek.values());
        daysField.setValue(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        daysField.setWidthFull();

        startTimeField = new TimePicker("Start Time");
        startTimeField.setValue(LocalTime.of(9, 0));
        startTimeField.setWidthFull();

        endTimeField = new TimePicker("End Time");
        endTimeField.setValue(LocalTime.of(17, 0));
        endTimeField.setWidthFull();

        intervalField = new IntegerField("Interval (mins)");
        intervalField.setValue(30);
        intervalField.setMin(15);
        intervalField.setWidthFull();

        slotCapacityField = new IntegerField("Slot Capacity");
        slotCapacityField.setValue(1);
        slotCapacityField.setMin(1);
        slotCapacityField.setWidthFull();

        notesField = new TextArea("Notes");
        notesField.setWidthFull();
    }

    private FormLayout buildForm() {
        FormLayout form = new FormLayout();

        form.add(branchField, recruiterField, interviewModeField, startDateField, endDateField, daysField, startTimeField, endTimeField, intervalField, slotCapacityField, notesField);

        form.setColspan(daysField, 2);
        form.setColspan(notesField, 2);

        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("600px", 2));

        return form;
    }

    private HorizontalLayout buildFooter() {
        Button cancel = new Button("Cancel", e -> close());

        Button generate = new Button("Generate", e -> generateSchedules());
        generate.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);

        return new HorizontalLayout(cancel, generate);
    }

    private void generateSchedules() {
        if (!isValid()) {
            showError("Please complete all required fields.");
            return;
        }

        try {
            BulkScheduleResult result = scheduleService.generateBulkSchedules(branchField.getValue(), recruiterField.getValue(), startDateField.getValue(), endDateField.getValue(), daysField.getValue(), startTimeField.getValue(), endTimeField.getValue(), intervalField.getValue(), slotCapacityField.getValue(), interviewModeField.getValue(), notesField.getValue());

            Notification.show("Created: " + result.getCreatedCount() + " | Skipped: " + result.getSkippedCount(), 4000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            saveListener.onSave();

            close();

        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private boolean isValid() {
        return branchField.getValue() != null && recruiterField.getValue() != null && interviewModeField.getValue() != null && startDateField.getValue() != null && endDateField.getValue() != null && !daysField.getValue().isEmpty() && startTimeField.getValue() != null && endTimeField.getValue() != null && endTimeField.getValue().isAfter(startTimeField.getValue()) && intervalField.getValue() != null && slotCapacityField.getValue() != null;
    }

    private void showError(String message) {
        Notification.show(message, 4000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}