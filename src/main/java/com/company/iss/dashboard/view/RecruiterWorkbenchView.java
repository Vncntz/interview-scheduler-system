package com.company.iss.dashboard.view;

import com.company.iss.applicant.view.ApplicantDetailView;
import com.company.iss.booking.dialog.BookingFormDialog;
import com.company.iss.booking.dto.BookingApplicantInput;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.service.BookingService;
import com.company.iss.dashboard.dto.FollowUpApplicant;
import com.company.iss.dashboard.dto.FollowUpDeadlineFilter;
import com.company.iss.dashboard.dto.FollowUpQueueSummary;
import com.company.iss.dashboard.dto.FollowUpSlaStatus;
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
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

@Route(value = "workbench", layout = MainLayout.class)
@PageTitle("Recruiter Workbench")
@RolesAllowed("RECRUITER")
public class RecruiterWorkbenchView extends VerticalLayout {

    private static final int FOLLOW_UP_PAGE_SIZE = 25;
    private static final DateTimeFormatter FOLLOW_UP_DATE = DateTimeFormatter.ofPattern(
            "MMM d, uuuu h:mm a", Locale.ENGLISH
    );

    private final RecruiterWorkbenchService workbenchService;
    private final BookingService bookingService;
    private final InterviewEvaluationService evaluationService;
    private final ScheduleService scheduleService;
    private InterviewStage selectedFollowUpStage = InterviewStage.FINAL;
    private FollowUpDeadlineFilter selectedDeadlineFilter = FollowUpDeadlineFilter.ALL;

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

        if (data.finalInterviewFollowUp().total() == 0 && data.clientInterviewFollowUp().total() == 0) {
            Span empty = new Span("No applicants currently require interview follow-up.");
            empty.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            section.add(empty);
        }

        FollowUpQueueSummary summary = selectedFollowUpStage == InterviewStage.FINAL
                ? data.finalInterviewFollowUp()
                : data.clientInterviewFollowUp();
        Select<InterviewStage> stageFilter = new Select<>();
        stageFilter.setLabel("Stage");
        stageFilter.setItems(InterviewStage.FINAL, InterviewStage.CLIENT);
        stageFilter.setItemLabelGenerator(this::stageLabel);
        stageFilter.setValue(selectedFollowUpStage);
        stageFilter.addValueChangeListener(event -> {
            selectedFollowUpStage = event.getValue();
            refresh();
        });
        Select<FollowUpDeadlineFilter> deadlineFilter = new Select<>();
        deadlineFilter.setLabel("Deadline status");
        deadlineFilter.setItems(FollowUpDeadlineFilter.values());
        deadlineFilter.setItemLabelGenerator(this::deadlineFilterLabel);
        deadlineFilter.setValue(selectedDeadlineFilter);
        deadlineFilter.addValueChangeListener(event -> {
            selectedDeadlineFilter = event.getValue();
            refresh();
        });
        section.add(new HorizontalLayout(stageFilter, deadlineFilter), followUpQueue(summary));
        return section;
    }

    private Component followUpQueue(FollowUpQueueSummary summary) {
        InterviewStage stage = summary.stage();
        VerticalLayout section = new VerticalLayout();
        section.setWidthFull();
        section.getStyle()
                .set("border", "1px solid var(--vaadin-border-color-secondary)")
                .set("border-radius", "var(--vaadin-radius-l)");
        H3 title = new H3(stageLabel(stage) + " Interviews (" + summary.total() + ")");
        title.getStyle().set("margin", "0");
        Span workload = new Span(
                "On track " + summary.onTrack()
                        + " · Due soon " + summary.dueSoon()
                        + " · Overdue " + summary.overdue()
                        + " · Timing unavailable " + summary.timingUnavailable()
                        + " · Target " + formatTarget(summary)
        );
        workload.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        section.add(title, workload);

        if (summary.total() == 0) {
            Span empty = new Span("No applicants in this follow-up queue.");
            empty.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            section.add(empty);
            return section;
        }

        Grid<FollowUpApplicant> grid = new Grid<>();
        grid.setPageSize(FOLLOW_UP_PAGE_SIZE);
        CallbackDataProvider<FollowUpApplicant, Void> dataProvider = DataProvider.fromCallbacks(
                query -> workbenchService.findFollowUpPage(
                        stage, selectedDeadlineFilter, query.getOffset(), query.getLimit()
                ).stream(),
                query -> toIntCount(workbenchService.countFollowUps(stage, selectedDeadlineFilter))
        );
        grid.setDataProvider(dataProvider);
        grid.addColumn(FollowUpApplicant::applicantName)
                .setHeader("Applicant").setKey("follow-up-applicant").setAutoWidth(true);
        grid.addColumn(item -> item.requiredStage().name())
                .setHeader("Required Stage").setKey("follow-up-stage").setAutoWidth(true);
        grid.addColumn(item -> display(item.positionTitle()))
                .setHeader("Position").setKey("follow-up-position").setAutoWidth(true);
        grid.addColumn(item -> display(item.clientName()))
                .setHeader("Client").setKey("follow-up-client").setAutoWidth(true);
        grid.addColumn(item -> formatTimestamp(item.relatedAppointmentAt(), "Related appointment unavailable"))
                .setHeader("Related appointment").setKey("follow-up-related-appointment").setAutoWidth(true);
        grid.addColumn(item -> formatTimestamp(item.waitingSince(), "Timing unavailable"))
                .setHeader("Waiting since").setKey("follow-up-waiting-since").setAutoWidth(true);
        grid.addComponentColumn(this::statusBadge)
                .setHeader("Deadline status").setKey("follow-up-deadline-status").setAutoWidth(true);
        grid.addColumn(item -> formatTimestamp(item.dueAt(), "Timing unavailable"))
                .setHeader("Due at").setKey("follow-up-due-at").setAutoWidth(true);
        grid.addColumn(this::waitingDuration)
                .setHeader("Waiting").setKey("follow-up-waiting").setAutoWidth(true);
        grid.addComponentColumn(this::scheduleButton)
                .setHeader("Actions").setKey("follow-up-actions").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setHeight("360px");
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
        if (applicant.elapsed() == null) {
            return "Timing unavailable";
        }
        long hours = applicant.elapsed().toHours();
        long days = hours / 24;
        long remainderHours = hours % 24;
        return days == 0 ? hours + "h" : days + "d " + remainderHours + "h";
    }

    private Component statusBadge(FollowUpApplicant applicant) {
        FollowUpSlaStatus status = applicant.deadlineStatus();
        Span badge = new Span(status == null ? "Timing unavailable" : switch (status) {
            case ON_TRACK -> "On track";
            case DUE_SOON -> "Due soon";
            case OVERDUE -> "Overdue";
        });
        badge.getElement().getThemeList().add(status == null ? "badge contrast" : switch (status) {
            case ON_TRACK -> "badge success";
            case DUE_SOON -> "badge contrast";
            case OVERDUE -> "badge error";
        });
        badge.getElement().setAttribute("aria-label", "Follow-up SLA status: " + badge.getText());
        return badge;
    }

    private String formatTarget(FollowUpQueueSummary summary) {
        long hours = summary.target().toHours();
        return hours % 24 == 0 ? (hours / 24) + " days" : hours + " hours";
    }

    private int toIntCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String formatTimestamp(java.time.LocalDateTime timestamp, String unavailableText) {
        return timestamp == null ? unavailableText : timestamp.format(FOLLOW_UP_DATE);
    }

    private String stageLabel(InterviewStage stage) {
        return stage == InterviewStage.FINAL ? "Final" : "Client";
    }

    private String deadlineFilterLabel(FollowUpDeadlineFilter filter) {
        return switch (filter) {
            case ALL -> "All";
            case ON_TRACK -> "On track";
            case DUE_SOON -> "Due soon";
            case OVERDUE -> "Overdue";
            case TIMING_UNAVAILABLE -> "Timing unavailable";
        };
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
        grid.addColumn(item -> item.interviewStage().name())
                .setHeader("Stage").setKey("interview-stage").setAutoWidth(true);
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
