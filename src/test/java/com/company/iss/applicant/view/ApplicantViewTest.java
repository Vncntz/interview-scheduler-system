package com.company.iss.applicant.view;

import com.company.iss.applicant.dto.ApplicantGridFilter;
import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.service.ApplicantService;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.service.BookingService;
import com.company.iss.branch.service.BranchService;
import com.company.iss.position.service.PositionOpeningService;
import com.company.iss.schedule.service.ScheduleService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.Query;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicantViewTest {

    @Test
    void gridUsesFiftyRowCallbackPagesAndClearableDatabaseFilters() {
        ApplicantService applicantService = mock(ApplicantService.class);
        ApplicantView view = view(applicantService);
        Grid<Applicant> grid = grid(view);
        TextField keyword = descendants(view)
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .findFirst()
                .orElseThrow();
        ComboBox<ApplicantStatus> status = applicantStatusFilter(view);

        assertEquals(50, grid.getPageSize());
        assertInstanceOf(CallbackDataProvider.class, grid.getDataProvider());
        assertTrue(keyword.isClearButtonVisible());
        assertTrue(status.isClearButtonVisible());

        keyword.setValue("  ALEX  ");
        status.setValue(ApplicantStatus.SCREENING);
        when(applicantService.findGridPage(
                new ApplicantGridFilter("alex", ApplicantStatus.SCREENING), 0, 50
        )).thenReturn(List.of());

        grid.getDataProvider().fetch(new Query<>(0, 50, List.of(), null, null)).toList();

        verify(applicantService).findGridPage(
                new ApplicantGridFilter("alex", ApplicantStatus.SCREENING), 0, 50
        );
    }

    @Test
    void filterRefreshKeepsProviderAndClearsStaleSelection() {
        ApplicantView view = view(mock(ApplicantService.class));
        Grid<Applicant> grid = grid(view);
        var provider = grid.getDataProvider();
        Applicant stale = new Applicant();
        stale.setId(10L);
        grid.select(stale);

        applicantStatusFilter(view).setValue(ApplicantStatus.ON_HOLD);

        assertSame(provider, grid.getDataProvider());
        assertTrue(grid.asSingleSelect().isEmpty());
    }

    @Test
    void callbackCountSaturatesAtVaadinsIntegerLimit() {
        ApplicantService applicantService = mock(ApplicantService.class);
        ApplicantView view = view(applicantService);
        Grid<Applicant> grid = grid(view);
        when(applicantService.countGrid(ApplicantGridFilter.empty()))
                .thenReturn((long) Integer.MAX_VALUE + 1L);

        int size = grid.getDataProvider().size(new Query<>(0, 50, List.of(), null, null));

        assertEquals(Integer.MAX_VALUE, size);
    }

    @Test
    void profileActionIsAvailableWithoutRemovingExistingActions() {
        ApplicantView view = view(mock(ApplicantService.class));
        List<String> labels = descendants(view)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .map(Button::getText)
                .toList();

        assertTrue(labels.containsAll(List.of("Add", "Edit", "Book Schedule", "View Profile")));
    }

    private ApplicantView view(ApplicantService applicantService) {
        return new ApplicantView(
                applicantService,
                mock(BookingService.class),
                mock(ScheduleService.class),
                mock(PositionOpeningService.class),
                mock(SecurityService.class),
                mock(BranchService.class)
        );
    }

    @SuppressWarnings("unchecked")
    private Grid<Applicant> grid(ApplicantView view) {
        return (Grid<Applicant>) descendants(view)
                .filter(Grid.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private ComboBox<ApplicantStatus> applicantStatusFilter(ApplicantView view) {
        return (ComboBox<ApplicantStatus>) descendants(view)
                .filter(ComboBox.class::isInstance)
                .map(ComboBox.class::cast)
                .filter(comboBox -> "Applicant Status".equals(comboBox.getPlaceholder()))
                .findFirst()
                .orElseThrow();
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
