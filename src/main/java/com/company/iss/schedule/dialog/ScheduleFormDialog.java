package com.company.iss.schedule.dialog;

import com.company.iss.auth.entity.User;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.service.BranchService;
import com.company.iss.recruiter.service.RecruiterService;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;

public class ScheduleFormDialog extends Dialog {

    private final Binder<Schedule> binder = new Binder<>(Schedule.class);
    private final Schedule schedule;
    private final SaveListener saveListener;

    private ComboBox<Branch> branchField;
    private ComboBox<User> recruiterField;
    private ComboBox<InterviewMode> interviewModeField;

    private DatePicker scheduleDateField;
    private TimePicker startTimeField;
    private TimePicker endTimeField;
    private IntegerField slotCapacityField;
    private TextArea notesField;

    public interface SaveListener {
        void onSave(Schedule schedule);
    }

    public ScheduleFormDialog(Schedule schedule, BranchService branchService, RecruiterService recruiterService, SaveListener saveListener) {
        this.schedule = schedule;
        this.saveListener = saveListener;

        setHeaderTitle(schedule.getId() == null ? "Create Schedule" : "Edit Schedule");

        setWidth("600px");
        setCloseOnOutsideClick(false);
        setCloseOnEsc(false);

        initFields(branchService, recruiterService);
        bindFields(recruiterService);

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

        scheduleDateField = new DatePicker("Schedule Date");
        scheduleDateField.setWidthFull();

        startTimeField = new TimePicker("Start Time");
        startTimeField.setWidthFull();

        endTimeField = new TimePicker("End Time");
        endTimeField.setWidthFull();

        slotCapacityField = new IntegerField("Slot Capacity");
        slotCapacityField.setMin(1);
        slotCapacityField.setValue(1);
        slotCapacityField.setWidthFull();

        notesField = new TextArea("Notes");
        notesField.setWidthFull();
    }

    private void bindFields(RecruiterService recruiterService) {

        binder.forField(branchField).asRequired("Branch is required").bind(Schedule::getBranch, Schedule::setBranch);

        binder.forField(recruiterField).asRequired("Recruiter is required").bind(Schedule::getRecruiter, Schedule::setRecruiter);

        binder.forField(interviewModeField).asRequired("Interview mode is required").bind(Schedule::getInterviewMode, Schedule::setInterviewMode);

        binder.forField(scheduleDateField).asRequired("Schedule date is required").bind(Schedule::getScheduleDate, Schedule::setScheduleDate);

        binder.forField(startTimeField).asRequired("Start time is required").bind(Schedule::getStartTime, Schedule::setStartTime);

        binder.forField(endTimeField).asRequired("End time is required").withValidator(end -> startTimeField.getValue() == null || end == null || end.isAfter(startTimeField.getValue()), "End time must be after start time").bind(Schedule::getEndTime, Schedule::setEndTime);

        binder.forField(slotCapacityField).asRequired("Capacity is required").bind(Schedule::getSlotCapacity, Schedule::setSlotCapacity);

        binder.forField(notesField).bind(Schedule::getNotes, Schedule::setNotes);

        if (schedule.getBranch() != null) {
            recruiterField.setItems(recruiterService.findByBranch(schedule.getBranch()));
            recruiterField.setEnabled(true);
        }

        binder.readBean(schedule);
    }

    private FormLayout buildForm() {
        FormLayout form = new FormLayout();

        form.add(branchField, recruiterField, interviewModeField, scheduleDateField, startTimeField, endTimeField, slotCapacityField, notesField);

        form.setColspan(notesField, 2);

        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("600px", 2));

        return form;
    }

    private HorizontalLayout buildFooter() {
        Button cancel = new Button("Cancel", e -> close());

        Button save = new Button("Save", e -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        return new HorizontalLayout(cancel, save);
    }

    private void save() {
        try {
            binder.writeBean(schedule);

            Notification.show(schedule.getId() == null ? "Schedule created" : "Schedule updated", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            saveListener.onSave(schedule);

            close();

        } catch (ValidationException e) {
            binder.validate();
            showError("Please fix validation errors.");

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void showError(String message) {
        Notification.show(message, 4000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}