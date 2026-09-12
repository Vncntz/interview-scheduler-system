package com.company.iss.hiring.view;

import com.company.iss.hiring.dialog.HiringActionDialog;
import com.company.iss.hiring.dialog.HiringAuditDialog;
import com.company.iss.hiring.dialog.IssueOfferDialog;
import com.company.iss.hiring.dto.CompletedDecisionSort;
import com.company.iss.hiring.dto.CompletedDecisionSortOrder;
import com.company.iss.hiring.dto.EligibleCandidateSort;
import com.company.iss.hiring.dto.EligibleCandidateSortOrder;
import com.company.iss.hiring.dto.EligibleHiringCandidate;
import com.company.iss.hiring.dto.HiringActionCommand;
import com.company.iss.hiring.dto.HiringDecisionSummary;
import com.company.iss.hiring.dto.HiringWorklistFilter;
import com.company.iss.hiring.dto.OutstandingDecisionSort;
import com.company.iss.hiring.dto.OutstandingDecisionSortOrder;
import com.company.iss.hiring.service.HiringDecisionService;
import com.company.iss.shared.view.MainLayout;
import com.company.iss.shared.view.UserSafeNotifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.QuerySortOrder;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

@Route(value = "hiring-decisions", layout = MainLayout.class)
@PageTitle("Final Hiring Decisions")
@RolesAllowed({"ADMIN", "RECRUITER"})
public class HiringDecisionView extends VerticalLayout {

    private static final int GRID_PAGE_SIZE = 50;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final HiringDecisionService hiringDecisionService;
    private final Grid<EligibleHiringCandidate> eligibleGrid = new Grid<>();
    private final Grid<HiringDecisionSummary> outstandingGrid = new Grid<>();
    private final Grid<HiringDecisionSummary> completedGrid = new Grid<>();
    private final TextField filter = new TextField("Filter");
    private final Button refreshButton = new Button("Refresh");
    private final Span eligibleState = new Span("Loading eligible candidates...");
    private final Span outstandingState = new Span("Loading outstanding offers...");
    private final Span completedState = new Span("Loading completed decisions...");
    private CallbackDataProvider<EligibleHiringCandidate, Void> eligibleProvider;
    private CallbackDataProvider<HiringDecisionSummary, Void> outstandingProvider;
    private CallbackDataProvider<HiringDecisionSummary, Void> completedProvider;
    private boolean eligibleLoadFailed;
    private boolean outstandingLoadFailed;
    private boolean completedLoadFailed;

    public HiringDecisionView(HiringDecisionService hiringDecisionService) {
        this.hiringDecisionService = hiringDecisionService;
        setSizeFull();
        setPadding(true);

        filter.setPlaceholder("Applicant, branch, position, client, or decision status");
        filter.setClearButtonVisible(true);
        filter.setWidth("420px");
        filter.setMaxLength(100);
        filter.setHelperText("Search is case-insensitive; %, _, and \\ are matched literally.");
        filter.setValueChangeMode(ValueChangeMode.LAZY);
        filter.setValueChangeTimeout(350);
        filter.addValueChangeListener(event -> refreshWorklists(true));
        refreshButton.addClickListener(event -> refreshWorklists(false));

        configureEligibleGrid();
        configureOutstandingGrid();
        configureCompletedGrid();
        add(new HorizontalLayout(filter, refreshButton),
                heading("Eligible passed candidates", eligibleState), eligibleGrid,
                heading("Outstanding offers", outstandingState), outstandingGrid,
                heading("Completed decisions and audit", completedState), completedGrid);
    }

    private HorizontalLayout heading(String title, Span state) {
        H2 heading = new H2(title);
        heading.getStyle().set("margin", "0");
        HorizontalLayout row = new HorizontalLayout(heading, state);
        row.setAlignItems(Alignment.BASELINE);
        return row;
    }

    private void configureEligibleGrid() {
        configureGrid(eligibleGrid);
        eligibleGrid.addColumn(EligibleHiringCandidate::applicantName).setHeader("Applicant")
                .setKey("applicant").setSortProperty("applicant").setAutoWidth(true);
        eligibleGrid.addColumn(EligibleHiringCandidate::branch).setHeader("Branch")
                .setKey("branch").setSortProperty("branch").setAutoWidth(true);
        eligibleGrid.addColumn(EligibleHiringCandidate::position).setHeader("Position")
                .setKey("position").setSortProperty("position").setAutoWidth(true);
        eligibleGrid.addColumn(EligibleHiringCandidate::client).setHeader("Client")
                .setKey("client").setSortProperty("client").setAutoWidth(true);
        eligibleGrid.addColumn(EligibleHiringCandidate::workLocation).setHeader("Work location")
                .setKey("workLocation").setSortProperty("workLocation").setAutoWidth(true);
        eligibleGrid.addColumn(row -> format(row.evaluatedAt())).setHeader("Evaluated")
                .setKey("evaluatedAt").setSortProperty("evaluatedAt").setAutoWidth(true);
        eligibleGrid.addComponentColumn(candidate -> {
            Button offer = new Button("Issue offer", event -> new IssueOfferDialog(
                    candidate, hiringDecisionService, this::onHiringActionSucceeded).open());
            offer.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            return offer;
        }).setHeader("Action").setAutoWidth(true);
        eligibleProvider = DataProvider.fromCallbacks(
                query -> fetchEligible(query.getOffset(), query.getLimit(), query.getSortOrders()),
                query -> countEligible());
        eligibleGrid.setDataProvider(eligibleProvider);
    }

    private void configureOutstandingGrid() {
        configureGrid(outstandingGrid);
        addDecisionColumns(outstandingGrid);
        outstandingGrid.addComponentColumn(decision -> {
            Button hired = new Button("Mark hired", event -> openAction(
                    "Confirm hire",
                    "Mark %s as hired for %s? This permanently consumes one headcount."
                            .formatted(decision.applicantName(), decision.position()),
                    "Mark hired", false,
                    remarks -> hiringDecisionService.acceptAndHire(
                            new HiringActionCommand(decision.applicantId(), remarks))));
            hired.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            Button decline = new Button("Decline", event -> openAction(
                    "Decline offer",
                    "Record that %s declined the offer? This decision cannot be reversed."
                            .formatted(decision.applicantName()),
                    "Decline offer", true,
                    remarks -> hiringDecisionService.decline(
                            new HiringActionCommand(decision.applicantId(), remarks))));
            decline.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            Button withdraw = new Button("Withdraw", event -> openAction(
                    "Withdraw offer",
                    "Withdraw the offer for %s? This decision cannot be reversed."
                            .formatted(decision.applicantName()),
                    "Withdraw offer", true,
                    remarks -> hiringDecisionService.withdraw(
                            new HiringActionCommand(decision.applicantId(), remarks))));
            withdraw.addThemeVariants(ButtonVariant.LUMO_SMALL);
            return new HorizontalLayout(hired, decline, withdraw, auditButton(decision));
        }).setHeader("Actions").setAutoWidth(true);
        outstandingProvider = DataProvider.fromCallbacks(
                query -> fetchOutstanding(query.getOffset(), query.getLimit(), query.getSortOrders()),
                query -> countOutstanding());
        outstandingGrid.setDataProvider(outstandingProvider);
    }

    private void configureCompletedGrid() {
        configureGrid(completedGrid);
        addDecisionColumns(completedGrid);
        completedGrid.addColumn(row -> format(row.resolvedAt())).setHeader("Resolved")
                .setKey("resolvedAt").setSortProperty("resolvedAt").setAutoWidth(true);
        completedGrid.addColumn(HiringDecisionSummary::resolvedBy).setHeader("Resolved by").setAutoWidth(true);
        completedGrid.addColumn(row -> row.resolutionRemarks() == null ? "" : row.resolutionRemarks())
                .setHeader("Resolution remarks").setAutoWidth(true);
        completedGrid.addComponentColumn(this::auditButton).setHeader("Audit").setAutoWidth(true);
        completedProvider = DataProvider.fromCallbacks(
                query -> fetchCompleted(query.getOffset(), query.getLimit(), query.getSortOrders()),
                query -> countCompleted());
        completedGrid.setDataProvider(completedProvider);
    }

    private <T> void configureGrid(Grid<T> grid) {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setPageSize(GRID_PAGE_SIZE);
        grid.setWidthFull();
        grid.setHeight("320px");
    }

    private void addDecisionColumns(Grid<HiringDecisionSummary> grid) {
        grid.addColumn(HiringDecisionSummary::applicantName).setHeader("Applicant")
                .setKey("applicant").setSortProperty("applicant").setAutoWidth(true);
        grid.addColumn(HiringDecisionSummary::branch).setHeader("Branch")
                .setKey("branch").setSortProperty("branch").setAutoWidth(true);
        grid.addColumn(HiringDecisionSummary::position).setHeader("Position")
                .setKey("position").setSortProperty("position").setAutoWidth(true);
        grid.addColumn(HiringDecisionSummary::client).setHeader("Client")
                .setKey("client").setSortProperty("client").setAutoWidth(true);
        grid.addColumn(row -> row.status().name()).setHeader("Status")
                .setKey("status").setSortProperty("status").setAutoWidth(true);
        grid.addColumn(row -> format(row.offeredAt())).setHeader("Offered")
                .setKey("offeredAt").setSortProperty("offeredAt").setAutoWidth(true);
        grid.addColumn(HiringDecisionSummary::offeredBy).setHeader("Offered by").setAutoWidth(true);
    }

    private Stream<EligibleHiringCandidate> fetchEligible(int offset, int limit, List<QuerySortOrder> sortOrders) {
        try {
            return hiringDecisionService.findEligiblePage(
                    currentFilter(), offset, limit, eligibleSortOrders(sortOrders)).stream();
        } catch (RuntimeException exception) {
            markFailure(eligibleState, "eligible candidates", exception);
            return Stream.empty();
        }
    }

    private int countEligible() {
        try {
            return updateCount(eligibleState, hiringDecisionService.countEligible(currentFilter()), "eligible candidates");
        } catch (RuntimeException exception) {
            markFailure(eligibleState, "eligible candidates", exception);
            return 0;
        }
    }

    private Stream<HiringDecisionSummary> fetchOutstanding(int offset, int limit, List<QuerySortOrder> sortOrders) {
        try {
            return hiringDecisionService.findOutstandingPage(
                    currentFilter(), offset, limit, outstandingSortOrders(sortOrders)).stream();
        } catch (RuntimeException exception) {
            markFailure(outstandingState, "outstanding offers", exception);
            return Stream.empty();
        }
    }

    private int countOutstanding() {
        try {
            return updateCount(outstandingState,
                    hiringDecisionService.countOutstanding(currentFilter()), "outstanding offers");
        } catch (RuntimeException exception) {
            markFailure(outstandingState, "outstanding offers", exception);
            return 0;
        }
    }

    private Stream<HiringDecisionSummary> fetchCompleted(int offset, int limit, List<QuerySortOrder> sortOrders) {
        try {
            return hiringDecisionService.findCompletedPage(
                    currentFilter(), offset, limit, completedSortOrders(sortOrders)).stream();
        } catch (RuntimeException exception) {
            markFailure(completedState, "completed decisions", exception);
            return Stream.empty();
        }
    }

    private int countCompleted() {
        try {
            return updateCount(completedState,
                    hiringDecisionService.countCompleted(currentFilter()), "completed decisions");
        } catch (RuntimeException exception) {
            markFailure(completedState, "completed decisions", exception);
            return 0;
        }
    }

    private int updateCount(Span state, long count, String label) {
        if (!loadFailed(state)) {
            state.setText(count == 0 ? "No matching " + label + "." : count + " matching " + label + ".");
        }
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private void markFailure(Span state, String label, RuntimeException exception) {
        if (state == eligibleState) {
            eligibleLoadFailed = true;
        } else if (state == outstandingState) {
            outstandingLoadFailed = true;
        } else {
            completedLoadFailed = true;
        }
        state.setText("Unable to load " + label + ".");
        UserSafeNotifier.showError(exception);
    }

    private boolean loadFailed(Span state) {
        if (state == eligibleState) {
            return eligibleLoadFailed;
        }
        if (state == outstandingState) {
            return outstandingLoadFailed;
        }
        return completedLoadFailed;
    }

    private HiringWorklistFilter currentFilter() {
        return new HiringWorklistFilter(filter.getValue());
    }

    private List<EligibleCandidateSortOrder> eligibleSortOrders(List<QuerySortOrder> orders) {
        return orders.stream()
                .map(order -> EligibleCandidateSort.fromKey(order.getSorted())
                        .map(field -> new EligibleCandidateSortOrder(field, direction(order))))
                .flatMap(java.util.Optional::stream).toList();
    }

    private List<OutstandingDecisionSortOrder> outstandingSortOrders(List<QuerySortOrder> orders) {
        return orders.stream()
                .map(order -> OutstandingDecisionSort.fromKey(order.getSorted())
                        .map(field -> new OutstandingDecisionSortOrder(field, direction(order))))
                .flatMap(java.util.Optional::stream).toList();
    }

    private List<CompletedDecisionSortOrder> completedSortOrders(List<QuerySortOrder> orders) {
        return orders.stream()
                .map(order -> CompletedDecisionSort.fromKey(order.getSorted())
                        .map(field -> new CompletedDecisionSortOrder(field, direction(order))))
                .flatMap(java.util.Optional::stream).toList();
    }

    private Sort.Direction direction(QuerySortOrder order) {
        return order.getDirection() == SortDirection.ASCENDING ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    private Button auditButton(HiringDecisionSummary decision) {
        Button audit = new Button("View audit", event -> {
            try {
                new HiringAuditDialog(decision.applicantName(),
                        hiringDecisionService.findAudit(decision.decisionId())).open();
            } catch (RuntimeException exception) {
                UserSafeNotifier.showError(exception);
            }
        });
        audit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        return audit;
    }

    private void openAction(String title, String message, String label, boolean reasonRequired,
                            java.util.function.Consumer<String> action) {
        new HiringActionDialog(title, message, label, reasonRequired, action, this::onHiringActionSucceeded).open();
    }

    void onHiringActionSucceeded() {
        refreshWorklists(true);
    }

    private void refreshWorklists(boolean scrollToStart) {
        eligibleLoadFailed = false;
        outstandingLoadFailed = false;
        completedLoadFailed = false;
        eligibleState.setText("Loading eligible candidates...");
        outstandingState.setText("Loading outstanding offers...");
        completedState.setText("Loading completed decisions...");
        if (scrollToStart) {
            eligibleGrid.scrollToStart();
            outstandingGrid.scrollToStart();
            completedGrid.scrollToStart();
        }
        eligibleGrid.deselectAll();
        outstandingGrid.deselectAll();
        completedGrid.deselectAll();
        eligibleProvider.refreshAll();
        outstandingProvider.refreshAll();
        completedProvider.refreshAll();
    }

    private String format(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME);
    }
}
