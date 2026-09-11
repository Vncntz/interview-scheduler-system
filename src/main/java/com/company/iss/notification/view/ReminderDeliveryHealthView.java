package com.company.iss.notification.view;

import com.company.iss.notification.dto.ReminderDeliveryHealthFilter;
import com.company.iss.notification.dto.ReminderDeliveryHealthIndicator;
import com.company.iss.notification.dto.ReminderDeliveryHealthItem;
import com.company.iss.notification.dto.ReminderDeliveryHealthMetadata;
import com.company.iss.notification.entity.InterviewReminderDeliveryStatus;
import com.company.iss.notification.entity.InterviewReminderType;
import com.company.iss.notification.service.ReminderDeliveryHealthService;
import com.company.iss.shared.view.MainLayout;
import com.company.iss.shared.view.UserSafeNotifier;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Route(value = "reminder-delivery-health", layout = MainLayout.class)
@PageTitle("Reminder Delivery Health")
@RolesAllowed("ADMIN")
public class ReminderDeliveryHealthView extends VerticalLayout {

    private static final int GRID_PAGE_SIZE = 50;
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern(
            "MMM d, uuuu h:mm a", Locale.ENGLISH
    );

    private final ReminderDeliveryHealthService healthService;
    private final Clock clock;
    private final ComboBox<InterviewReminderDeliveryStatus> statusFilter = new ComboBox<>("Delivery status");
    private final ComboBox<InterviewReminderType> typeFilter = new ComboBox<>("Reminder type");
    private final DatePicker dateFromFilter = new DatePicker("Appointment date from");
    private final DatePicker dateThroughFilter = new DatePicker("Appointment date through");
    private final Button refreshButton = new Button("Refresh", VaadinIcon.REFRESH.create());
    private final Span timezoneText = new Span("Business timezone unavailable.");
    private final Span schedulerText = new Span("Reminder scheduler configuration unavailable.");
    private final Span loadState = new Span("Loading reminder deliveries...");
    private final Span refreshedAt = new Span("Last refreshed: unavailable");
    private final Grid<ReminderDeliveryHealthItem> grid = new Grid<>();
    private final CallbackDataProvider<ReminderDeliveryHealthItem, Void> dataProvider;

    private ZoneId businessZone;
    private boolean pageLoaded;
    private boolean countLoaded;
    private boolean loadFailed;
    private long matchingCount;

    public ReminderDeliveryHealthView(ReminderDeliveryHealthService healthService, Clock clock) {
        this.healthService = healthService;
        this.clock = clock;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        configureFilters();
        configureGrid();
        dataProvider = DataProvider.fromCallbacks(
                query -> fetchSafely(query.getOffset(), query.getLimit()),
                query -> countSafely()
        );
        grid.setDataProvider(dataProvider);
        add(createHeader(), createFilters(), loadState, grid);
    }

    @PostConstruct
    void initialize() {
        refreshData();
    }

    private void refreshData() {
        try {
            ReminderDeliveryHealthMetadata metadata = healthService.getMetadata();
            businessZone = metadata.businessZone();
            timezoneText.setText(
                    "Appointment dates and times use " + businessZone
                            + ". Date filters include the full selected dates in this timezone."
            );
            schedulerText.setText(
                    "Reminder scheduler: " + (metadata.reminderSchedulerEnabled() ? "enabled" : "disabled")
                            + "; configured attempt limit: " + metadata.maxAttempts() + "."
            );
            beginProviderRefresh();
        } catch (RuntimeException exception) {
            markLoadFailure(exception);
        }
    }

    private Component createHeader() {
        H1 title = new H1("Reminder Delivery Health");
        title.getStyle().set("margin", "0");
        Paragraph description = new Paragraph(
                "Review persisted reminder outcomes and evidence-based delivery health. "
                        + "A Sent status means SMTP accepted the message; inbox delivery is not confirmed."
        );
        description.getStyle().set("margin", "0");
        VerticalLayout copy = new VerticalLayout(title, description, timezoneText, schedulerText, refreshedAt);
        copy.setPadding(false);
        copy.setSpacing(false);
        refreshButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        refreshButton.addClickListener(event -> refreshData());
        HorizontalLayout header = new HorizontalLayout(copy, refreshButton);
        header.setWidthFull();
        header.expand(copy);
        header.setAlignItems(Alignment.CENTER);
        return header;
    }

    private Component createFilters() {
        HorizontalLayout filters = new HorizontalLayout(
                statusFilter, typeFilter, dateFromFilter, dateThroughFilter
        );
        filters.setWidthFull();
        filters.setWrap(true);
        filters.setAlignItems(Alignment.END);
        return filters;
    }

    private void configureFilters() {
        statusFilter.setItems(InterviewReminderDeliveryStatus.values());
        statusFilter.setItemLabelGenerator(this::displayEnum);
        statusFilter.setClearButtonVisible(true);
        statusFilter.setPlaceholder("All statuses");
        statusFilter.addValueChangeListener(event -> beginProviderRefreshIfReady());

        typeFilter.setItems(InterviewReminderType.values());
        typeFilter.setItemLabelGenerator(this::displayReminderType);
        typeFilter.setClearButtonVisible(true);
        typeFilter.setPlaceholder("All reminder types");
        typeFilter.addValueChangeListener(event -> beginProviderRefreshIfReady());

        dateFromFilter.setClearButtonVisible(true);
        dateThroughFilter.setClearButtonVisible(true);
        dateFromFilter.addValueChangeListener(event -> {
            dateThroughFilter.setMin(event.getValue());
            beginProviderRefreshIfReady();
        });
        dateThroughFilter.addValueChangeListener(event -> {
            dateFromFilter.setMax(event.getValue());
            beginProviderRefreshIfReady();
        });
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.setPageSize(GRID_PAGE_SIZE);
        grid.addThemeVariants(
                GridVariant.LUMO_ROW_STRIPES,
                GridVariant.LUMO_COLUMN_BORDERS,
                GridVariant.LUMO_COMPACT
        );
        grid.addColumn(ReminderDeliveryHealthItem::bookingReference)
                .setHeader("Booking reference").setKey("booking-reference").setWidth("180px").setResizable(true);
        grid.addColumn(item -> displayReminderType(item.reminderType()))
                .setHeader("Reminder type").setKey("reminder-type").setWidth("140px").setResizable(true);
        grid.addColumn(item -> formatScheduled(item.scheduledStartAt()))
                .setHeader("Scheduled appointment").setKey("scheduled-appointment")
                .setWidth("220px").setResizable(true);
        grid.addColumn(item -> displayEnum(item.persistedStatus()))
                .setHeader("Persisted status").setKey("persisted-status").setWidth("140px").setResizable(true);
        grid.addColumn(ReminderDeliveryHealthItem::attemptCount)
                .setHeader("Attempts").setKey("attempt-count").setWidth("100px").setResizable(true);
        grid.addColumn(item -> formatUtc(item.claimedAtUtc()))
                .setHeader("Claimed at").setKey("claimed-at").setWidth("220px").setResizable(true);
        grid.addColumn(item -> formatUtc(item.nextAttemptAtUtc()))
                .setHeader("Next attempt").setKey("next-attempt-at").setWidth("220px").setResizable(true);
        grid.addColumn(item -> formatUtc(item.sentAtUtc()))
                .setHeader("Sent at").setKey("sent-at").setWidth("220px").setResizable(true);
        grid.addColumn(ReminderDeliveryHealthItem::safeStatusReason)
                .setHeader("Status reason").setKey("status-reason").setWidth("320px").setResizable(true);
        grid.addColumn(this::displayIndicators)
                .setHeader("Health indicators").setKey("health-indicators").setWidth("300px").setResizable(true);
    }

    private Stream<ReminderDeliveryHealthItem> fetchSafely(int offset, int limit) {
        try {
            List<ReminderDeliveryHealthItem> page = healthService.findPage(currentFilter(), offset, limit);
            pageLoaded = true;
            updateSuccessfulLoadState();
            return page.stream();
        } catch (RuntimeException exception) {
            markLoadFailure(exception);
            return Stream.empty();
        }
    }

    private int countSafely() {
        try {
            matchingCount = healthService.count(currentFilter());
            countLoaded = true;
            updateSuccessfulLoadState();
            return matchingCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) matchingCount;
        } catch (RuntimeException exception) {
            markLoadFailure(exception);
            return 0;
        }
    }

    private ReminderDeliveryHealthFilter currentFilter() {
        return new ReminderDeliveryHealthFilter(
                statusFilter.getValue(),
                typeFilter.getValue(),
                dateFromFilter.getValue(),
                dateThroughFilter.getValue()
        );
    }

    private void beginProviderRefreshIfReady() {
        if (businessZone != null) {
            beginProviderRefresh();
        }
    }

    private void beginProviderRefresh() {
        pageLoaded = false;
        countLoaded = false;
        loadFailed = false;
        loadState.setText("Loading reminder deliveries...");
        refreshButton.setEnabled(false);
        grid.deselectAll();
        dataProvider.refreshAll();
    }

    private void updateSuccessfulLoadState() {
        if (loadFailed || !countLoaded || (!pageLoaded && matchingCount != 0)) {
            return;
        }
        loadState.setText(matchingCount == 0
                ? "No reminder deliveries match the selected filters."
                : matchingCount + " reminder deliveries match the selected filters.");
        refreshedAt.setText("Last refreshed: " + formatRefreshInstant(clock.instant()));
        refreshButton.setEnabled(true);
    }

    private void markLoadFailure(RuntimeException exception) {
        loadFailed = true;
        loadState.setText("Reminder delivery data could not be loaded. Use Refresh to try again.");
        refreshButton.setEnabled(true);
        if (UI.getCurrent() != null) {
            UserSafeNotifier.showError(exception);
        }
    }

    private String formatScheduled(LocalDateTime value) {
        if (value == null || businessZone == null) {
            return "Unavailable";
        }
        return DATE_TIME_FORMAT.format(value) + " " + businessZone;
    }

    private String formatUtc(LocalDateTime value) {
        if (value == null || businessZone == null) {
            return "Unavailable";
        }
        return DATE_TIME_FORMAT.format(
                value.atZone(ZoneOffset.UTC).withZoneSameInstant(businessZone)
        ) + " " + businessZone;
    }

    private String formatRefreshInstant(Instant value) {
        if (businessZone == null) {
            return "unavailable";
        }
        return DATE_TIME_FORMAT.format(value.atZone(businessZone)) + " " + businessZone;
    }

    private String displayIndicators(ReminderDeliveryHealthItem item) {
        if (item.healthIndicators().isEmpty()) {
            return "None supported by stored evidence";
        }
        return item.healthIndicators().stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(ReminderDeliveryHealthIndicator::getDisplayName)
                .collect(Collectors.joining(", "));
    }

    private String displayReminderType(InterviewReminderType type) {
        return type == null ? "Unavailable" : switch (type) {
            case REMINDER_24H -> "24-hour";
            case REMINDER_2H -> "2-hour";
        };
    }

    private String displayEnum(Enum<?> value) {
        if (value == null) {
            return "Unavailable";
        }
        String normalized = value.name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
