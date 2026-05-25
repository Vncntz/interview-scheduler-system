package com.company.iss.evaluation.view;

import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.evaluation.service.InterviewEvaluationService;
import com.company.iss.shared.view.MainLayout;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "evaluations", layout = MainLayout.class)
@PageTitle("Interview Evaluations")
@RolesAllowed({"ADMIN", "RECRUITER"})
public class InterviewEvaluationView extends VerticalLayout {

    @Autowired
    private InterviewEvaluationService interviewEvaluationService;

    private Grid<InterviewEvaluation> evaluationGrid;

    public InterviewEvaluationView() {
        setSizeFull();

        evaluationGrid = new Grid<>();
        evaluationGrid.setSizeFull();

        evaluationGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS, GridVariant.LUMO_COMPACT);

        evaluationGrid.addColumn(o -> o.getApplicant().getFullName()).setHeader("Applicant").setWidth("220px").setResizable(true);

        evaluationGrid.addColumn(o -> o.getApplicant().getPositionOpening().getTitle()).setHeader("Position").setWidth("180px").setResizable(true);

        evaluationGrid.addColumn(o -> o.getApplicant().getPositionOpening().getClient().getCompanyName()).setHeader("Client").setWidth("220px").setResizable(true);

        evaluationGrid.addColumn(InterviewEvaluation::getCommunicationScore).setHeader("Communication").setWidth("140px").setTextAlign(ColumnTextAlign.CENTER).setResizable(true);

        evaluationGrid.addColumn(InterviewEvaluation::getTechnicalScore).setHeader("Technical").setWidth("120px").setTextAlign(ColumnTextAlign.CENTER).setResizable(true);

        evaluationGrid.addColumn(InterviewEvaluation::getAttitudeScore).setHeader("Attitude").setWidth("120px").setTextAlign(ColumnTextAlign.CENTER).setResizable(true);

        evaluationGrid.addColumn(o -> o.getResult().name()).setHeader("Result").setWidth("180px").setTextAlign(ColumnTextAlign.CENTER).setResizable(true);

        evaluationGrid.addColumn(o -> o.getEvaluator().getFullName()).setHeader("Evaluator").setWidth("220px").setResizable(true);

        evaluationGrid.addColumn(InterviewEvaluation::getEvaluationDate).setHeader("Evaluation Date").setWidth("200px").setTextAlign(ColumnTextAlign.CENTER).setResizable(true);

        add(evaluationGrid);
    }

    @PostConstruct
    private void init() {
        evaluationGrid.setItems(interviewEvaluationService.findAll());
    }
}