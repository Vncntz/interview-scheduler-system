package com.company.iss.evaluation.view;

import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.evaluation.dto.EvaluationGridFilter;
import com.company.iss.evaluation.dto.EvaluationGridSort;
import com.company.iss.evaluation.dto.EvaluationGridSortOrder;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.evaluation.service.InterviewEvaluationService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.provider.QuerySortOrder;
import com.vaadin.flow.data.provider.SortDirection;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewEvaluationViewTest {

    @Test
    void providerForwardsNonAlignedWindowAllFiltersAndTypedSort() {
        InterviewEvaluationService service = mock(InterviewEvaluationService.class);
        InterviewEvaluationView view = new InterviewEvaluationView(service);
        Grid<InterviewEvaluation> grid = grid(view);
        keyword(view).setValue("  Alex  ");
        stage(view).setValue(InterviewStage.FINAL);
        result(view).setValue(InterviewResult.PASS);
        LocalDate date = LocalDate.of(2026, 9, 1);
        date(view).setValue(date);
        EvaluationGridFilter filter = new EvaluationGridFilter(
                "alex", InterviewStage.FINAL, InterviewResult.PASS, date
        );
        List<EvaluationGridSortOrder> sort = List.of(
                new EvaluationGridSortOrder(EvaluationGridSort.EVALUATION_DATE, Sort.Direction.ASC)
        );
        when(service.findGridPage(filter, 25, 10, sort)).thenReturn(List.of());

        grid.getDataProvider().fetch(new Query<>(
                25, 10, List.of(new QuerySortOrder("evaluationDate", SortDirection.ASCENDING)), null, null
        )).toList();

        assertEquals(50, grid.getPageSize());
        assertInstanceOf(CallbackDataProvider.class, grid.getDataProvider());
        assertTrue(keyword(view).isClearButtonVisible());
        assertTrue(stage(view).isClearButtonVisible());
        assertTrue(result(view).isClearButtonVisible());
        assertTrue(date(view).isClearButtonVisible());
        verify(service).findGridPage(filter, 25, 10, sort);
    }

    @Test
    void filterRefreshRetainsProviderAndClearsSelection() {
        InterviewEvaluationView view = new InterviewEvaluationView(mock(InterviewEvaluationService.class));
        Grid<InterviewEvaluation> grid = grid(view);
        var provider = grid.getDataProvider();
        InterviewEvaluation stale = new InterviewEvaluation();
        stale.setId(1L);
        grid.select(stale);

        result(view).setValue(InterviewResult.ON_HOLD);

        assertSame(provider, grid.getDataProvider());
        assertTrue(grid.asSingleSelect().isEmpty());
    }

    @Test
    void countUsesCurrentFilterAndSaturates() {
        InterviewEvaluationService service = mock(InterviewEvaluationService.class);
        InterviewEvaluationView view = new InterviewEvaluationView(service);
        when(service.countGrid(EvaluationGridFilter.empty())).thenReturn((long) Integer.MAX_VALUE + 1);

        int size = grid(view).getDataProvider().size(new Query<>(0, 50, List.of(), null, null));

        assertEquals(Integer.MAX_VALUE, size);
    }

    @SuppressWarnings("unchecked")
    private Grid<InterviewEvaluation> grid(InterviewEvaluationView view) {
        return (Grid<InterviewEvaluation>) descendants(view)
                .filter(Grid.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    private TextField keyword(InterviewEvaluationView view) {
        return descendants(view).filter(TextField.class::isInstance).map(TextField.class::cast)
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private ComboBox<InterviewStage> stage(InterviewEvaluationView view) {
        return (ComboBox<InterviewStage>) descendants(view).filter(ComboBox.class::isInstance)
                .map(ComboBox.class::cast)
                .filter(combo -> "Interview Stage".equals(combo.getPlaceholder()))
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private ComboBox<InterviewResult> result(InterviewEvaluationView view) {
        return (ComboBox<InterviewResult>) descendants(view).filter(ComboBox.class::isInstance)
                .map(ComboBox.class::cast)
                .filter(combo -> "Evaluation Result".equals(combo.getPlaceholder()))
                .findFirst().orElseThrow();
    }

    private DatePicker date(InterviewEvaluationView view) {
        return descendants(view).filter(DatePicker.class::isInstance).map(DatePicker.class::cast)
                .findFirst().orElseThrow();
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
