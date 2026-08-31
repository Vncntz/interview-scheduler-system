package com.company.iss.applicant.view;

import com.company.iss.applicant.dto.ApplicantInterviewSummary;
import com.company.iss.applicant.dto.ApplicantNextAction;
import com.company.iss.applicant.dto.ApplicantProfile;
import com.company.iss.applicant.dto.ApplicantProfileAction;
import com.company.iss.applicant.dto.ApplicantProfileActionType;
import com.company.iss.applicant.dto.ApplicantRecruitmentState;
import com.company.iss.applicant.dto.ApplicantSummary;
import com.company.iss.applicant.dto.RecruitmentTimelineEvent;
import com.company.iss.applicant.dto.RecruitmentTimelineItem;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.service.ApplicantJourneyService;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.service.BookingService;
import com.company.iss.evaluation.service.InterviewEvaluationService;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.service.ScheduleService;
import com.company.iss.shared.view.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.OrderedList;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicantDetailViewTest {

    @Test
    void routeAndRolesAreExplicit() {
        Route route = ApplicantDetailView.class.getAnnotation(Route.class);
        assertEquals("applicants/:applicantId", route.value());
        assertEquals(MainLayout.class, route.layout());
        assertEquals(List.of("ADMIN", "RECRUITER"),
                List.of(ApplicantDetailView.class.getAnnotation(RolesAllowed.class).value()));
    }

    @Test
    void rendersIdentityStateDetailsAndOnlySuppliedActions() {
        ApplicantProfileAction primary = new ApplicantProfileAction(
                ApplicantProfileActionType.SCHEDULE_INTERVIEW, "raw action label", 42L, null,
                InterviewStage.FINAL);
        ApplicantProfileAction secondary = new ApplicantProfileAction(
                ApplicantProfileActionType.VIEW_BOOKING, "View Booking", 42L, 91L,
                InterviewStage.INITIAL);
        ApplicantProfile profile = profile(
                new ApplicantRecruitmentState(
                        ApplicantStatus.FOR_FINAL_INTERVIEW, InterviewStage.INITIAL, InterviewStage.FINAL,
                        ApplicantNextAction.SCHEDULE_INTERVIEW, null, false, false),
                List.of(primary, secondary),
                List.of(applicationCreated())
        );

        ApplicantDetailView view = render(profile);

        assertTrue(view.hasClassName("applicant-profile"));
        assertEquals("AC", firstWithClass(view, Span.class, "applicant-profile__avatar").getText());
        String text = view.getElement().getTextRecursively();
        assertTrue(text.contains("Alex Candidate"));
        assertTrue(text.contains("Engineer · Client"));
        assertTrue(text.contains("For Final Interview"));
        assertTrue(text.contains("Initial Interview"));
        assertTrue(text.contains("Final Interview"));
        assertTrue(text.contains("Schedule the applicant's final interview."));
        assertFalse(text.contains("FOR_FINAL_INTERVIEW"));

        List<String> headings = descendants(view).filter(H2.class::isInstance)
                .map(H2.class::cast).map(H2::getText).toList();
        assertEquals(List.of("Recruitment State", "Current Interview", "Candidate Details",
                "Recruitment Timeline"), headings);

        for (String detail : List.of("Email", "Mobile", "Branch", "Position", "Client", "Work Location",
                "Source", "Remarks", "Record status", "Application date")) {
            assertTrue(text.contains(detail), "Missing candidate detail: " + detail);
        }
        assertTrue(text.contains("A long remark\nthat keeps its line break."));

        List<Button> actionButtons = descendants(view).filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.hasClassName("applicant-profile__action--primary")
                        || button.hasClassName("applicant-profile__action--secondary"))
                .toList();
        assertEquals(List.of("Schedule Final Interview", "View Booking"),
                actionButtons.stream().map(Button::getText).toList());
        assertTrue(actionButtons.getFirst().getThemeNames().contains("primary"));
        assertTrue(actionButtons.get(1).getThemeNames().contains("tertiary"));
        assertEquals("Schedule Final Interview",
                actionButtons.getFirst().getElement().getAttribute("aria-label"));
    }

    @Test
    void rendersPopulatedAppointmentAndTimelineInServiceOrder() {
        ApplicantInterviewSummary appointment = new ApplicantInterviewSummary(
                91L, "BK-2026-VERY-LONG-REFERENCE", InterviewStage.INITIAL,
                LocalDate.of(2026, 9, 14), LocalTime.of(9, 30), LocalTime.of(10, 45),
                InterviewMode.ONSITE, "Riley Recruiter", BookingStatus.CONFIRMED);
        ApplicantRecruitmentState state = new ApplicantRecruitmentState(
                ApplicantStatus.SCHEDULED, InterviewStage.INITIAL, null,
                ApplicantNextAction.CONFIRM_INTERVIEW, appointment, false, false);
        List<RecruitmentTimelineItem> timeline = List.of(
                new RecruitmentTimelineItem(RecruitmentTimelineEvent.APPLICATION_CREATED,
                        LocalDateTime.of(2026, 8, 25, 17, 34), "Ignored application title",
                        "Sales Associate · Tanza\nSource: Indeed", null),
                new RecruitmentTimelineItem(RecruitmentTimelineEvent.INTERVIEW_BOOKED,
                        LocalDateTime.of(2026, 8, 26, 16, 11), "Ignored interview title",
                        "Reference: BK-813A4172", InterviewStage.INITIAL)
        );

        ApplicantDetailView view = render(profile(state, List.of(), timeline));
        String text = view.getElement().getTextRecursively();

        assertTrue(text.contains("Monday, September 14, 2026"));
        assertTrue(text.contains("9:30 AM–10:45 AM"));
        assertTrue(text.contains("On-site"));
        assertTrue(text.contains("Riley Recruiter"));
        assertTrue(text.contains("BK-2026-VERY-LONG-REFERENCE"));
        assertTrue(text.contains("Confirmed"));
        assertTrue(text.contains("Aug 25, 2026 · 5:34 PM"));
        assertTrue(text.contains("Aug 26, 2026 · 4:11 PM"));
        assertTrue(text.contains("Reference: BK-813A4172"));
        assertFalse(text.contains("ONSITE"));
        assertFalse(text.contains("Ignored interview title"));

        OrderedList orderedList = descendants(view).filter(OrderedList.class::isInstance)
                .map(OrderedList.class::cast).findFirst().orElseThrow();
        List<ListItem> items = orderedList.getChildren().map(ListItem.class::cast).toList();
        assertEquals(List.of("Application Created", "Interview Booked"),
                items.stream().map(item -> descendants(item).filter(H3.class::isInstance)
                        .map(H3.class::cast).map(H3::getText).findFirst().orElseThrow()).toList());
        assertEquals(List.of("application", "interview"),
                items.stream().map(item -> item.getElement().getAttribute("data-event-family")).toList());
        assertFalse(items.getFirst().getElement().getTextRecursively().contains("Interview"));
        assertTrue(items.get(1).getElement().getTextRecursively().contains("Initial Interview"));

        Div interviewHeader = firstWithClass(items.get(1), Div.class,
                "applicant-profile__timeline-header");
        assertTrue(interviewHeader.getChildren().anyMatch(component -> component instanceof H3 heading
                && "Interview Booked".equals(heading.getText())));
        assertTrue(interviewHeader.getChildren().anyMatch(component -> component instanceof Span stage
                && stage.hasClassName("applicant-profile__badge")
                && "Initial Interview".equals(stage.getText())));
    }

    @Test
    void rendersIntentionalEmptyAppointmentActionsAndTimelineStates() {
        ApplicantRecruitmentState state = new ApplicantRecruitmentState(
                ApplicantStatus.NEW, null, InterviewStage.INITIAL,
                ApplicantNextAction.REVIEW, null, false, false);

        ApplicantDetailView view = render(profile(state, List.of(), List.of()));
        String text = view.getElement().getTextRecursively();

        assertTrue(text.contains("No current interview"));
        assertTrue(text.contains("No active interview appointment is currently scheduled."));
        assertTrue(text.contains("Initial Interview"));
        assertTrue(text.contains("No profile actions are currently available."));
        assertTrue(text.contains("No timestamped recruitment history is available."));
        assertEquals(1, descendants(view).filter(Button.class::isInstance).count());
        assertTrue(descendants(view).noneMatch(OrderedList.class::isInstance));
    }

    @Test
    void loadsProfileOnceAndKeepsSuccessfulNavigationOnTheDetailRoute() {
        ApplicantJourneyService service = mock(ApplicantJourneyService.class);
        when(service.load(42L)).thenReturn(profile(
                new ApplicantRecruitmentState(ApplicantStatus.NEW, null, InterviewStage.INITIAL,
                        ApplicantNextAction.SCHEDULE_INTERVIEW, null, false, false),
                List.of(), List.of(applicationCreated())));
        ApplicantDetailView view = new ApplicantDetailView(
                service, mock(BookingService.class), mock(ScheduleService.class),
                mock(InterviewEvaluationService.class));
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters("applicantId", "42"));

        view.beforeEnter(event);

        verify(service).load(42L);
        verify(event, never()).rerouteTo(any(Class.class));
        assertNotNull(firstWithClass(view, Span.class, "applicant-profile__avatar"));
    }

    private ApplicantDetailView render(ApplicantProfile profile) {
        ApplicantJourneyService service = mock(ApplicantJourneyService.class);
        when(service.load(42L)).thenReturn(profile);
        ApplicantDetailView view = new ApplicantDetailView(
                service, mock(BookingService.class), mock(ScheduleService.class),
                mock(InterviewEvaluationService.class));
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters("applicantId", "42"));
        view.beforeEnter(event);
        return view;
    }

    private ApplicantProfile profile(
            ApplicantRecruitmentState state,
            List<ApplicantProfileAction> actions,
            List<RecruitmentTimelineItem> timeline
    ) {
        ApplicantSummary summary = new ApplicantSummary(
                42L, 7L, "Alex Candidate", "alex@example.test", "09170000000",
                "Manila", "Engineer", "Client", "Singapore", state.status(),
                true, "Referral", "A long remark\nthat keeps its line break.",
                LocalDateTime.of(2026, 8, 1, 8, 0)
        );
        return new ApplicantProfile(summary, state, actions, timeline);
    }

    private RecruitmentTimelineItem applicationCreated() {
        return new RecruitmentTimelineItem(
                RecruitmentTimelineEvent.APPLICATION_CREATED, LocalDateTime.of(2026, 8, 1, 8, 0),
                "Raw application title", "Engineer · Manila", null);
    }

    private <T extends Component> T firstWithClass(Component root, Class<T> type, String className) {
        return descendants(root).filter(type::isInstance).map(type::cast)
                .filter(component -> component.hasClassName(className)).findFirst().orElseThrow();
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
