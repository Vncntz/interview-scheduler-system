package com.company.iss.dashboard.view;

import com.vaadin.flow.component.icon.Icon;

import com.company.iss.dashboard.dto.DashboardMetrics;
import com.company.iss.dashboard.service.DashboardService;
import com.company.iss.shared.view.MainLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Dashboard")
@RolesAllowed("ADMIN")
public class DashboardView extends VerticalLayout {

    @Autowired
    private DashboardService dashboardService;

    private HorizontalLayout metricsLayout;

    public DashboardView() {
        setSizeFull();

        H2 title = new H2("Dashboard");

        metricsLayout = new HorizontalLayout();
        metricsLayout.setWidthFull();
        metricsLayout.setSpacing(true);

        add(title, metricsLayout);
Icon icon = new Icon("vaadin:user");
add(icon);
    }

    @PostConstruct
    private void init() {
        DashboardMetrics metrics = dashboardService.getMetrics();

        metricsLayout.removeAll();

        metricsLayout.add(createMetricCard("Applicants", metrics.getTotalApplicants()), createMetricCard("Open Positions", metrics.getOpenPositions()), createMetricCard("Today's Interviews", metrics.getTodaysInterviews()), createMetricCard("Booked", metrics.getBookedInterviews()), createMetricCard("Passed", metrics.getPassedApplicants()), createMetricCard("Failed", metrics.getFailedApplicants()), createMetricCard("No Shows", metrics.getNoShows()));
    }

    private VerticalLayout createMetricCard(String label, Long value) {
        Span valueLabel = new Span(String.valueOf(value));

        valueLabel.getStyle().set("font-size", "2rem").set("font-weight", "700");

        Span textLabel = new Span(label);

        textLabel.getStyle().set("font-size", "0.9rem").set("color", "var(--lumo-secondary-text-color)");

        VerticalLayout card = new VerticalLayout(valueLabel, textLabel);

        card.setPadding(true);
        card.setSpacing(false);
        card.setWidth("180px");

        card.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)").set("border-radius", "12px");

        return card;
    }
}
