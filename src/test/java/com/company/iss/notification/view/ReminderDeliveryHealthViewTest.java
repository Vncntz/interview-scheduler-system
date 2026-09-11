package com.company.iss.notification.view;

import com.company.iss.notification.dto.ReminderDeliveryHealthFilter;
import com.company.iss.notification.dto.ReminderDeliveryHealthMetadata;
import com.company.iss.notification.entity.InterviewReminderDeliveryStatus;
import com.company.iss.notification.entity.InterviewReminderType;
import com.company.iss.notification.service.ReminderDeliveryHealthService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderDeliveryHealthViewTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC
    );

    @Test
    void routeIsAdministratorOnlyAndGridExposesReadOnlyMonitoringColumns() {
        ReminderDeliveryHealthView view = view(mock(ReminderDeliveryHealthService.class));
        Grid<?> grid = grid(view);

        assertEquals("reminder-delivery-health",
                ReminderDeliveryHealthView.class.getAnnotation(Route.class).value());
        assertEquals("Reminder Delivery Health",
                ReminderDeliveryHealthView.class.getAnnotation(PageTitle.class).value());
        assertEquals(List.of("ADMIN"),
                List.of(ReminderDeliveryHealthView.class.getAnnotation(RolesAllowed.class).value()));
        assertEquals(50, grid.getPageSize());
        assertInstanceOf(CallbackDataProvider.class, grid.getDataProvider());
        assertNotNull(grid.getColumnByKey("booking-reference"));
        assertNotNull(grid.getColumnByKey("reminder-type"));
        assertNotNull(grid.getColumnByKey("scheduled-appointment"));
        assertNotNull(grid.getColumnByKey("persisted-status"));
        assertNotNull(grid.getColumnByKey("attempt-count"));
        assertNotNull(grid.getColumnByKey("claimed-at"));
        assertNotNull(grid.getColumnByKey("next-attempt-at"));
        assertNotNull(grid.getColumnByKey("sent-at"));
        assertNotNull(grid.getColumnByKey("status-reason"));
        assertNotNull(grid.getColumnByKey("health-indicators"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtersDriveExactLazyCallbacksAndSurviveManualRefresh() {
        ReminderDeliveryHealthService service = mock(ReminderDeliveryHealthService.class);
        when(service.getMetadata()).thenReturn(metadata());
        ReminderDeliveryHealthView view = view(service);
        view.initialize();
        ComboBox<InterviewReminderDeliveryStatus> status = (ComboBox<InterviewReminderDeliveryStatus>)
                comboBox(view, "Delivery status");
        ComboBox<InterviewReminderType> type = (ComboBox<InterviewReminderType>)
                comboBox(view, "Reminder type");
        List<DatePicker> dates = descendants(view).filter(DatePicker.class::isInstance)
                .map(DatePicker.class::cast).toList();
        status.setValue(InterviewReminderDeliveryStatus.FAILED);
        type.setValue(InterviewReminderType.REMINDER_2H);
        dates.get(0).setValue(LocalDate.of(2026, 9, 2));
        dates.get(1).setValue(LocalDate.of(2026, 9, 3));
        ReminderDeliveryHealthFilter expected = new ReminderDeliveryHealthFilter(
                InterviewReminderDeliveryStatus.FAILED,
                InterviewReminderType.REMINDER_2H,
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 3)
        );
        when(service.findPage(expected, 25, 10)).thenReturn(List.of());
        when(service.count(expected)).thenReturn(0L);

        button(view, "Refresh").click();
        Grid<?> grid = grid(view);
        grid.getDataProvider().fetch(new Query<>(25, 10, List.of(), null, null)).toList();
        grid.getDataProvider().size(new Query<>());

        assertEquals(InterviewReminderDeliveryStatus.FAILED, status.getValue());
        assertEquals(InterviewReminderType.REMINDER_2H, type.getValue());
        assertEquals(LocalDate.of(2026, 9, 2), dates.get(0).getValue());
        assertEquals(LocalDate.of(2026, 9, 3), dates.get(1).getValue());
        verify(service).findPage(expected, 25, 10);
        verify(service).count(expected);
    }

    @Test
    void emptyCountCompletesLoadAndRecordsSuccessfulRefreshWithoutFetch() {
        ReminderDeliveryHealthService service = mock(ReminderDeliveryHealthService.class);
        when(service.getMetadata()).thenReturn(metadata());
        when(service.count(ReminderDeliveryHealthFilter.empty())).thenReturn(0L);
        ReminderDeliveryHealthView view = view(service);
        view.initialize();

        grid(view).getDataProvider().size(new Query<>());

        assertTrue(spanTexts(view).anyMatch(text -> text.equals(
                "No reminder deliveries match the selected filters."
        )));
        assertTrue(spanTexts(view).anyMatch(text -> text.startsWith("Last refreshed: Sep 1, 2026 8:00 AM")));
        assertTrue(button(view, "Refresh").isEnabled());
    }

    @Test
    void failedLoadShowsSafeStateAndDoesNotAdvanceSuccessfulRefreshTime() {
        ReminderDeliveryHealthService service = mock(ReminderDeliveryHealthService.class);
        when(service.getMetadata()).thenReturn(metadata());
        when(service.count(ReminderDeliveryHealthFilter.empty()))
                .thenReturn(0L)
                .thenThrow(new IllegalStateException("smtp-password=secret recipient@example.test"));
        ReminderDeliveryHealthView view = view(service);
        view.initialize();
        grid(view).getDataProvider().size(new Query<>());
        String successfulRefresh = spanTexts(view)
                .filter(text -> text.startsWith("Last refreshed:"))
                .findFirst().orElseThrow();

        button(view, "Refresh").click();
        grid(view).getDataProvider().size(new Query<>());

        List<String> text = spanTexts(view).toList();
        assertTrue(text.contains("Reminder delivery data could not be loaded. Use Refresh to try again."));
        assertFalse(text.stream().anyMatch(value -> value.contains("secret") || value.contains("example.test")));
        assertTrue(text.contains(successfulRefresh));
    }

    @Test
    void utcDeliveryTimestampsAreConvertedToTheConfiguredBusinessZone() throws Exception {
        ReminderDeliveryHealthService service = mock(ReminderDeliveryHealthService.class);
        when(service.getMetadata()).thenReturn(metadata());
        ReminderDeliveryHealthView view = view(service);
        view.initialize();
        Method formatUtc = ReminderDeliveryHealthView.class.getDeclaredMethod("formatUtc", LocalDateTime.class);
        formatUtc.setAccessible(true);

        assertEquals("Sep 1, 2026 8:00 AM Asia/Manila",
                formatUtc.invoke(view, LocalDateTime.of(2026, 9, 1, 0, 0)));
        assertEquals("Unavailable", formatUtc.invoke(view, new Object[]{null}));
    }

    private ReminderDeliveryHealthView view(ReminderDeliveryHealthService service) {
        return new ReminderDeliveryHealthView(service, CLOCK);
    }

    private ReminderDeliveryHealthMetadata metadata() {
        return new ReminderDeliveryHealthMetadata(ZoneId.of("Asia/Manila"), true, 3);
    }

    @SuppressWarnings("unchecked")
    private Grid<Object> grid(ReminderDeliveryHealthView view) {
        return (Grid<Object>) descendants(view).filter(Grid.class::isInstance).findFirst().orElseThrow();
    }

    private ComboBox<?> comboBox(ReminderDeliveryHealthView view, String label) {
        return descendants(view).filter(ComboBox.class::isInstance).map(ComboBox.class::cast)
                .filter(combo -> label.equals(combo.getLabel())).findFirst().orElseThrow();
    }

    private Button button(ReminderDeliveryHealthView view, String text) {
        return descendants(view).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private Stream<String> spanTexts(ReminderDeliveryHealthView view) {
        return descendants(view).filter(Span.class::isInstance).map(Span.class::cast).map(Span::getText);
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
