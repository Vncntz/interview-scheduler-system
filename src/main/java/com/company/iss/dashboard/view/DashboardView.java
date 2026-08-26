package com.company.iss.dashboard.view;

import com.company.iss.dashboard.component.DashboardChart;
import com.company.iss.dashboard.dto.DashboardMetrics;
import com.company.iss.dashboard.dto.InterviewActivity;
import com.company.iss.dashboard.dto.ScheduleSummary;
import com.company.iss.dashboard.dto.UpcomingInterview;
import com.company.iss.dashboard.service.DashboardService;
import com.company.iss.shared.util.DateTimeUtil;
import com.company.iss.shared.view.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "dashboard", layout = MainLayout.class)
@PageTitle("Dashboard")
@RolesAllowed("ADMIN")
public class DashboardView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter ACTIVITY_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d");

    @Autowired
    private DashboardService dashboardService;

    public DashboardView() {
        setSizeFull();
        setPadding(true);
        setSpacing(false);
        setMaxWidth("1600px");
        getStyle()
                .set("gap", "var(--vaadin-gap-l)")
                .set("overflow", "auto")
                .set("margin", "0 auto");
    }

    @PostConstruct
    private void init() {
        DashboardMetrics metrics = dashboardService.getMetrics();
        List<UpcomingInterview> upcomingInterviews = dashboardService.getUpcomingInterviews();
        List<ScheduleSummary> todaysSchedule = dashboardService.getTodaysSchedule();
        List<InterviewActivity> interviewActivity = dashboardService.getInterviewActivity();

        add(
                createHeader(),
                createMetrics(metrics),
                createCharts(metrics, interviewActivity),
                createOperationalContent(upcomingInterviews, todaysSchedule)
        );
    }

    private Component createHeader() {
        H2 title = new H2("Dashboard");
        title.getStyle().set("margin", "0");

        Paragraph description = new Paragraph("Overview of recruitment activity, interviews, and applicant outcomes");
        description.getStyle()
                .set("color", "var(--vaadin-text-color-secondary)")
                .set("margin", "var(--vaadin-gap-xs) 0 0");

        VerticalLayout header = new VerticalLayout(title, description);
        header.setPadding(false);
        header.setSpacing(false);
        return header;
    }

    private Component createMetrics(DashboardMetrics metrics) {
        Div metricsGrid = createResponsiveGrid("15rem", "var(--vaadin-gap-m)");
        metricsGrid.add(
                createMetricCard("Applicants", "Total applicants", metrics.getTotalApplicants(), VaadinIcon.USERS),
                createMetricCard("Open Positions", "Active openings", metrics.getOpenPositions(), VaadinIcon.BRIEFCASE),
                createMetricCard("Today's Interviews", "Active schedules today", metrics.getTodaysInterviews(), VaadinIcon.CALENDAR_CLOCK),
                createMetricCard("Booked", "Awaiting interview", metrics.getBookedInterviews(), VaadinIcon.CHECK_CIRCLE),
                createMetricCard("Passed", "Successful applicants", metrics.getPassedApplicants(), VaadinIcon.CHECK),
                createMetricCard("Failed", "Unsuccessful applicants", metrics.getFailedApplicants(), VaadinIcon.CLOSE),
                createMetricCard("No Shows", "Missed interviews", metrics.getNoShows(), VaadinIcon.WARNING)
        );
        return metricsGrid;
    }

    private Component createMetricCard(String label, String supportingText, Long value, VaadinIcon iconType) {
        Icon icon = iconType.create();
        icon.setSize("var(--vaadin-icon-size, 1.25rem)");
        icon.getStyle().set("color", "var(--aura-accent-color)");

        Span labelText = new Span(label);
        labelText.getStyle().set("font-weight", "600");

        HorizontalLayout heading = new HorizontalLayout(icon, labelText);
        heading.setAlignItems(FlexComponent.Alignment.CENTER);
        heading.setPadding(false);

        Span valueText = new Span(String.valueOf(value));
        valueText.getStyle()
                .set("font-size", "calc(var(--aura-font-size-xl) * 1.6)")
                .set("font-weight", "700")
                .set("line-height", "1.1");

        Span detailText = new Span(supportingText);
        detailText.getStyle()
                .set("color", "var(--vaadin-text-color-secondary)")
                .set("font-size", "var(--aura-font-size-s)");

        VerticalLayout card = new VerticalLayout(heading, valueText, detailText);
        styleCard(card);
        card.setMinHeight("9rem");
        card.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return card;
    }

    private Component createCharts(DashboardMetrics metrics, List<InterviewActivity> activity) {
        Div chartsGrid = createResponsiveGrid("30rem", "var(--vaadin-gap-l)");
        chartsGrid.getStyle().set("align-items", "stretch");
        chartsGrid.add(createOutcomesChartSection(metrics), createActivityChartSection(activity));
        return chartsGrid;
    }

    private Component createOutcomesChartSection(DashboardMetrics metrics) {
        List<Long> values = List.of(
                metrics.getBookedInterviews(),
                metrics.getPassedApplicants(),
                metrics.getFailedApplicants(),
                metrics.getNoShows()
        );

        Component content = values.stream().allMatch(value -> value == 0)
                ? createEmptyState(VaadinIcon.BAR_CHART, "No outcome data available yet.")
                : new DashboardChart(
                        "bar",
                        List.of("Booked", "Passed", "Failed", "No Shows"),
                        values,
                        "Current booking and applicant outcome counts"
                );

        return createSection("Applicant Outcomes", "Current booking and applicant outcome counts", null, content);
    }

    private Component createActivityChartSection(List<InterviewActivity> activity) {
        List<Long> values = activity.stream()
                .map(InterviewActivity::scheduledSchedules)
                .toList();

        Component content = values.stream().allMatch(value -> value == 0)
                ? createEmptyState(VaadinIcon.BAR_CHART, "No interview activity scheduled for the next seven days.")
                : new DashboardChart(
                        "bar",
                        activity.stream()
                                .map(item -> item.date().format(ACTIVITY_DATE_FORMAT))
                                .toList(),
                        values,
                        "Active interview schedules during the next seven days"
                );

        return createSection("Interview Activity", "Active interview schedules for the next seven days", null, content);
    }

    private Component createOperationalContent(List<UpcomingInterview> upcomingInterviews,
                                               List<ScheduleSummary> todaysSchedule) {
        Div operationalGrid = createResponsiveGrid("42rem", "var(--vaadin-gap-l)");
        operationalGrid.getStyle().set("align-items", "start");
        operationalGrid.add(
                createUpcomingInterviewsSection(upcomingInterviews),
                createTodaysScheduleSection(todaysSchedule)
        );
        return operationalGrid;
    }

    private Component createUpcomingInterviewsSection(List<UpcomingInterview> interviews) {
        Button viewScheduling = new Button("View Scheduling", VaadinIcon.ARROW_RIGHT.create());
        viewScheduling.setIconAfterText(true);
        viewScheduling.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        viewScheduling.addClickListener(event -> UI.getCurrent().navigate("scheduling"));

        VerticalLayout content = createSectionContent();
        if (interviews.isEmpty()) {
            content.add(createEmptyState(VaadinIcon.CALENDAR, "No upcoming interviews."));
        } else {
            Grid<UpcomingInterview> grid = new Grid<>();
            grid.setItems(interviews);
            grid.addColumn(interview -> interview.date().format(DATE_FORMAT)).setHeader("Date").setAutoWidth(true);
            grid.addColumn(interview -> DateTimeUtil.formatTime(interview.time())).setHeader("Time").setAutoWidth(true);
            grid.addColumn(UpcomingInterview::applicant).setHeader("Applicant").setAutoWidth(true).setFlexGrow(1);
            grid.addColumn(UpcomingInterview::position).setHeader("Position").setAutoWidth(true).setFlexGrow(1);
            grid.addColumn(UpcomingInterview::recruiter).setHeader("Recruiter").setAutoWidth(true);
            grid.addColumn(UpcomingInterview::branch).setHeader("Branch").setAutoWidth(true);
            grid.addColumn(interview -> formatEnum(interview.mode().name())).setHeader("Mode").setAutoWidth(true);
            grid.addColumn(interview -> formatEnum(interview.status().name())).setHeader("Status").setAutoWidth(true);
            grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
            grid.setAllRowsVisible(true);
            grid.setWidthFull();
            content.add(grid);
        }
        return createSection("Upcoming Interviews", "Next five active interview bookings", viewScheduling, content);
    }

    private Component createTodaysScheduleSection(List<ScheduleSummary> schedules) {
        VerticalLayout content = createSectionContent();
        if (schedules.isEmpty()) {
            content.add(createEmptyState(VaadinIcon.CLOCK, "No interviews scheduled for today."));
        } else {
            schedules.forEach(schedule -> content.add(createScheduleRow(schedule)));
        }
        return createSection("Today's Slot Utilization", "Booked capacity across today's schedule", null, content);
    }

    private Component createScheduleRow(ScheduleSummary schedule) {
        Span time = new Span(DateTimeUtil.formatTime(schedule.startTime()));
        time.getStyle().set("font-weight", "600");

        Span details = new Span(formatEnum(schedule.mode().name()) + " - " + schedule.branch());
        details.getStyle().set("color", "var(--vaadin-text-color-secondary)")
                .set("font-size", "var(--aura-font-size-s)");

        Span status = new Span(formatEnum(schedule.status().name()));
        status.getStyle().set("background", "var(--vaadin-background-container)")
                .set("border-radius", "var(--vaadin-radius-l)")
                .set("font-size", "var(--aura-font-size-xs)")
                .set("font-weight", "600")
                .set("padding", "var(--vaadin-padding-xs) var(--vaadin-padding-s)");

        HorizontalLayout heading = new HorizontalLayout(time, status);
        heading.setWidthFull();
        heading.setAlignItems(FlexComponent.Alignment.CENTER);
        heading.expand(time);

        double utilization = schedule.capacity() == 0
                ? 0
                : Math.min(1, (double) schedule.bookedCount() / schedule.capacity());
        ProgressBar progressBar = new ProgressBar(0, 1, utilization);
        progressBar.setWidthFull();
        progressBar.getElement().setAttribute(
                "aria-label",
                schedule.bookedCount() + " of " + schedule.capacity() + " slots booked"
        );

        Span capacity = new Span(
                schedule.bookedCount() + " of " + schedule.capacity() + " booked - "
                        + Math.round(utilization * 100) + "% utilized"
        );
        capacity.getStyle().set("color", "var(--vaadin-text-color-secondary)")
                .set("font-size", "var(--aura-font-size-s)");

        VerticalLayout row = new VerticalLayout(heading, details, progressBar, capacity);
        row.setWidthFull();
        row.setPadding(false);
        row.setSpacing(false);
        row.getStyle().set("gap", "var(--vaadin-gap-xs)")
                .set("padding", "var(--vaadin-padding-s) 0")
                .set("border-bottom", "1px solid var(--vaadin-border-color-secondary)");
        return row;
    }

    private Component createSection(String title, String description, Component action, Component content) {
        H3 heading = new H3(title);
        heading.getStyle().set("margin", "0");

        Span supportingText = new Span(description);
        supportingText.getStyle().set("color", "var(--vaadin-text-color-secondary)")
                .set("font-size", "var(--aura-font-size-s)");

        VerticalLayout headingText = new VerticalLayout(heading, supportingText);
        headingText.setPadding(false);
        headingText.setSpacing(false);

        HorizontalLayout sectionHeader = new HorizontalLayout(headingText);
        sectionHeader.setWidthFull();
        sectionHeader.setAlignItems(FlexComponent.Alignment.CENTER);
        sectionHeader.expand(headingText);
        if (action != null) {
            sectionHeader.add(action);
        }

        VerticalLayout section = new VerticalLayout(sectionHeader, content);
        styleCard(section);
        return section;
    }

    private Component createEmptyState(VaadinIcon iconType, String message) {
        Icon icon = iconType.create();
        icon.setSize("calc(var(--vaadin-icon-size, 1.25rem) * 1.5)");
        icon.getStyle().set("color", "var(--vaadin-text-color-secondary)");

        Span text = new Span(message);
        text.getStyle().set("color", "var(--vaadin-text-color-secondary)");

        VerticalLayout emptyState = new VerticalLayout(icon, text);
        emptyState.setAlignItems(FlexComponent.Alignment.CENTER);
        emptyState.getStyle().set("padding", "var(--vaadin-padding-xl) var(--vaadin-padding-m)");
        return emptyState;
    }

    private Div createResponsiveGrid(String minimumWidth, String gap) {
        Div grid = new Div();
        grid.setWidthFull();
        grid.getStyle().set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(min(100%, " + minimumWidth + "), 1fr))")
                .set("gap", gap);
        return grid;
    }

    private VerticalLayout createSectionContent() {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(false);
        return content;
    }

    private void styleCard(VerticalLayout card) {
        card.setPadding(true);
        card.setSpacing(true);
        card.setWidthFull();
        card.getStyle().set("background", "var(--aura-surface-color)")
                .set("border", "1px solid var(--vaadin-border-color-secondary)")
                .set("border-radius", "var(--vaadin-radius-l)")
                .set("box-sizing", "border-box");
    }

    private String formatEnum(String value) {
        String normalized = value.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
