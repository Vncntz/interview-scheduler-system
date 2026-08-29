package com.company.iss.hiring.view;

import com.company.iss.hiring.dialog.HiringActionDialog;
import com.company.iss.hiring.dialog.HiringAuditDialog;
import com.company.iss.hiring.dialog.IssueOfferDialog;
import com.company.iss.hiring.dto.EligibleHiringCandidate;
import com.company.iss.hiring.dto.HiringActionCommand;
import com.company.iss.hiring.dto.HiringDecisionSummary;
import com.company.iss.hiring.service.HiringDecisionService;
import com.company.iss.shared.view.MainLayout;
import com.company.iss.shared.view.UserSafeNotifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Route(value = "hiring-decisions", layout = MainLayout.class)
@PageTitle("Final Hiring Decisions")
@RolesAllowed({"ADMIN", "RECRUITER"})
public class HiringDecisionView extends VerticalLayout {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final HiringDecisionService hiringDecisionService;
    private final Grid<EligibleHiringCandidate> eligibleGrid = new Grid<>();
    private final Grid<HiringDecisionSummary> outstandingGrid = new Grid<>();
    private final Grid<HiringDecisionSummary> completedGrid = new Grid<>();
    private final TextField filter = new TextField("Filter");

    private List<EligibleHiringCandidate> eligible = List.of();
    private List<HiringDecisionSummary> outstanding = List.of();
    private List<HiringDecisionSummary> completed = List.of();

    public HiringDecisionView(HiringDecisionService hiringDecisionService) {
        this.hiringDecisionService = hiringDecisionService;
        setSizeFull();
        setPadding(true);

        filter.setPlaceholder("Applicant, branch, position, or client");
        filter.setClearButtonVisible(true);
        filter.setWidth("420px");
        filter.addValueChangeListener(event -> applyFilter());

        configureEligibleGrid();
        configureOutstandingGrid();
        configureCompletedGrid();
        add(
                filter,
                new H2("Eligible passed candidates"),
                eligibleGrid,
                new H2("Outstanding offers"),
                outstandingGrid,
                new H2("Completed decisions and audit"),
                completedGrid
        );
        refresh();
    }

    private void configureEligibleGrid() {
        configureGrid(eligibleGrid);
        eligibleGrid.addColumn(EligibleHiringCandidate::applicantName).setHeader("Applicant").setAutoWidth(true);
        eligibleGrid.addColumn(EligibleHiringCandidate::branch).setHeader("Branch").setAutoWidth(true);
        eligibleGrid.addColumn(EligibleHiringCandidate::position).setHeader("Position").setAutoWidth(true);
        eligibleGrid.addColumn(EligibleHiringCandidate::client).setHeader("Client").setAutoWidth(true);
        eligibleGrid.addColumn(EligibleHiringCandidate::workLocation).setHeader("Work location").setAutoWidth(true);
        eligibleGrid.addColumn(row -> format(row.evaluatedAt())).setHeader("Evaluated").setAutoWidth(true);
        eligibleGrid.addComponentColumn(candidate -> {
            Button offer = new Button("Issue offer", event -> new IssueOfferDialog(
                    candidate,
                    hiringDecisionService,
                    this::refresh
            ).open());
            offer.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            return offer;
        }).setHeader("Action").setAutoWidth(true);
    }

    private void configureOutstandingGrid() {
        configureGrid(outstandingGrid);
        addDecisionColumns(outstandingGrid);
        outstandingGrid.addComponentColumn(decision -> {
            Button hired = new Button("Mark hired", event -> openAction(
                    "Confirm hire",
                    "Mark %s as hired for %s? This permanently consumes one headcount."
                            .formatted(decision.applicantName(), decision.position()),
                    "Mark hired",
                    false,
                    remarks -> hiringDecisionService.acceptAndHire(
                            new HiringActionCommand(decision.applicantId(), remarks)
                    )
            ));
            hired.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

            Button decline = new Button("Decline", event -> openAction(
                    "Decline offer",
                    "Record that %s declined the offer? This decision cannot be reversed."
                            .formatted(decision.applicantName()),
                    "Decline offer",
                    true,
                    remarks -> hiringDecisionService.decline(
                            new HiringActionCommand(decision.applicantId(), remarks)
                    )
            ));
            decline.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

            Button withdraw = new Button("Withdraw", event -> openAction(
                    "Withdraw offer",
                    "Withdraw the offer for %s? This decision cannot be reversed."
                            .formatted(decision.applicantName()),
                    "Withdraw offer",
                    true,
                    remarks -> hiringDecisionService.withdraw(
                            new HiringActionCommand(decision.applicantId(), remarks)
                    )
            ));
            withdraw.addThemeVariants(ButtonVariant.LUMO_SMALL);

            Button audit = auditButton(decision);
            return new HorizontalLayout(hired, decline, withdraw, audit);
        }).setHeader("Actions").setAutoWidth(true);
    }

    private void configureCompletedGrid() {
        configureGrid(completedGrid);
        addDecisionColumns(completedGrid);
        completedGrid.addColumn(row -> format(row.resolvedAt())).setHeader("Resolved").setAutoWidth(true);
        completedGrid.addColumn(HiringDecisionSummary::resolvedBy).setHeader("Resolved by").setAutoWidth(true);
        completedGrid.addColumn(row -> row.resolutionRemarks() == null ? "" : row.resolutionRemarks())
                .setHeader("Resolution remarks").setAutoWidth(true);
        completedGrid.addComponentColumn(this::auditButton).setHeader("Audit").setAutoWidth(true);
    }

    private <T> void configureGrid(Grid<T> grid) {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setAllRowsVisible(true);
        grid.setWidthFull();
    }

    private void addDecisionColumns(Grid<HiringDecisionSummary> grid) {
        grid.addColumn(HiringDecisionSummary::applicantName).setHeader("Applicant").setAutoWidth(true);
        grid.addColumn(HiringDecisionSummary::branch).setHeader("Branch").setAutoWidth(true);
        grid.addColumn(HiringDecisionSummary::position).setHeader("Position").setAutoWidth(true);
        grid.addColumn(HiringDecisionSummary::client).setHeader("Client").setAutoWidth(true);
        grid.addColumn(row -> row.status().name()).setHeader("Status").setAutoWidth(true);
        grid.addColumn(row -> format(row.offeredAt())).setHeader("Offered").setAutoWidth(true);
        grid.addColumn(HiringDecisionSummary::offeredBy).setHeader("Offered by").setAutoWidth(true);
    }

    private Button auditButton(HiringDecisionSummary decision) {
        Button audit = new Button("View audit", event -> {
            try {
                new HiringAuditDialog(
                        decision.applicantName(),
                        hiringDecisionService.findAudit(decision.decisionId())
                ).open();
            } catch (RuntimeException exception) {
                UserSafeNotifier.showError(exception);
            }
        });
        audit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        return audit;
    }

    private void openAction(
            String title,
            String message,
            String label,
            boolean reasonRequired,
            java.util.function.Consumer<String> action
    ) {
        new HiringActionDialog(title, message, label, reasonRequired, action, this::refresh).open();
    }

    private void refresh() {
        try {
            eligible = hiringDecisionService.findEligibleCandidates();
            outstanding = hiringDecisionService.findOutstandingDecisions();
            completed = hiringDecisionService.findCompletedDecisions();
            applyFilter();
        } catch (RuntimeException exception) {
            UserSafeNotifier.showError(exception);
        }
    }

    private void applyFilter() {
        String keyword = filter.getValue() == null ? "" : filter.getValue().trim().toLowerCase(Locale.ROOT);
        eligibleGrid.setItems(eligible.stream().filter(row -> matches(
                keyword, row.applicantName(), row.branch(), row.position(), row.client()
        )).toList());
        outstandingGrid.setItems(outstanding.stream().filter(row -> matches(
                keyword, row.applicantName(), row.branch(), row.position(), row.client(), row.status().name()
        )).toList());
        completedGrid.setItems(completed.stream().filter(row -> matches(
                keyword, row.applicantName(), row.branch(), row.position(), row.client(), row.status().name()
        )).toList());
    }

    private boolean matches(String keyword, String... values) {
        if (keyword.isBlank()) {
            return true;
        }
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String format(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME);
    }
}
