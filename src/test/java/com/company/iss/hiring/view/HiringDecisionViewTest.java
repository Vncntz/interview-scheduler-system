package com.company.iss.hiring.view;

import com.company.iss.hiring.dto.CompletedDecisionSort;
import com.company.iss.hiring.dto.CompletedDecisionSortOrder;
import com.company.iss.hiring.dto.EligibleCandidateSort;
import com.company.iss.hiring.dto.EligibleCandidateSortOrder;
import com.company.iss.hiring.dto.HiringWorklistFilter;
import com.company.iss.hiring.dto.HiringDecisionSummary;
import com.company.iss.hiring.dto.OutstandingDecisionSort;
import com.company.iss.hiring.dto.OutstandingDecisionSortOrder;
import com.company.iss.hiring.service.HiringDecisionService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.provider.QuerySortOrder;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.value.ValueChangeMode;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HiringDecisionViewTest {

    @Test
    void allThreeProvidersForwardExactWindowsFilterAndWhitelistedSorts() {
        HiringDecisionService service = mock(HiringDecisionService.class);
        HiringDecisionView view = new HiringDecisionView(service);
        TextField filter = filter(view);
        filter.setValue("  Alex  ");
        HiringWorklistFilter expected = new HiringWorklistFilter("  Alex  ");
        List<Grid<?>> grids = grids(view);

        grids.get(0).getDataProvider().fetch(query(25, 10, "evaluatedAt")).toList();
        grids.get(1).getDataProvider().fetch(query(15, 5, "offeredAt")).toList();
        grids.get(2).getDataProvider().fetch(query(7, 3, "resolvedAt")).toList();

        verify(service).findEligiblePage(expected, 25, 10, List.of(
                new EligibleCandidateSortOrder(EligibleCandidateSort.EVALUATED_AT, Sort.Direction.ASC)));
        verify(service).findOutstandingPage(expected, 15, 5, List.of(
                new OutstandingDecisionSortOrder(OutstandingDecisionSort.OFFERED_AT, Sort.Direction.ASC)));
        verify(service).findCompletedPage(expected, 7, 3, List.of(
                new CompletedDecisionSortOrder(CompletedDecisionSort.RESOLVED_AT, Sort.Direction.ASC)));
        assertEquals(ValueChangeMode.LAZY, filter.getValueChangeMode());
        assertEquals(350, filter.getValueChangeTimeout());
        assertEquals(100, filter.getMaxLength());
        grids.forEach(grid -> {
            assertEquals(50, grid.getPageSize());
            assertInstanceOf(CallbackDataProvider.class, grid.getDataProvider());
            assertFalse(grid.isAllRowsVisible());
        });
    }

    @Test
    void countsUseSameFilterAndEmptyResultsAreClear() {
        HiringDecisionService service = mock(HiringDecisionService.class);
        HiringDecisionView view = new HiringDecisionView(service);
        HiringWorklistFilter filter = new HiringWorklistFilter("");
        when(service.countEligible(filter)).thenReturn(0L);
        when(service.countOutstanding(filter)).thenReturn(0L);
        when(service.countCompleted(filter)).thenReturn(0L);

        grids(view).forEach(grid -> grid.getDataProvider().size(new Query<>()));

        List<String> text = spanTexts(view).toList();
        assertTrue(text.contains("No matching eligible candidates."));
        assertTrue(text.contains("No matching outstanding offers."));
        assertTrue(text.contains("No matching completed decisions."));
    }

    @Test
    void successfulActionRefreshesEveryProviderWhilePreservingFilterAndSort() {
        HiringDecisionView view = new HiringDecisionView(mock(HiringDecisionService.class));
        TextField filter = filter(view);
        filter.setValue("candidate");
        List<Grid<?>> grids = grids(view);
        sortAscending(grids.get(0), "applicant");
        sortAscending(grids.get(1), "offeredAt");
        sortAscending(grids.get(2), "resolvedAt");
        List<Object> providers = grids.stream().map(grid -> (Object) grid.getDataProvider()).toList();
        List<AtomicInteger> refreshes = Stream.generate(AtomicInteger::new).limit(3).toList();
        for (int i = 0; i < grids.size(); i++) {
            int index = i;
            grids.get(i).getDataProvider().addDataProviderListener(event -> refreshes.get(index).incrementAndGet());
        }

        view.onHiringActionSucceeded();

        assertEquals("candidate", filter.getValue());
        for (int i = 0; i < grids.size(); i++) {
            assertSame(providers.get(i), grids.get(i).getDataProvider());
            assertEquals(1, refreshes.get(i).get());
            assertEquals(1, grids.get(i).getSortOrder().size());
        }
    }

    @Test
    void fetchFailureRemainsVisibleWhenCountLaterSucceedsAndDoesNotExposeDetails() {
        UI.setCurrent(new UI());
        HiringDecisionService service = mock(HiringDecisionService.class);
        HiringDecisionView view = new HiringDecisionView(service);
        HiringWorklistFilter filter = new HiringWorklistFilter("");
        when(service.findEligiblePage(filter, 0, 50, List.of()))
                .thenThrow(new IllegalStateException("backend diagnostic detail"));
        when(service.countEligible(filter)).thenReturn(12L);
        Grid<?> eligible = grids(view).getFirst();

        eligible.getDataProvider().fetch(new Query<>(0, 50, List.of(), null, null)).toList();
        eligible.getDataProvider().size(new Query<>());

        List<String> text = spanTexts(view).toList();
        assertTrue(text.contains("Unable to load eligible candidates."));
        assertFalse(text.stream().anyMatch(value -> value.contains("backend diagnostic detail")));
        UI.setCurrent(null);
    }

    @Test
    void successfulLastRowActionRecountsAndAllowsReloadFromFirstWindow() {
        HiringDecisionService service = mock(HiringDecisionService.class);
        HiringDecisionView view = new HiringDecisionView(service);
        HiringWorklistFilter filter = new HiringWorklistFilter("");
        Grid<?> outstanding = grids(view).get(1);
        when(service.countOutstanding(filter)).thenReturn(1L, 0L);
        when(service.findOutstandingPage(filter, 50, 50, List.of()))
                .thenReturn(List.of(mock(HiringDecisionSummary.class)), List.of());
        when(service.findOutstandingPage(filter, 0, 50, List.of())).thenReturn(List.of());

        assertEquals(1, outstanding.getDataProvider().size(new Query<>()));
        outstanding.getDataProvider().fetch(new Query<>(50, 50, List.of(), null, null)).toList();
        view.onHiringActionSucceeded();
        assertEquals(0, outstanding.getDataProvider().size(new Query<>()));
        outstanding.getDataProvider().fetch(new Query<>(50, 50, List.of(), null, null)).toList();
        outstanding.getDataProvider().fetch(new Query<>(0, 50, List.of(), null, null)).toList();

        verify(service, org.mockito.Mockito.times(2)).countOutstanding(filter);
        verify(service, org.mockito.Mockito.times(2)).findOutstandingPage(filter, 50, 50, List.of());
        verify(service).findOutstandingPage(filter, 0, 50, List.of());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Query query(int offset, int limit, String key) {
        return new Query(offset, limit, List.of(new QuerySortOrder(key, SortDirection.ASCENDING)), null, null);
    }

    private TextField filter(HiringDecisionView view) {
        return descendants(view).filter(TextField.class::isInstance).map(TextField.class::cast)
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private List<Grid<?>> grids(HiringDecisionView view) {
        return descendants(view).filter(Grid.class::isInstance)
                .map(component -> (Grid<?>) component)
                .collect(java.util.stream.Collectors.toList());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void sortAscending(Grid<?> grid, String key) {
        Grid raw = grid;
        raw.sort(GridSortOrder.asc(raw.getColumnByKey(key)).build());
    }

    private Stream<String> spanTexts(HiringDecisionView view) {
        return descendants(view).filter(Span.class::isInstance).map(Span.class::cast).map(Span::getText);
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
