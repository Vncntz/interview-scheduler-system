package com.company.iss.hiring.dialog;

import com.company.iss.hiring.dto.HiringDecisionAuditSummary;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class HiringAuditDialog extends Dialog {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public HiringAuditDialog(String applicantName, List<HiringDecisionAuditSummary> auditRows) {
        setHeaderTitle("Hiring audit - " + applicantName);
        setWidth("900px");

        Grid<HiringDecisionAuditSummary> grid = new Grid<>();
        grid.addColumn(row -> row.action().name()).setHeader("Action").setAutoWidth(true);
        grid.addColumn(row -> row.previousStatus() == null ? "-" : row.previousStatus().name())
                .setHeader("From").setAutoWidth(true);
        grid.addColumn(row -> row.newStatus().name()).setHeader("To").setAutoWidth(true);
        grid.addColumn(HiringDecisionAuditSummary::actor).setHeader("Actor").setAutoWidth(true);
        grid.addColumn(row -> row.occurredAt().format(DATE_TIME)).setHeader("Date").setAutoWidth(true);
        grid.addColumn(row -> row.remarks() == null ? "" : row.remarks()).setHeader("Remarks").setFlexGrow(1);
        grid.setItems(auditRows);
        grid.setAllRowsVisible(true);
        add(grid);

        getFooter().add(new Button("Close", event -> close()));
    }
}
