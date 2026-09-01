package com.company.iss.evaluation.view;

import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.evaluation.dto.EvaluationGridFilter;
import com.company.iss.evaluation.dto.EvaluationGridSort;
import com.company.iss.evaluation.dto.EvaluationGridSortOrder;
import com.company.iss.evaluation.service.InterviewEvaluationService;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.shared.view.MainLayout;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "evaluations", layout = MainLayout.class)
@PageTitle("Interview Evaluations")
@RolesAllowed({"ADMIN", "RECRUITER"})
public class InterviewEvaluationView extends VerticalLayout {

    private static final int GRID_PAGE_SIZE = 50;

    private final InterviewEvaluationService interviewEvaluationService;

    private Grid<InterviewEvaluation> evaluationGrid;
    private CallbackDataProvider<InterviewEvaluation, Void> dataProvider;
    private TextField keywordFilter;
    private ComboBox<InterviewStage> stageFilter;
    private ComboBox<InterviewResult> resultFilter;
    private DatePicker dateFilter;

    public InterviewEvaluationView(InterviewEvaluationService interviewEvaluationService) {
        this.interviewEvaluationService = interviewEvaluationService;
        setSizeFull();

        keywordFilter = new TextField();
        keywordFilter.setPlaceholder("Search Evaluations");
        keywordFilter.setClearButtonVisible(true);
        keywordFilter.setValueChangeMode(ValueChangeMode.LAZY);
        keywordFilter.addValueChangeListener(event -> refreshGrid());

        stageFilter = new ComboBox<>();
        stageFilter.setPlaceholder("Interview Stage");
        stageFilter.setItems(InterviewStage.values());
        stageFilter.setClearButtonVisible(true);
        stageFilter.addValueChangeListener(event -> refreshGrid());

        resultFilter = new ComboBox<>();
        resultFilter.setPlaceholder("Evaluation Result");
        resultFilter.setItems(InterviewResult.values());
        resultFilter.setClearButtonVisible(true);
        resultFilter.addValueChangeListener(event -> refreshGrid());

        dateFilter = new DatePicker();
        dateFilter.setPlaceholder("Evaluation Date");
        dateFilter.setClearButtonVisible(true);
        dateFilter.addValueChangeListener(event -> refreshGrid());

        HorizontalLayout filters = new HorizontalLayout(keywordFilter, stageFilter, resultFilter, dateFilter);
        filters.setWidthFull();
        filters.setAlignItems(Alignment.END);

        evaluationGrid = new Grid<>();
        evaluationGrid.setSizeFull();
        evaluationGrid.setPageSize(GRID_PAGE_SIZE);

        evaluationGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS, GridVariant.LUMO_COMPACT);

        evaluationGrid.addColumn(o -> o.getApplicant() == null ? "" : o.getApplicant().getFullName()).setHeader("Applicant").setSortProperty("applicant").setWidth("220px").setResizable(true);

        evaluationGrid.addColumn(this::positionTitle).setHeader("Position").setSortProperty("position").setWidth("180px").setResizable(true);

        evaluationGrid.addColumn(this::clientName).setHeader("Client").setSortProperty("client").setWidth("220px").setResizable(true);

        evaluationGrid.addColumn(this::interviewStage).setHeader("Interview Stage").setSortProperty("stage").setWidth("160px").setTextAlign(ColumnTextAlign.CENTER).setResizable(true);

        evaluationGrid.addColumn(InterviewEvaluation::getCommunicationScore).setHeader("Communication").setWidth("140px").setTextAlign(ColumnTextAlign.CENTER).setResizable(true);

        evaluationGrid.addColumn(InterviewEvaluation::getTechnicalScore).setHeader("Technical").setWidth("120px").setTextAlign(ColumnTextAlign.CENTER).setResizable(true);

        evaluationGrid.addColumn(InterviewEvaluation::getAttitudeScore).setHeader("Attitude").setWidth("120px").setTextAlign(ColumnTextAlign.CENTER).setResizable(true);

        evaluationGrid.addColumn(o -> o.getResult() == null ? "" : o.getResult().name()).setHeader("Result").setSortProperty("result").setWidth("180px").setTextAlign(ColumnTextAlign.CENTER).setResizable(true);

        evaluationGrid.addColumn(o -> o.getEvaluator() == null ? "" : o.getEvaluator().getFullName()).setHeader("Evaluator").setSortProperty("evaluator").setWidth("220px").setResizable(true);

        evaluationGrid.addColumn(InterviewEvaluation::getEvaluationDate).setHeader("Evaluation Date").setSortProperty("evaluationDate").setWidth("200px").setTextAlign(ColumnTextAlign.CENTER).setResizable(true);

        dataProvider = DataProvider.fromCallbacks(
                query -> interviewEvaluationService.findGridPage(
                        currentFilter(), query.getOffset(), query.getLimit(), mapSortOrders(query.getSortOrders())
                ).stream(),
                query -> toIntCount(interviewEvaluationService.countGrid(currentFilter()))
        );
        evaluationGrid.setDataProvider(dataProvider);

        add(filters, evaluationGrid);
    }

    private EvaluationGridFilter currentFilter() {
        return new EvaluationGridFilter(
                keywordFilter.getValue(), stageFilter.getValue(), resultFilter.getValue(), dateFilter.getValue()
        );
    }

    private java.util.List<EvaluationGridSortOrder> mapSortOrders(
            java.util.List<com.vaadin.flow.data.provider.QuerySortOrder> sortOrders
    ) {
        return sortOrders.stream()
                .map(order -> EvaluationGridSort.fromKey(order.getSorted())
                        .map(field -> new EvaluationGridSortOrder(
                                field,
                                order.getDirection() == SortDirection.ASCENDING
                                        ? org.springframework.data.domain.Sort.Direction.ASC
                                        : org.springframework.data.domain.Sort.Direction.DESC
                        )))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    private void refreshGrid() {
        evaluationGrid.deselectAll();
        dataProvider.refreshAll();
    }

    private int toIntCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private String positionTitle(InterviewEvaluation evaluation) {
        return evaluation.getApplicant() == null || evaluation.getApplicant().getPositionOpening() == null
                ? "" : evaluation.getApplicant().getPositionOpening().getTitle();
    }

    private String clientName(InterviewEvaluation evaluation) {
        return evaluation.getApplicant() == null
                || evaluation.getApplicant().getPositionOpening() == null
                || evaluation.getApplicant().getPositionOpening().getClient() == null
                ? "" : evaluation.getApplicant().getPositionOpening().getClient().getCompanyName();
    }

    private String interviewStage(InterviewEvaluation evaluation) {
        return evaluation.getBooking() == null || evaluation.getBooking().getInterviewStage() == null
                ? "" : evaluation.getBooking().getInterviewStage().name();
    }
}
