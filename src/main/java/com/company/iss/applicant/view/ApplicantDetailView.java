package com.company.iss.applicant.view;

import com.company.iss.applicant.dto.*;
import com.company.iss.applicant.service.ApplicantJourneyService;
import com.company.iss.booking.dialog.BookingFormDialog;
import com.company.iss.booking.dto.BookingApplicantInput;
import com.company.iss.booking.service.BookingService;
import com.company.iss.booking.view.BookingView;
import com.company.iss.evaluation.dialog.InterviewEvaluationDialog;
import com.company.iss.evaluation.service.InterviewEvaluationService;
import com.company.iss.hiring.view.HiringDecisionView;
import com.company.iss.schedule.service.ScheduleService;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import com.company.iss.shared.util.DateTimeUtil;
import com.company.iss.shared.view.MainLayout;
import com.company.iss.shared.view.UserSafeNotifier;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.time.format.DateTimeFormatter;

@Route(value = "applicants/:applicantId", layout = MainLayout.class)
@PageTitle("Applicant Profile")
@RolesAllowed({"ADMIN", "RECRUITER"})
public class ApplicantDetailView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, uuuu h:mm a");

    private final ApplicantJourneyService journeyService;
    private final BookingService bookingService;
    private final ScheduleService scheduleService;
    private final InterviewEvaluationService evaluationService;
    private Long applicantId;

    public ApplicantDetailView(
            ApplicantJourneyService journeyService,
            BookingService bookingService,
            ScheduleService scheduleService,
            InterviewEvaluationService evaluationService
    ) {
        this.journeyService = journeyService;
        this.bookingService = bookingService;
        this.scheduleService = scheduleService;
        this.evaluationService = evaluationService;
        addClassName("applicant-profile");
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        try {
            applicantId = Long.valueOf(event.getRouteParameters().get("applicantId")
                    .orElseThrow(() -> new IllegalArgumentException("Missing applicant")));
            render(journeyService.load(applicantId));
        } catch (RuntimeException exception) {
            applicantId = null;
            removeAll();
            UserSafeNotifier.showError(new BusinessRuleViolationException("Applicant profile is unavailable."));
            event.rerouteTo(ApplicantView.class);
        }
    }

    private void reload() {
        if (applicantId != null) {
            removeAll();
            render(journeyService.load(applicantId));
        }
    }

    private void render(ApplicantProfile profile) {
        ApplicantSummary summary = profile.summary();
        Button back = new Button("Back to Applicants", VaadinIcon.ARROW_LEFT.create(),
                event -> getUI().ifPresent(ui -> ui.navigate(ApplicantView.class)));
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        H1 name = new H1(summary.fullName().isBlank() ? "Applicant Profile" : summary.fullName());
        name.getStyle().set("margin", "0");
        Paragraph subtitle = new Paragraph(joinNonBlank(summary.position(), summary.client()));
        subtitle.getStyle().set("margin", "0");
        VerticalLayout identity = new VerticalLayout(name, subtitle);
        identity.setPadding(false);
        identity.setSpacing(false);
        HorizontalLayout header = new HorizontalLayout(identity, back);
        header.addClassName("applicant-profile__header");
        header.setWidthFull();
        header.expand(identity);
        header.setAlignItems(Alignment.CENTER);

        Div cards = new Div(summaryCard(summary), stateCard(profile.currentState()));
        cards.addClassName("applicant-profile__cards");
        add(header, cards, actionsCard(profile), timelineCard(profile));
    }

    private Component summaryCard(ApplicantSummary summary) {
        Div card = card("Applicant Summary", "applicant-profile__summary");
        Div fields = new Div();
        fields.addClassName("applicant-profile__fields");
        fields.add(
                field("Email", summary.email()), field("Mobile", summary.mobileNumber()),
                field("Branch", summary.branch()), field("Position", summary.position()),
                field("Client", summary.client()), field("Work location", summary.workLocation()),
                field("Status", summary.status() == null ? "" : summary.status().name()),
                field("Record", summary.active() ? "Active" : "Inactive"),
                field("Source", summary.source()), field("Remarks", summary.remarks())
        );
        card.add(fields);
        return card;
    }

    private Component stateCard(ApplicantRecruitmentState state) {
        Div card = card("Current Recruitment State", "applicant-profile__state");
        Div fields = new Div();
        fields.addClassName("applicant-profile__fields");
        fields.add(
                field("Status", state.status() == null ? "" : state.status().name()),
                field("Current stage", name(state.currentStage())),
                field("Next required stage", name(state.nextRequiredStage())),
                field("Next action", nextActionLabel(state.nextAction()))
        );
        if (state.currentInterview() == null) {
            fields.add(field("Upcoming interview", "No upcoming interview"));
        } else {
            ApplicantInterviewSummary appointment = state.currentInterview();
            fields.add(
                    field("Upcoming interview", appointment.date() + " · "
                            + DateTimeUtil.formatTime(appointment.startTime()) + "–"
                            + DateTimeUtil.formatTime(appointment.endTime())),
                    field("Mode", name(appointment.interviewMode())),
                    field("Recruiter", appointment.recruiter()),
                    field("Booking", appointment.bookingReference() + " · " + name(appointment.bookingStatus()))
            );
        }
        card.add(fields);
        return card;
    }

    private Component actionsCard(ApplicantProfile profile) {
        Div card = card("Quick Actions", "applicant-profile__actions-card");
        HorizontalLayout actions = new HorizontalLayout();
        actions.addClassName("applicant-profile__actions");
        actions.setWidthFull();
        for (ApplicantProfileAction action : profile.actions()) {
            Button button = new Button(action.label(), event -> execute(action, profile.summary()));
            button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            actions.add(button);
        }
        if (profile.actions().isEmpty()) {
            actions.add(new Span("No quick action is available. Review the current state or use the dedicated workflow screen."));
        }
        card.add(actions);
        return card;
    }

    private Component timelineCard(ApplicantProfile profile) {
        Div card = card("Recruitment Timeline", "applicant-profile__timeline-card");
        VerticalLayout timeline = new VerticalLayout();
        timeline.addClassName("applicant-profile__timeline");
        timeline.setPadding(false);
        for (RecruitmentTimelineItem item : profile.timeline()) {
            Div row = new Div();
            row.addClassName("applicant-profile__timeline-item");
            Span time = new Span(item.occurredAt().format(DATE_TIME));
            time.addClassName("applicant-profile__timeline-time");
            H3 title = new H3(item.title());
            title.getStyle().set("margin", "0");
            Paragraph description = new Paragraph(item.description());
            description.getStyle().set("margin", "0");
            row.add(time, title, description);
            timeline.add(row);
        }
        if (profile.timeline().isEmpty()) {
            timeline.add(new Span("No timestamped recruitment history is available."));
        }
        card.add(timeline);
        return card;
    }

    private void execute(ApplicantProfileAction action, ApplicantSummary summary) {
        try {
            switch (action.type()) {
                case SCHEDULE_INTERVIEW -> new BookingFormDialog(
                        new BookingApplicantInput(summary.applicantId(), summary.branchId(), summary.fullName()),
                        action.interviewStage(), scheduleService, command -> {
                    try {
                        bookingService.createBooking(command);
                        success("Interview booked successfully.");
                        reload();
                    } catch (RuntimeException exception) {
                        UserSafeNotifier.showError(exception);
                    }
                }).open();
                case EVALUATE_INTERVIEW -> new InterviewEvaluationDialog(
                        bookingService.findScopedById(action.bookingId()), evaluationService, this::reload).open();
                case VIEW_BOOKING -> getUI().ifPresent(ui -> ui.navigate(BookingView.class));
                case OPEN_HIRING -> getUI().ifPresent(ui -> ui.navigate(HiringDecisionView.class));
            }
        } catch (RuntimeException exception) {
            UserSafeNotifier.showError(exception);
        }
    }

    private Div card(String heading, String className) {
        Div card = new Div();
        card.addClassNames("applicant-profile__card", className);
        H2 title = new H2(heading);
        title.getStyle().set("margin", "0 0 var(--vaadin-gap-m) 0");
        card.add(title);
        return card;
    }

    private Div field(String label, String value) {
        Div field = new Div();
        field.addClassName("applicant-profile__field");
        Span caption = new Span(label);
        caption.addClassName("applicant-profile__label");
        Span content = new Span(value == null || value.isBlank() ? "—" : value);
        field.add(caption, content);
        return field;
    }

    private String nextActionLabel(ApplicantNextAction action) {
        if (action == null) {
            return "Review";
        }
        return switch (action) {
            case SCHEDULE_INTERVIEW -> "Schedule interview";
            case CONFIRM_INTERVIEW -> "Confirm interview";
            case RECORD_ATTENDANCE -> "Record attendance";
            case MANAGE_BOOKING -> "Manage booking";
            case EVALUATE_INTERVIEW -> "Evaluate interview";
            case ISSUE_JOB_OFFER -> "Issue job offer";
            case RECORD_OFFER_DECISION -> "Record offer decision";
            case REVIEW -> "Manual review";
            case RECRUITMENT_COMPLETE -> "Recruitment complete";
            case RECRUITMENT_CLOSED -> "Recruitment closed";
        };
    }

    private String name(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private String joinNonBlank(String first, String second) {
        if (first == null || first.isBlank()) {
            return second == null ? "" : second;
        }
        return second == null || second.isBlank() ? first : first + " · " + second;
    }

    private void success(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
}
