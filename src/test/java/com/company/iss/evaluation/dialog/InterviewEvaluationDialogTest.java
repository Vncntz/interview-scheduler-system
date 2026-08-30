package com.company.iss.evaluation.dialog;

import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.evaluation.service.InterviewEvaluationService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewEvaluationDialogTest {

    @Test
    void displaysStageAndUsesOnlyStageAllowedResults() {
        Booking booking = Booking.forInterviewStage(InterviewStage.CLIENT);
        booking.setId(42L);
        InterviewEvaluationService evaluationService = mock(InterviewEvaluationService.class);
        List<InterviewResult> allowed = List.of(
                InterviewResult.PASS,
                InterviewResult.FAIL,
                InterviewResult.ON_HOLD
        );
        when(evaluationService.allowedResults(InterviewStage.CLIENT)).thenReturn(allowed);

        InterviewEvaluationDialog dialog = new InterviewEvaluationDialog(
                booking,
                evaluationService,
                () -> { }
        );

        TextField stageField = descendants(dialog)
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .filter(field -> "Interview Stage".equals(field.getLabel()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        ComboBox<InterviewResult> resultField = (ComboBox<InterviewResult>) descendants(dialog)
                .filter(ComboBox.class::isInstance)
                .map(ComboBox.class::cast)
                .filter(field -> "Result".equals(field.getLabel()))
                .findFirst()
                .orElseThrow();

        assertEquals("CLIENT", stageField.getValue());
        assertTrue(stageField.isReadOnly());
        assertEquals(allowed, resultField.getListDataView().getItems().toList());
        verify(evaluationService).allowedResults(InterviewStage.CLIENT);
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
