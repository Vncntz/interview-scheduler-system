package com.company.iss.booking.dialog;

import com.company.iss.booking.dto.BookingRescheduleCommand;
import com.company.iss.booking.entity.Booking;
import com.company.iss.schedule.entity.Schedule;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextArea;

import java.util.List;

public class BookingRescheduleDialog extends Dialog {

    private final Booking booking;
    private final SaveListener saveListener;
    private final ComboBox<Schedule> scheduleField = new ComboBox<>("New schedule");
    private final TextArea reasonField = new TextArea("Reason");

    @FunctionalInterface
    public interface SaveListener {
        boolean onSave(BookingRescheduleCommand command);
    }

    public BookingRescheduleDialog(
            Booking booking,
            List<Schedule> eligibleSchedules,
            SaveListener saveListener
    ) {
        this.booking = booking;
        this.saveListener = saveListener;

        setHeaderTitle("Reschedule " + booking.getBookingReference());
        setWidth("760px");
        setCloseOnOutsideClick(false);
        setCloseOnEsc(false);

        configureFields(eligibleSchedules);
        add(buildForm());
        getFooter().add(buildFooter());
    }

    private void configureFields(List<Schedule> eligibleSchedules) {
        scheduleField.setItems(eligibleSchedules);
        scheduleField.setItemLabelGenerator(this::scheduleLabel);
        scheduleField.setRequiredIndicatorVisible(true);
        scheduleField.setWidthFull();

        reasonField.setRequiredIndicatorVisible(true);
        reasonField.setMaxLength(1000);
        reasonField.setWidthFull();
    }

    private FormLayout buildForm() {
        FormLayout form = new FormLayout(scheduleField, reasonField);
        form.setColspan(reasonField, 2);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );
        return form;
    }

    private Button[] buildFooter() {
        Button cancelButton = new Button("Cancel", event -> close());
        Button saveButton = new Button("Reschedule", event -> save());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return new Button[]{cancelButton, saveButton};
    }

    private void save() {
        if (scheduleField.getValue() == null) {
            showValidationError("Please select a new schedule.");
            return;
        }
        if (reasonField.getValue() == null || reasonField.getValue().isBlank()) {
            showValidationError("Please provide a reschedule reason.");
            return;
        }

        BookingRescheduleCommand command = new BookingRescheduleCommand(
                booking.getId(),
                scheduleField.getValue().getId(),
                reasonField.getValue()
        );
        if (saveListener.onSave(command)) {
            close();
        }
    }

    private String scheduleLabel(Schedule schedule) {
        return schedule.getScheduleDate()
                + " | " + schedule.getStartTime() + " - " + schedule.getEndTime()
                + " | " + schedule.getBranch().getBranchName()
                + " | " + schedule.getRecruiter().getFullName()
                + " | " + schedule.getInterviewMode().name();
    }

    private void showValidationError(String message) {
        Notification.show(message, 3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
