package com.company.iss.dashboard.view;

import com.company.iss.booking.entity.Booking;
import com.company.iss.applicant.view.ApplicantDetailView;
import com.company.iss.booking.dialog.BookingFormDialog;
import com.company.iss.booking.dto.BookingApplicantInput;
import com.company.iss.booking.service.BookingService;
import com.company.iss.dashboard.dto.FollowUpApplicant;
import com.company.iss.dashboard.dto.RecruiterWorkbenchData;
import com.company.iss.dashboard.dto.WorkbenchInterview;
import com.company.iss.dashboard.service.RecruiterWorkbenchService;
import com.company.iss.evaluation.dialog.InterviewEvaluationDialog;
import com.company.iss.evaluation.service.InterviewEvaluationService;
import com.company.iss.schedule.service.ScheduleService;
import com.company.iss.shared.util.DateTimeUtil;
import com.company.iss.shared.view.MainLayout;
import com.company.iss.shared.view.UserSafeNotifier;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

@Route(value = "workbench", layout = MainLayout.class)
@PageTitle("Recruiter Workbench")
@RolesAllowed("RECRUITER")
public class RecruiterWorkbenchView extends VerticalLayout {

    private static final DateTimeFormatter FOLLOW_UP_DATE = DateTimeFormatter.ofPattern(
            "MMM d, uuuu", Locale.ENGLISH
    );

    private final RecruiterWorkbenchService workbenchService;
    private final BookingService bookingService;
    private final InterviewEvaluationService evaluationService;
    private final ScheduleService scheduleService;

    public RecruiterWorkbenchView(
            RecruiterWorkbenchService workbenchService,
            BookingService bookingService,
            InterviewEvaluationService evaluationService,
            ScheduleService scheduleService
    ) {
        this.workbenchService = workbenchService;
        this.bookingService = bookingService;
        this.evaluationService = evaluationService;
        this.scheduleService = scheduleService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @PostConstruct
    private void refresh() {
        RecruiterWorkbenchData data = workbenchService.load();
        removeAll();

        H2 title = new H2("Recruiter Workbench");
        title.getStyle().set("margin", "0");
        Paragraph description = new Paragraph(
                "Your assigned interviews and the queues requiring action in your branch."
        );
        description.getStyle().set("margin", "0");
        Button refreshButton = new Button("Refresh", VaadinIcon.REFRESH.create(), event -> refresh());
        HorizontalLayout header = new HorizontalLayout(new VerticalLayout(title, description), refreshButton);
        header.setWidthFull();
        header.expand(header.getComponentAt(0));
        header.setAlignItems(Alignment.CENTER);

        add(
                header,
                followUpSection(data),
                section("My interviews today", data.todaysAssigned(), null),
                section("Upcoming assigned interviews", data.upcomingAssigned(), null),
                section("Pending confirmations", data.pendingConfirmations(), this::confirmButton),
                section("Attendance queue", data.attendanceQueue(), this::attendanceActions),
                section("Overdue evaluations", data.overdueEvaluations(), this::evaluateButton)
        );
    }

    private Component followUpSection(RecruiterWorkbenchData data) {
        VerticalLayout section = new VerticalLayout();
        section.setWidthFull();
        section.setPadding(false);
        H2 heading = new H2("Needs Follow-up");
        heading.getStyle().set("margin", "0");
        section.add(heading);

        if (data.finalInterviewFollowUps().isEmpty() && data.clientInterviewFollowUps().isEmpty()) {
            Span empty = new Span("No applicants currently require interview follow-up.");
            empty.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            section.add(empty);
        }

        section.add(
                followUpQueue("Final Interviews", data.finalInterviewFollowUps()),
                followUpQueue("Client Interviews", data.clientInterviewFollowUps())
        );
        return section;
    }

    private Component followUpQueue(String heading, List<FollowUpApplicant> applicants) {
        VerticalLayout section = new VerticalLayout();
        section.setWidthFull();
        section.getStyle()
                .set("border", "1px solid var(--vaadin-border-color-secondary)")
                .set("border-radius", "var(--vaadin-radius-l)");
        H3 title = new H3(heading + " (" + applicants.size() + ")");
        title.getStyle().set("margin", "0");
        section.add(title);

        if (applicants.isEmpty()) {
            Span empty = new Span("No applicants in this follow-up queue.");
            empty.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            section.add(empty);
            return section;
        }

        Grid<FollowUpApplicant> grid = new Grid<>();
        grid.setItems(applicants);
        grid.addColumn(FollowUpApplicant::applicantName)
                .setHeader("Applicant").setKey("follow-up-applicant").setAutoWidth(true);
        grid.addColumn(item -> item.requiredStage().name())
                .setHeader("Required Stage").setKey("follow-up-stage").setAutoWidth(true);
        grid.addColumn(item -> display(item.positionTitle()))
                .setHeader("Position").setKey("follow-up-position").setAutoWidth(true);
        grid.addColumn(item -> display(item.clientName()))
                .setHeader("Client").setKey("follow-up-client").setAutoWidth(true);
        grid.addColumn(item -> item.lastInterviewAt() == null
                        ? "—"
                        : item.lastInterviewAt().format(FOLLOW_UP_DATE))
                .setHeader("Last Interview").setKey("follow-up-last-interview").setAutoWidth(true);
        grid.addColumn(this::waitingDuration)
                .setHeader("Waiting").setKey("follow-up-waiting").setAutoWidth(true);
        grid.addComponentColumn(this::scheduleButton)
                .setHeader("Actions").setKey("follow-up-actions").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setAllRowsVisible(true);
        grid.setWidthFull();
        section.add(grid);
        return section;
    }

    private Component scheduleButton(FollowUpApplicant applicant) {
        Button button = new Button("Schedule Interview", event -> {
            try {
                new BookingFormDialog(
                        new BookingApplicantInput(
                                applicant.applicantId(), applicant.branchId(), applicant.applicantName()
                        ),
                        applicant.requiredStage(),
                        scheduleService,
                        command -> execute(
                                () -> bookingService.createBooking(command),
                                "Interview booked successfully."
                        )
                ).open();
            } catch (RuntimeException exception) {
                UserSafeNotifier.showError(exception);
            }
        });
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        return button;
    }

    private String waitingDuration(FollowUpApplicant applicant) {
        if (applicant.waitingSince() == null) {
            return "—";
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(
                applicant.waitingSince().toLocalDate(), LocalDate.now()
        ));
        return days + (days == 1 ? " day" : " days");
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private Component section(
            String heading,
            List<WorkbenchInterview> interviews,
            Function<WorkbenchInterview, Component> actionFactory
    ) {
        VerticalLayout section = new VerticalLayout();
        section.setWidthFull();
        section.getStyle()
                .set("border", "1px solid var(--vaadin-border-color-secondary)")
                .set("border-radius", "var(--vaadin-radius-l)");
        H3 title = new H3(heading + " (" + interviews.size() + ")");
        title.getStyle().set("margin", "0");
        section.add(title);

        if (interviews.isEmpty()) {
            Span empty = new Span("No interviews in this queue.");
            empty.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            section.add(empty);
            return section;
        }

        Grid<WorkbenchInterview> grid = new Grid<>();
        grid.setItems(interviews);
        grid.addColumn(WorkbenchInterview::bookingReference).setHeader("Reference").setAutoWidth(true);
        grid.addColumn(WorkbenchInterview::applicant).setHeader("Applicant").setAutoWidth(true);
        grid.addColumn(item -> item.interviewStage().name()).setHeader("Stage").setKey("interview-stage").setAutoWidth(true);
        grid.addColumn(WorkbenchInterview::position).setHeader("Position").setAutoWidth(true);
        grid.addColumn(WorkbenchInterview::date).setHeader("Date").setAutoWidth(true);
        grid.addColumn(item -> DateTimeUtil.formatTime(item.startTime())).setHeader("Time").setAutoWidth(true);
        grid.addColumn(WorkbenchInterview::recruiter).setHeader("Recruiter").setAutoWidth(true);
        grid.addColumn(item -> item.status().name()).setHeader("Status").setAutoWidth(true);
        grid.addComponentColumn(item -> {
            Button profile = new Button("Profile", event -> getUI().ifPresent(
                    ui -> ui.navigate(ApplicantDetailView.class,
                            new RouteParameters("applicantId", item.applicantId().toString()))
            ));
            profile.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return profile;
        }).setHeader("Applicant Profile").setKey("applicant-profile").setAutoWidth(true);
        if (actionFactory != null) {
            grid.addComponentColumn(actionFactory::apply).setHeader("Actions").setAutoWidth(true);
        }
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setAllRowsVisible(true);
        grid.setWidthFull();
        section.add(grid);
        return section;
    }

    private Component confirmButton(WorkbenchInterview interview) {
        Button button = new Button("Confirm", event -> execute(
                () -> bookingService.confirm(interview.bookingId()),
                "Interview confirmed."
        ));
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        return button;
    }

    private Component attendanceActions(WorkbenchInterview interview) {
        Button attended = new Button("Attended", event -> execute(
                () -> bookingService.markAttended(interview.bookingId()),
                "Attendance recorded."
        ));
        attended.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
        Button noShow = new Button("No show", event -> execute(
                () -> bookingService.markNoShow(interview.bookingId()),
                "No-show recorded."
        ));
        noShow.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);
        return new HorizontalLayout(attended, noShow);
    }

    private Component evaluateButton(WorkbenchInterview interview) {
        Button button = new Button("Evaluate", event -> {
            try {
                Booking booking = bookingService.findScopedById(interview.bookingId());
                new InterviewEvaluationDialog(booking, evaluationService, this::refresh).open();
            } catch (RuntimeException exception) {
                UserSafeNotifier.showError(exception);
            }
        });
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        return button;
    }

    private void execute(Runnable action, String successMessage) {
        try {
            action.run();
            Notification success = Notification.show(successMessage, 3000, Notification.Position.TOP_CENTER);
            success.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refresh();
        } catch (RuntimeException exception) {
            UserSafeNotifier.showError(exception);
        }
    }
}
