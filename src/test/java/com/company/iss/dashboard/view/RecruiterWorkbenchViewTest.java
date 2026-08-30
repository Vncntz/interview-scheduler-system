package com.company.iss.dashboard.view;

import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.service.BookingService;
import com.company.iss.dashboard.dto.RecruiterWorkbenchData;
import com.company.iss.dashboard.dto.WorkbenchInterview;
import com.company.iss.dashboard.service.RecruiterWorkbenchService;
import com.company.iss.evaluation.service.InterviewEvaluationService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecruiterWorkbenchViewTest {

    @Test
    void queuesExposeInterviewStageAndApplicantProfileNavigation() throws ReflectiveOperationException {
        RecruiterWorkbenchService service = mock(RecruiterWorkbenchService.class);
        WorkbenchInterview item = new WorkbenchInterview(
                10L, 20L, "BK-10", "Alex Candidate", "Engineer",
                LocalDate.of(2026, 9, 5), LocalTime.of(9, 0), LocalTime.of(10, 0),
                "Maria Santos", InterviewStage.FINAL, BookingStatus.CONFIRMED
        );
        when(service.load()).thenReturn(new RecruiterWorkbenchData(
                List.of(item), List.of(), List.of(), List.of(), List.of()
        ));
        RecruiterWorkbenchView view = new RecruiterWorkbenchView(
                service, mock(BookingService.class), mock(InterviewEvaluationService.class)
        );
        Method refresh = RecruiterWorkbenchView.class.getDeclaredMethod("refresh");
        refresh.setAccessible(true);

        refresh.invoke(view);

        List<Component> grids = descendants(view).filter(Grid.class::isInstance).toList();
        assertFalse(grids.isEmpty());
        Grid<?> grid = (Grid<?>) grids.getFirst();
        assertNotNull(grid.getColumnByKey("interview-stage"));
        assertNotNull(grid.getColumnByKey("applicant-profile"));
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
