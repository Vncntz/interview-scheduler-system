package com.company.iss.booking.view;

import com.company.iss.booking.dto.BookingGridFilter;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.service.BookingService;
import com.company.iss.evaluation.service.InterviewEvaluationService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.Query;
import org.junit.jupiter.api.Test;

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

class BookingViewTest {

    @Test
    void gridUsesFiftyRowCallbacksAndForwardsAllClearableFilters() {
        BookingService bookingService = mock(BookingService.class);
        BookingView view = new BookingView(bookingService, mock(InterviewEvaluationService.class));
        Grid<Booking> grid = grid(view);
        TextField keyword = descendants(view)
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .findFirst()
                .orElseThrow();
        ComboBox<BookingStatus> status = bookingStatusFilter(view);
        DatePicker date = descendants(view)
                .filter(DatePicker.class::isInstance)
                .map(DatePicker.class::cast)
                .findFirst()
                .orElseThrow();
        LocalDate scheduleDate = LocalDate.of(2026, 9, 20);

        assertEquals(50, grid.getPageSize());
        assertInstanceOf(CallbackDataProvider.class, grid.getDataProvider());
        assertTrue(keyword.isClearButtonVisible());
        assertTrue(status.isClearButtonVisible());
        assertTrue(date.isClearButtonVisible());

        keyword.setValue(" Alex Candidate ");
        status.setValue(BookingStatus.CONFIRMED);
        date.setValue(scheduleDate);
        BookingGridFilter expected = new BookingGridFilter(
                "alex candidate", BookingStatus.CONFIRMED, scheduleDate
        );
        when(bookingService.findGridPage(expected, 0, 50)).thenReturn(List.of());

        grid.getDataProvider().fetch(new Query<>(0, 50, List.of(), null, null)).toList();

        verify(bookingService).findGridPage(expected, 0, 50);
    }

    @Test
    void filterRefreshKeepsProviderAndClearsStaleSelection() {
        BookingView view = new BookingView(
                mock(BookingService.class), mock(InterviewEvaluationService.class)
        );
        Grid<Booking> grid = grid(view);
        var provider = grid.getDataProvider();
        Booking stale = new Booking();
        stale.setId(20L);
        grid.select(stale);

        bookingStatusFilter(view).setValue(BookingStatus.NO_SHOW);

        assertSame(provider, grid.getDataProvider());
        assertTrue(grid.asSingleSelect().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private Grid<Booking> grid(BookingView view) {
        return (Grid<Booking>) descendants(view)
                .filter(Grid.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private ComboBox<BookingStatus> bookingStatusFilter(BookingView view) {
        return (ComboBox<BookingStatus>) descendants(view)
                .filter(ComboBox.class::isInstance)
                .map(ComboBox.class::cast)
                .filter(comboBox -> "Booking Status".equals(comboBox.getPlaceholder()))
                .findFirst()
                .orElseThrow();
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
