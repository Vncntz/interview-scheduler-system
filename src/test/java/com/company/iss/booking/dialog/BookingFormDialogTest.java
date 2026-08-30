package com.company.iss.booking.dialog;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.dto.BookingApplicantInput;
import com.company.iss.branch.entity.Branch;
import com.company.iss.schedule.service.ScheduleService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingFormDialogTest {

    @Test
    void displaysTheInferredInterviewStageReadOnly() {
        Branch branch = new Branch();
        branch.setId(10L);
        Applicant applicant = new Applicant();
        applicant.setId(20L);
        applicant.setBranch(branch);
        ScheduleService scheduleService = mock(ScheduleService.class);
        when(scheduleService.findAvailableForCurrentUser(10L)).thenReturn(List.of());

        BookingFormDialog dialog = new BookingFormDialog(
                applicant,
                InterviewStage.FINAL,
                scheduleService,
                command -> { }
        );

        TextField stageField = descendants(dialog)
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .filter(field -> "Interview Stage".equals(field.getLabel()))
                .findFirst()
                .orElseThrow();
        assertEquals("FINAL", stageField.getValue());
        assertTrue(stageField.isReadOnly());
    }

    @Test
    void acceptsImmutableApplicantInputForProfileReuse() {
        ScheduleService scheduleService = mock(ScheduleService.class);
        when(scheduleService.findAvailableForCurrentUser(10L)).thenReturn(List.of());

        BookingFormDialog dialog = new BookingFormDialog(
                new BookingApplicantInput(20L, 10L, "Alex Candidate"),
                InterviewStage.CLIENT,
                scheduleService,
                command -> { }
        );

        TextField stage = descendants(dialog).filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .filter(field -> "Interview Stage".equals(field.getLabel()))
                .findFirst().orElseThrow();
        assertEquals("CLIENT", stage.getValue());
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
