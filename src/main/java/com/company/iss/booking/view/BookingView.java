package com.company.iss.booking.view;

import com.company.iss.booking.dialog.BookingRescheduleDialog;
import com.company.iss.booking.dto.BookingGridFilter;
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
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.access.AccessDeniedException;

import static com.vaadin.flow.component.grid.ColumnTextAlign.CENTER;

@Route(value = "bookings", layout = MainLayout.class)
@PageTitle("Booking Management")
@RolesAllowed({"ADMIN", "RECRUITER"})
public class BookingView extends VerticalLayout {

    private static final int GRID_PAGE_SIZE = 50;

    private final BookingService bookingService;
    private final InterviewEvaluationService interviewEvaluationService;

    private Grid<Booking> bookingGrid;
    private CallbackDataProvider<Booking, Void> dataProvider;
    private TextField searchField;
    private ComboBox<BookingStatus> statusFilter;
    private DatePicker scheduleDateFilter;
    private Button searchButton;

    public BookingView(
            BookingService bookingService,
            InterviewEvaluationService interviewEvaluationService
    ) {
        this.bookingService = bookingService;
        this.interviewEvaluationService = interviewEvaluationService;

        setSizeFull();

        HorizontalLayout filterLayout = new HorizontalLayout();

        searchField = new TextField();
        searchField.setPlaceholder("Search Booking");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(event -> refreshGrid());

        statusFilter = new ComboBox<>();
        statusFilter.setPlaceholder("Booking Status");
        statusFilter.setItems(BookingStatus.values());
        statusFilter.setClearButtonVisible(true);
        statusFilter.addValueChangeListener(event -> refreshGrid());

        scheduleDateFilter = new DatePicker();
        scheduleDateFilter.setPlaceholder("Schedule Date");
        scheduleDateFilter.setClearButtonVisible(true);
        scheduleDateFilter.addValueChangeListener(event -> refreshGrid());

        searchButton = new Button("Search");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> refreshGrid());

        filterLayout.add(searchField, statusFilter, scheduleDateFilter, searchButton);
        filterLayout.setWidthFull();
        filterLayout.setJustifyContentMode(JustifyContentMode.END);
        filterLayout.setAlignItems(Alignment.CENTER);

        bookingGrid = new Grid<>();
        bookingGrid.setHeightFull();
        bookingGrid.setWidth("100%");
        bookingGrid.setPageSize(GRID_PAGE_SIZE);
        bookingGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS, GridVariant.LUMO_COMPACT);
        bookingGrid.addColumn(Booking::getBookingReference).setHeader("Reference").setWidth("180px").setTextAlign(CENTER).setResizable(true);
        bookingGrid.addColumn(o -> o.getApplicant() == null ? "" : o.getApplicant().getFullName()).setHeader("Applicant").setWidth("220px").setResizable(true);
        bookingGrid.addColumn(this::positionTitle).setHeader("Position").setWidth("180px").setResizable(true);
        bookingGrid.addColumn(this::clientName).setHeader("Client").setWidth("220px").setResizable(true);
        bookingGrid.addColumn(o -> o.getSchedule() == null ? null : o.getSchedule().getScheduleDate()).setHeader("Schedule Date").setWidth("150px").setTextAlign(CENTER).setResizable(true);
        bookingGrid.addColumn(o -> o.getSchedule() == null ? null : o.getSchedule().getStartTime()).setHeader("Start Time").setWidth("120px").setTextAlign(CENTER).setResizable(true);
        bookingGrid.addColumn(this::recruiterName).setHeader("Recruiter").setWidth("220px").setResizable(true);
        bookingGrid.addColumn(o -> o.getInterviewStage() == null ? "" : o.getInterviewStage().name()).setHeader("Interview Stage").setWidth("160px").setTextAlign(CENTER).setResizable(true);
        bookingGrid.addColumn(o -> o.getStatus() == null ? "" : o.getStatus().name()).setHeader("Status").setWidth("140px").setTextAlign(CENTER).setResizable(true);
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

                    InterviewEvaluationDialog dialog = new InterviewEvaluationDialog(booking, interviewEvaluationService, this::refreshGrid);

                    dialog.open();
                });

                actions.add(evaluateButton);
            } else {

                Button completedButton = new Button(
                        booking.getStatus() == null ? "Unavailable" : booking.getStatus().name()
                );
                completedButton.setEnabled(false);

                actions.add(completedButton);
            }

            return actions;

        }).setHeader("Actions").setWidth("480px").setResizable(true);

        dataProvider = DataProvider.fromCallbacks(
                query -> bookingService.findGridPage(
                        currentFilter(), query.getPage(), query.getPageSize()
                ).stream(),
                query -> toIntCount(bookingService.countGrid(currentFilter()))
        );
        bookingGrid.setDataProvider(dataProvider);

        add(filterLayout, bookingGrid);
    }

    private BookingGridFilter currentFilter() {
        return new BookingGridFilter(
                searchField.getValue(), statusFilter.getValue(), scheduleDateFilter.getValue()
        );
    }

    private void refreshGrid() {
        bookingGrid.deselectAll();
        dataProvider.refreshAll();
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
                            refreshGrid();
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
            refreshGrid();
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

    private int toIntCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private String positionTitle(Booking booking) {
        return booking.getApplicant() == null || booking.getApplicant().getPositionOpening() == null
                ? ""
                : booking.getApplicant().getPositionOpening().getTitle();
    }

    private String clientName(Booking booking) {
        return booking.getApplicant() == null
                || booking.getApplicant().getPositionOpening() == null
                || booking.getApplicant().getPositionOpening().getClient() == null
                ? ""
                : booking.getApplicant().getPositionOpening().getClient().getCompanyName();
    }

    private String recruiterName(Booking booking) {
        if (booking.getSchedule() != null && booking.getSchedule().getRecruiter() != null) {
            return booking.getSchedule().getRecruiter().getFullName();
        }
        return booking.getRecruiter() == null ? "" : booking.getRecruiter().getFullName();
    }
}
