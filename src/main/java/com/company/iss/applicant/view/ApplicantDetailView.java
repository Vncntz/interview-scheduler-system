package com.company.iss.applicant.view;

import com.company.iss.applicant.dto.ApplicantInterviewSummary;
import com.company.iss.applicant.dto.ApplicantProfile;
import com.company.iss.applicant.dto.ApplicantProfileAction;
import com.company.iss.applicant.dto.ApplicantRecruitmentState;
import com.company.iss.applicant.dto.ApplicantSummary;
import com.company.iss.applicant.dto.RecruitmentTimelineItem;
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
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.OrderedList;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Route(value = "applicants/:applicantId", layout = MainLayout.class)
@PageTitle("Applicant Profile")
@RolesAllowed({"ADMIN", "RECRUITER"})
public class ApplicantDetailView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
            "MMM d, uuuu h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter APPOINTMENT_DATE = DateTimeFormatter.ofPattern(
            "EEEE, MMMM d, uuuu", Locale.ENGLISH);

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
        setSpacing(false);
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

        Div backRow = new Div();
        backRow.addClassName("applicant-profile__back-row");
        Button back = new Button("Back to Applicants", VaadinIcon.ARROW_LEFT.create(),
                event -> getUI().ifPresent(ui -> ui.navigate(ApplicantView.class)));
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        backRow.add(back);

        Div hero = new Div();
        hero.addClassName("applicant-profile__hero");
        Span avatar = new Span(ApplicantProfilePresentation.initials(summary.fullName()));
        avatar.addClassName("applicant-profile__avatar");
        avatar.getElement().setAttribute("aria-label", "Applicant initials: " + avatar.getText());

        Div identity = new Div();
        identity.addClassName("applicant-profile__identity");
        H1 name = new H1(display(summary.fullName(), "Applicant Profile"));
        name.addClassName("applicant-profile__name");
        Paragraph role = new Paragraph(joinNonBlank(summary.position(), summary.client()));
        role.addClassName("applicant-profile__subtitle");
        Paragraph branch = new Paragraph(display(summary.branch(), "Branch not assigned"));
        branch.addClassName("applicant-profile__branch");
        Div badges = new Div();
        badges.addClassName("applicant-profile__badges");
        badges.add(
                badge(summary.active() ? "Active" : "Inactive", summary.active() ? "success" : "neutral",
                        "Applicant record"),
                badge(ApplicantProfilePresentation.applicantStatusLabel(summary.status()),
                        ApplicantProfilePresentation.applicantStatusTone(summary.status()), "Applicant status")
        );
        if (profile.currentState().currentStage() != null) {
            badges.add(badge(ApplicantProfilePresentation.interviewStageLabel(
                    profile.currentState().currentStage()), "accent", "Current interview stage"));
        }
        identity.add(name, role, branch, badges);

        Div actions = profileActions(profile, summary);
        hero.add(avatar, identity, actions);

        Div workflow = new Div(recruitmentStateCard(profile.currentState()),
                currentInterviewCard(profile.currentState()));
        workflow.addClassName("applicant-profile__workflow");

        add(backRow, hero, workflow, candidateDetailsCard(summary), timelineCard(profile));
    }

    private Component recruitmentStateCard(ApplicantRecruitmentState state) {
        Div card = card("Recruitment State", "applicant-profile__state");
        Div content = new Div();
        content.addClassName("applicant-profile__state-content");
        content.add(labeledBadge("Status",
                ApplicantProfilePresentation.applicantStatusLabel(state.status()),
                ApplicantProfilePresentation.applicantStatusTone(state.status()), "Recruitment status"));
        if (state.currentStage() != null) {
            content.add(labeledBadge("Current stage",
                    ApplicantProfilePresentation.interviewStageLabel(state.currentStage()),
                    "accent", "Current interview stage"));
        }
        if (state.nextRequiredStage() != null) {
            content.add(labeledBadge("Next required stage",
                    ApplicantProfilePresentation.interviewStageLabel(state.nextRequiredStage()),
                    "warning", "Next required interview stage"));
        }
        Div nextStep = new Div();
        nextStep.addClassName("applicant-profile__next-step");
        Span label = new Span("Next step");
        label.addClassName("applicant-profile__label");
        Paragraph description = new Paragraph(ApplicantProfilePresentation.nextStep(
                state.nextAction(), state.nextRequiredStage()));
        nextStep.add(label, description);
        content.add(nextStep);
        card.add(content);
        return card;
    }

    private Component currentInterviewCard(ApplicantRecruitmentState state) {
        Div card = card("Current Interview", "applicant-profile__appointment");
        ApplicantInterviewSummary appointment = state.currentInterview();
        if (appointment == null) {
            Div empty = new Div();
            empty.addClassName("applicant-profile__empty-state");
            H3 heading = new H3("No current interview");
            Paragraph description = new Paragraph("No active interview appointment is currently scheduled.");
            empty.add(heading, description);
            if (state.nextRequiredStage() != null) {
                empty.add(labeledBadge("Next required stage",
                        ApplicantProfilePresentation.interviewStageLabel(state.nextRequiredStage()),
                        "warning", "Next required interview stage"));
            }
            card.add(empty);
            return card;
        }

        Div appointmentHeader = new Div();
        appointmentHeader.addClassName("applicant-profile__appointment-header");
        appointmentHeader.add(badge(
                ApplicantProfilePresentation.interviewStageLabel(appointment.interviewStage()),
                "accent", "Interview stage"));
        Div fields = new Div();
        fields.addClassName("applicant-profile__appointment-fields");
        fields.add(
                field("Date", appointment.date() == null ? null : appointment.date().format(APPOINTMENT_DATE)),
                field("Time", appointment.startTime() == null || appointment.endTime() == null ? null
                        : DateTimeUtil.formatTime(appointment.startTime()) + "–"
                        + DateTimeUtil.formatTime(appointment.endTime())),
                field("Mode", ApplicantProfilePresentation.interviewModeLabel(appointment.interviewMode())),
                field("Recruiter", appointment.recruiter()),
                field("Booking reference", appointment.bookingReference()),
                labeledBadge("Booking status",
                        ApplicantProfilePresentation.bookingStatusLabel(appointment.bookingStatus()),
                        ApplicantProfilePresentation.bookingStatusTone(appointment.bookingStatus()),
                        "Booking status")
        );
        card.add(appointmentHeader, fields);
        return card;
    }

    private Component candidateDetailsCard(ApplicantSummary summary) {
        Div card = card("Candidate Details", "applicant-profile__details");
        Div fields = new Div();
        fields.addClassName("applicant-profile__details-grid");
        fields.add(
                field("Email", summary.email()),
                field("Mobile", summary.mobileNumber()),
                field("Branch", summary.branch()),
                field("Position", summary.position()),
                field("Client", summary.client()),
                field("Work Location", summary.workLocation()),
                field("Source", summary.source()),
                field("Record status", summary.active() ? "Active" : "Inactive"),
                field("Application date", summary.createdAt() == null ? null : summary.createdAt().format(DATE_TIME))
        );
        Div remarks = field("Remarks", summary.remarks());
        remarks.addClassName("applicant-profile__field--wide");
        fields.add(remarks);
        card.add(fields);
        return card;
    }

    private Div profileActions(ApplicantProfile profile, ApplicantSummary summary) {
        Div actions = new Div();
        actions.addClassName("applicant-profile__actions");
        actions.getElement().setAttribute("role", "group");
        actions.getElement().setAttribute("aria-label", "Applicant profile actions");
        for (int index = 0; index < profile.actions().size(); index++) {
            ApplicantProfileAction action = profile.actions().get(index);
            String label = ApplicantProfilePresentation.actionLabel(action);
            Button button = new Button(label, event -> execute(action, summary));
            button.addClassName(index == 0
                    ? "applicant-profile__action--primary"
                    : "applicant-profile__action--secondary");
            button.getElement().setAttribute("aria-label", label);
            if (index == 0) {
                button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            } else {
                button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            }
            actions.add(button);
        }
        if (profile.actions().isEmpty()) {
            Span empty = new Span("No profile actions are currently available.");
            empty.addClassName("applicant-profile__actions-empty");
            actions.add(empty);
        }
        return actions;
    }

    private Component timelineCard(ApplicantProfile profile) {
        Div card = card("Recruitment Timeline", "applicant-profile__timeline-card");
        if (profile.timeline().isEmpty()) {
            Paragraph empty = new Paragraph("No timestamped recruitment history is available.");
            empty.addClassName("applicant-profile__empty-state-copy");
            card.add(empty);
            return card;
        }

        OrderedList timeline = new OrderedList();
        timeline.addClassName("applicant-profile__timeline");
        for (RecruitmentTimelineItem item : profile.timeline()) {
            String family = ApplicantProfilePresentation.timelineFamily(item.event());
            ListItem entry = new ListItem();
            entry.addClassNames("applicant-profile__timeline-item",
                    "applicant-profile__timeline-item--" + family);
            entry.getElement().setAttribute("data-event-family", family);

            Span marker = new Span();
            marker.addClassName("applicant-profile__timeline-marker");
            marker.getElement().setAttribute("aria-hidden", "true");
            Span time = new Span(item.occurredAt() == null ? "Date unavailable" : item.occurredAt().format(DATE_TIME));
            time.addClassName("applicant-profile__timeline-time");
            H3 title = new H3(ApplicantProfilePresentation.timelineTitle(item.event()));
            title.addClassName("applicant-profile__timeline-title");

            Div content = new Div(time, title);
            content.addClassName("applicant-profile__timeline-content");
            if (item.interviewStage() != null) {
                content.add(badge(ApplicantProfilePresentation.interviewStageLabel(item.interviewStage()),
                        "accent", "Interview stage"));
            }
            if (item.description() != null && !item.description().isBlank()) {
                Paragraph description = new Paragraph(item.description());
                description.addClassName("applicant-profile__timeline-description");
                content.add(description);
            }
            entry.add(marker, content);
            timeline.add(entry);
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
        title.addClassName("applicant-profile__section-title");
        card.add(title);
        return card;
    }

    private Div field(String label, String value) {
        Div field = new Div();
        field.addClassName("applicant-profile__field");
        Span caption = new Span(label);
        caption.addClassName("applicant-profile__label");
        Span content = new Span(display(value, "—"));
        content.addClassName("applicant-profile__value");
        field.add(caption, content);
        return field;
    }

    private Div labeledBadge(String label, String value, String tone, String accessibleContext) {
        Div field = new Div();
        field.addClassName("applicant-profile__field");
        Span caption = new Span(label);
        caption.addClassName("applicant-profile__label");
        field.add(caption, badge(value, tone, accessibleContext));
        return field;
    }

    private Span badge(String value, String tone, String accessibleContext) {
        Span badge = new Span(value);
        badge.addClassNames("applicant-profile__badge", "applicant-profile__badge--" + tone);
        badge.getElement().setAttribute("aria-label", accessibleContext + ": " + value);
        return badge;
    }

    private String display(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
