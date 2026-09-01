package com.company.iss.schedule.view;

import com.company.iss.branch.service.BranchService;
import com.company.iss.recruiter.service.RecruiterService;
import com.company.iss.schedule.dto.ScheduleGridFilter;
import com.company.iss.schedule.dto.ScheduleGridSort;
import com.company.iss.schedule.dto.ScheduleGridSortOrder;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.service.ScheduleService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.provider.QuerySortOrder;
import com.vaadin.flow.data.provider.SortDirection;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleViewTest {

    @Test
    void providerUsesFiftyRowsAndForwardsNonAlignedOffsetFilterAndSort() {
        ScheduleService service = mock(ScheduleService.class);
        ScheduleView view = view(service);
        Grid<Schedule> grid = grid(view);
        TextField keyword = keyword(view);
        keyword.setValue("  Online  ");
        List<ScheduleGridSortOrder> expectedSort = List.of(
                new ScheduleGridSortOrder(ScheduleGridSort.DATE, Sort.Direction.DESC)
        );
        when(service.findGridPage(new ScheduleGridFilter("online"), 25, 10, expectedSort))
                .thenReturn(List.of());

        grid.getDataProvider().fetch(new Query<>(
                25, 10, List.of(new QuerySortOrder("date", SortDirection.DESCENDING)), null, null
        )).toList();

        assertEquals(50, grid.getPageSize());
        assertInstanceOf(CallbackDataProvider.class, grid.getDataProvider());
        assertTrue(keyword.isClearButtonVisible());
        verify(service).findGridPage(new ScheduleGridFilter("online"), 25, 10, expectedSort);
    }

    @Test
    void searchRefreshRetainsProviderAndClearsSelection() {
        ScheduleView view = view(mock(ScheduleService.class));
        Grid<Schedule> grid = grid(view);
        var provider = grid.getDataProvider();
        Schedule stale = new Schedule();
        stale.setId(1L);
        grid.select(stale);

        keyword(view).setValue("branch");

        assertSame(provider, grid.getDataProvider());
        assertTrue(grid.asSingleSelect().isEmpty());
    }

    @Test
    void countSaturatesAtVaadinIntegerLimit() {
        ScheduleService service = mock(ScheduleService.class);
        ScheduleView view = view(service);
        when(service.countGrid(ScheduleGridFilter.empty())).thenReturn((long) Integer.MAX_VALUE + 1);

        int size = grid(view).getDataProvider().size(new Query<>(0, 50, List.of(), null, null));

        assertEquals(Integer.MAX_VALUE, size);
    }

    private ScheduleView view(ScheduleService service) {
        return new ScheduleView(service, mock(BranchService.class), mock(RecruiterService.class));
    }

    @SuppressWarnings("unchecked")
    private Grid<Schedule> grid(ScheduleView view) {
        return (Grid<Schedule>) descendants(view)
                .filter(Grid.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    private TextField keyword(ScheduleView view) {
        return descendants(view)
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
