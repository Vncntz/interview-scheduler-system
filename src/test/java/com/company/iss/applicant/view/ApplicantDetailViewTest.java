package com.company.iss.applicant.view;

import com.company.iss.applicant.dto.*;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.service.ApplicantJourneyService;
import com.company.iss.booking.service.BookingService;
import com.company.iss.evaluation.service.InterviewEvaluationService;
import com.company.iss.schedule.service.ScheduleService;
import com.company.iss.shared.view.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    void rendersStructuredProfileSectionsFromDto() {
        ApplicantJourneyService service = mock(ApplicantJourneyService.class);
        when(service.load(42L)).thenReturn(profile());
        ApplicantDetailView view = new ApplicantDetailView(
                service, mock(BookingService.class), mock(ScheduleService.class),
                mock(InterviewEvaluationService.class)
        );
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters("applicantId", "42"));

        view.beforeEnter(event);

        List<String> headings = descendants(view).filter(H2.class::isInstance)
                .map(H2.class::cast).map(H2::getText).toList();
        assertTrue(headings.containsAll(List.of(
                "Applicant Summary", "Current Recruitment State", "Quick Actions", "Recruitment Timeline"
        )));
        assertTrue(view.hasClassName("applicant-profile"));
        verify(service).load(42L);
        verify(event, never()).rerouteTo(any(Class.class));
    }

    private ApplicantProfile profile() {
        ApplicantSummary summary = new ApplicantSummary(
                42L, 7L, "Alex Candidate", "alex@example.test", "09170000000",
                "Manila", "Engineer", "Client", "Singapore", ApplicantStatus.NEW,
                true, "Referral", "", LocalDateTime.of(2026, 8, 1, 8, 0)
        );
        ApplicantRecruitmentState state = new ApplicantRecruitmentState(
                ApplicantStatus.NEW, null, com.company.iss.booking.entity.InterviewStage.INITIAL,
                ApplicantNextAction.SCHEDULE_INTERVIEW, null, false, false
        );
        return new ApplicantProfile(summary, state, List.of(), List.of(new RecruitmentTimelineItem(
                RecruitmentTimelineEvent.APPLICATION_CREATED, summary.createdAt(),
                "Application created", "Engineer · Manila", null
        )));
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
