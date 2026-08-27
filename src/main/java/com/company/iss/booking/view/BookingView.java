package com.company.iss.booking.view;

import com.company.iss.booking.dialog.BookingRescheduleDialog;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.exception.BookingCancellationException;
import com.company.iss.booking.exception.BookingRescheduleException;
import com.company.iss.booking.service.BookingService;
import com.company.iss.evaluation.dialog.InterviewEvaluationDialog;
import com.company.iss.evaluation.service.InterviewEvaluationService;
import com.company.iss.shared.view.MainLayout;
import com.company.iss.shared.view.UserSafeNotifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import static com.vaadin.flow.component.grid.ColumnTextAlign.CENTER;

@Route(value = "bookings", layout = MainLayout.class)
@PageTitle("Booking Management")
@RolesAllowed({"ADMIN", "RECRUITER"})
public class BookingView extends VerticalLayout {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private InterviewEvaluationService interviewEvaluationService;

    private Grid<Booking> bookingGrid;
    private TextField searchField;
    private Button searchButton;

    public BookingView() {
        setSizeFull();

        HorizontalLayout filterLayout = new HorizontalLayout();

        searchField = new TextField();
        searchField.setPlaceholder("Search Booking");

        searchButton = new Button("Search");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> init());

        filterLayout.add(searchField, searchButton);
        filterLayout.setWidthFull();
        filterLayout.setJustifyContentMode(JustifyContentMode.END);
        filterLayout.setAlignItems(Alignment.CENTER);

        bookingGrid = new Grid<>();
        bookingGrid.setHeightFull();
        bookingGrid.setWidth("100%");
        bookingGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS, GridVariant.LUMO_COMPACT);
        bookingGrid.addColumn(Booking::getBookingReference).setHeader("Reference").setWidth("180px").setTextAlign(CENTER).setResizable(true);
        bookingGrid.addColumn(o -> o.getApplicant().getFullName()).setHeader("Applicant").setWidth("220px").setResizable(true);
        bookingGrid.addColumn(o -> o.getApplicant().getPositionOpening().getTitle()).setHeader("Position").setWidth("180px").setResizable(true);
        bookingGrid.addColumn(o -> o.getApplicant().getPositionOpening().getClient().getCompanyName()).setHeader("Client").setWidth("220px").setResizable(true);
        bookingGrid.addColumn(o -> o.getSchedule().getScheduleDate()).setHeader("Schedule Date").setWidth("150px").setTextAlign(CENTER).setResizable(true);
        bookingGrid.addColumn(o -> o.getSchedule().getStartTime()).setHeader("Start Time").setWidth("120px").setTextAlign(CENTER).setResizable(true);
        bookingGrid.addColumn(o -> o.getSchedule().getRecruiter().getFullName()).setHeader("Recruiter").setWidth("220px").setResizable(true);
        bookingGrid.addColumn(o -> o.getStatus().name()).setHeader("Status").setWidth("140px").setTextAlign(CENTER).setResizable(true);
        bookingGrid.addComponentColumn(booking -> {

            HorizontalLayout actions = new HorizontalLayout();
            actions.setWidthFull();
            actions.setJustifyContentMode(JustifyContentMode.CENTER);
            actions.setAlignItems(Alignment.CENTER);

            if (booking.getStatus() == BookingStatus.BOOKED) {

                Button confirmButton = new Button("Confirm");
                confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

                confirmButton.addClickListener(e -> executeBookingAction(
                        () -> bookingService.confirm(booking.getId()),
                        "Interview confirmed successfully."
                ));

                Button cancelButton = new Button("Cancel");
                cancelButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

                cancelButton.addClickListener(e -> executeBookingAction(
                        () -> bookingService.cancel(booking.getId()),
                        "Interview cancelled successfully."
                ));

                Button rescheduleButton = buildRescheduleButton(booking);

                actions.add(confirmButton, rescheduleButton, cancelButton);

            } else if (booking.getStatus() == BookingStatus.CONFIRMED) {

                Button attendedButton = new Button("Attended");
                attendedButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);

                attendedButton.addClickListener(e -> executeBookingAction(
                        () -> bookingService.markAttended(booking.getId()),
                        "Attendance recorded successfully."
                ));

                Button noShowButton = new Button("No Show");
                noShowButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);

                noShowButton.addClickListener(e -> executeBookingAction(
                        () -> bookingService.markNoShow(booking.getId()),
                        "No-show recorded successfully."
                ));

                Button cancelButton = new Button("Cancel");
                cancelButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

                cancelButton.addClickListener(e -> executeBookingAction(
                        () -> bookingService.cancel(booking.getId()),
                        "Interview cancelled successfully."
                ));

                Button rescheduleButton = buildRescheduleButton(booking);

                actions.add(attendedButton, noShowButton, rescheduleButton, cancelButton);

            } else if (booking.getStatus() == BookingStatus.ATTENDED) {

                Button evaluateButton = new Button("Evaluate");
                evaluateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

                evaluateButton.addClickListener(e -> {

                    InterviewEvaluationDialog dialog = new InterviewEvaluationDialog(booking, interviewEvaluationService, this::init);

                    dialog.open();
                });

                actions.add(evaluateButton);
            } else {

                Button completedButton = new Button(booking.getStatus().name());
                completedButton.setEnabled(false);

                actions.add(completedButton);
            }

            return actions;

        }).setHeader("Actions").setWidth("480px").setResizable(true);

        add(filterLayout, bookingGrid);
    }

    @PostConstruct
    private void init() {
        bookingGrid.setItems(bookingService.search(searchField.getValue()));
    }

    private Button buildRescheduleButton(Booking booking) {
        Button rescheduleButton = new Button("Reschedule");
        rescheduleButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        rescheduleButton.setVisible(bookingService.canReschedule(booking));
        rescheduleButton.addClickListener(event -> openRescheduleDialog(booking));
        return rescheduleButton;
    }

    private void openRescheduleDialog(Booking booking) {
        try {
            var eligibleSchedules = bookingService.findEligibleRescheduleDestinations(booking.getId());
            if (eligibleSchedules.isEmpty()) {
                showNotification("No eligible future schedules are currently available.", true);
                return;
            }

            BookingRescheduleDialog dialog = new BookingRescheduleDialog(
                    booking,
                    eligibleSchedules,
                    command -> {
                        try {
                            bookingService.reschedule(command);
                            showNotification("Interview rescheduled successfully.", false);
                            init();
                            return true;
                        } catch (BookingRescheduleException | AccessDeniedException exception) {
                            UserSafeNotifier.showError(exception);
                            return false;
                        } catch (RuntimeException exception) {
                            UserSafeNotifier.showError(exception);
                            return false;
                        }
                    }
            );
            dialog.open();
        } catch (BookingRescheduleException | AccessDeniedException exception) {
            UserSafeNotifier.showError(exception);
        }
    }

    private void executeBookingAction(Runnable action, String successMessage) {
        try {
            action.run();
            showNotification(successMessage, false);
            init();
        } catch (BookingCancellationException | AccessDeniedException
                 | IllegalArgumentException | IllegalStateException exception) {
            UserSafeNotifier.showError(exception);
        } catch (RuntimeException exception) {
            UserSafeNotifier.showError(exception);
        }
    }

    private void showNotification(String message, boolean error) {
        Notification notification = Notification.show(message, 3500, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(
                error ? NotificationVariant.LUMO_ERROR : NotificationVariant.LUMO_SUCCESS
        );
    }
}
