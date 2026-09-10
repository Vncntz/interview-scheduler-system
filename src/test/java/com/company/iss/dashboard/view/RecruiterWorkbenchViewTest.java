package com.company.iss.dashboard.view;

import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.service.BookingService;
import com.company.iss.dashboard.dto.FollowUpQueueSummary;
import com.company.iss.dashboard.dto.FollowUpDeadlineFilter;
import com.company.iss.dashboard.dto.RecruiterWorkbenchData;
import com.company.iss.dashboard.dto.WorkbenchInterview;
import com.company.iss.dashboard.service.RecruiterWorkbenchService;
import com.company.iss.evaluation.service.InterviewEvaluationService;
import com.company.iss.schedule.service.ScheduleService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.Query;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class RecruiterWorkbenchViewTest {

    @Test
    void interviewQueuesExposeStageAndApplicantProfileNavigation() throws ReflectiveOperationException {
        RecruiterWorkbenchService service = mock(RecruiterWorkbenchService.class);
        WorkbenchInterview item = new WorkbenchInterview(
                10L, 20L, "BK-10", "Alex Candidate", "Engineer",
                LocalDate.of(2026, 9, 5), LocalTime.of(9, 0), LocalTime.of(10, 0),
                "Maria Santos", InterviewStage.FINAL, BookingStatus.CONFIRMED
        );
        when(service.load()).thenReturn(data(List.of(item), summary(InterviewStage.FINAL, 0),
                summary(InterviewStage.CLIENT, 0)));

        RecruiterWorkbenchView view = view(service);
        refresh(view);

        List<Component> grids = descendants(view).filter(Grid.class::isInstance).toList();
        assertFalse(grids.isEmpty());
        Grid<?> grid = (Grid<?>) grids.getFirst();
        assertNotNull(grid.getColumnByKey("interview-stage"));
        assertNotNull(grid.getColumnByKey("applicant-profile"));
    }

    @Test
    void followUpQueuesAreBoundedLazyAndExposeSlaOperationalColumns() throws ReflectiveOperationException {
        RecruiterWorkbenchService service = mock(RecruiterWorkbenchService.class);
        when(service.load()).thenReturn(data(List.of(), summary(InterviewStage.FINAL, 4),
                summary(InterviewStage.CLIENT, 0)));

        RecruiterWorkbenchView view = view(service);
        refresh(view);

        Grid<?> grid = descendants(view).filter(Grid.class::isInstance)
                .map(Grid.class::cast)
                .filter(candidate -> candidate.getColumnByKey("follow-up-stage") != null)
                .findFirst().orElseThrow();
        assertInstanceOf(CallbackDataProvider.class, grid.getDataProvider());
        assertEquals(25, grid.getPageSize());
        assertEquals("360px", grid.getHeight());
        assertNotNull(grid.getColumnByKey("follow-up-applicant"));
        assertNotNull(grid.getColumnByKey("follow-up-position"));
        assertNotNull(grid.getColumnByKey("follow-up-client"));
        assertNotNull(grid.getColumnByKey("follow-up-related-appointment"));
        assertNotNull(grid.getColumnByKey("follow-up-waiting-since"));
        assertNotNull(grid.getColumnByKey("follow-up-deadline-status"));
        assertNotNull(grid.getColumnByKey("follow-up-due-at"));
        assertNotNull(grid.getColumnByKey("follow-up-waiting"));
        assertNotNull(grid.getColumnByKey("follow-up-actions"));
        assertEquals(2, descendants(view).filter(Select.class::isInstance).count());
    }

    @Test
    void emptyFollowUpQueuesShowAnExplicitEmptyState() throws ReflectiveOperationException {
        RecruiterWorkbenchService service = mock(RecruiterWorkbenchService.class);
        when(service.load()).thenReturn(data(List.of(), summary(InterviewStage.FINAL, 0),
                summary(InterviewStage.CLIENT, 0)));

        RecruiterWorkbenchView view = view(service);
        refresh(view);

        assertFalse(descendants(view)
                .filter(Span.class::isInstance)
                .map(Span.class::cast)
                .noneMatch(span -> "No applicants currently require interview follow-up.".equals(span.getText())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void stageAndDeadlineFiltersDriveLazyQueriesAndSurviveRefresh() throws ReflectiveOperationException {
        RecruiterWorkbenchService service = mock(RecruiterWorkbenchService.class);
        when(service.load()).thenReturn(data(List.of(), summary(InterviewStage.FINAL, 2),
                summary(InterviewStage.CLIENT, 2)));
        when(service.findFollowUpPage(InterviewStage.CLIENT, FollowUpDeadlineFilter.OVERDUE, 0, 25))
                .thenReturn(List.of());
        when(service.countFollowUps(InterviewStage.CLIENT, FollowUpDeadlineFilter.OVERDUE)).thenReturn(0L);
        RecruiterWorkbenchView view = view(service);
        refresh(view);

        Select<InterviewStage> stage = (Select<InterviewStage>) descendants(view)
                .filter(Select.class::isInstance).map(Select.class::cast)
                .filter(select -> "Stage".equals(select.getLabel())).findFirst().orElseThrow();
        stage.setValue(InterviewStage.CLIENT);
        Select<FollowUpDeadlineFilter> deadline = (Select<FollowUpDeadlineFilter>) descendants(view)
                .filter(Select.class::isInstance).map(Select.class::cast)
                .filter(select -> "Deadline status".equals(select.getLabel())).findFirst().orElseThrow();
        deadline.setValue(FollowUpDeadlineFilter.OVERDUE);

        Grid<?> grid = descendants(view).filter(Grid.class::isInstance).map(Grid.class::cast)
                .filter(candidate -> candidate.getColumnByKey("follow-up-stage") != null)
                .findFirst().orElseThrow();
        CallbackDataProvider<?, Void> provider = (CallbackDataProvider<?, Void>) grid.getDataProvider();
        provider.fetch(new Query<>(0, 25, List.of(), null, null)).toList();
        provider.size(new Query<>());
        refresh(view);

        assertTrue(descendants(view).filter(Select.class::isInstance).map(Select.class::cast)
                .anyMatch(select -> select.getValue() == InterviewStage.CLIENT));
        assertTrue(descendants(view).filter(Select.class::isInstance).map(Select.class::cast)
                .anyMatch(select -> select.getValue() == FollowUpDeadlineFilter.OVERDUE));
        verify(service).findFollowUpPage(InterviewStage.CLIENT, FollowUpDeadlineFilter.OVERDUE, 0, 25);
        verify(service).countFollowUps(InterviewStage.CLIENT, FollowUpDeadlineFilter.OVERDUE);
    }

    private RecruiterWorkbenchView view(RecruiterWorkbenchService service) {
        return new RecruiterWorkbenchView(
                service, mock(BookingService.class), mock(InterviewEvaluationService.class),
                mock(ScheduleService.class)
        );
    }

    private RecruiterWorkbenchData data(
            List<WorkbenchInterview> today,
            FollowUpQueueSummary finalSummary,
            FollowUpQueueSummary clientSummary
    ) {
        return new RecruiterWorkbenchData(
                today, List.of(), List.of(), List.of(), List.of(), finalSummary, clientSummary
        );
    }

    private FollowUpQueueSummary summary(InterviewStage stage, long total) {
        return new FollowUpQueueSummary(
                stage, stage == InterviewStage.FINAL ? Duration.ofHours(72) : Duration.ofHours(120),
                total, total, 0, 0, 0, LocalDateTime.of(2026, 9, 10, 12, 0)
        );
    }

    private void refresh(RecruiterWorkbenchView view) throws ReflectiveOperationException {
        Method refresh = RecruiterWorkbenchView.class.getDeclaredMethod("refresh");
        refresh.setAccessible(true);
        refresh.invoke(view);
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
