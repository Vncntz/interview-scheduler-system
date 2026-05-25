package com.company.iss.position.view;

import com.company.iss.client.service.ClientService;
import com.company.iss.position.dialog.PositionOpeningFormDialog;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.service.PositionOpeningService;
import com.company.iss.shared.view.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "positions", layout = MainLayout.class)
@PageTitle("Position Openings")
@RolesAllowed("ADMIN")
public class PositionOpeningView extends VerticalLayout {

    @Autowired
    private PositionOpeningService positionOpeningService;

    @Autowired
    private ClientService clientService;

    private Grid<PositionOpening> positionGrid;

    private HorizontalLayout filterLayout;
    private TextField searchField;
    private Button searchButton;

    private HorizontalLayout actionLayout;
    private Button addButton;
    private Button editButton;

    public PositionOpeningView() {
        setSizeFull();

        filterLayout = new HorizontalLayout();

        searchField = new TextField();
        searchField.setPlaceholder("Search Position");

        searchButton = new Button("Search");
        searchButton.setIcon(VaadinIcon.SEARCH.create());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> onSearch(searchField.getValue()));

        filterLayout.add(searchField, searchButton);
        filterLayout.setWidthFull();
        filterLayout.setJustifyContentMode(JustifyContentMode.END);

        positionGrid = new Grid<>();
        positionGrid.setHeightFull();
        positionGrid.setWidthFull();

        positionGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS, GridVariant.LUMO_COMPACT);

        positionGrid.addColumn(PositionOpening::getTitle).setHeader("Position").setWidth("180px").setResizable(true);

        positionGrid.addColumn(o -> o.getClient().getCompanyName()).setHeader("Client").setWidth("220px").setResizable(true);

        positionGrid.addColumn(PositionOpening::getWorkLocation).setHeader("Work Location").setWidth("220px").setResizable(true);

        positionGrid.addColumn(o -> o.getEmploymentType().name()).setHeader("Employment Type").setWidth("150px").setResizable(true);

        positionGrid.addColumn(PositionOpening::getRequiredHeadcount).setHeader("Required").setWidth("100px").setResizable(true);

        positionGrid.addColumn(PositionOpening::getAppliedCount).setHeader("Applied").setWidth("100px").setResizable(true);

        positionGrid.addColumn(PositionOpening::getInterviewedCount).setHeader("Interviewed").setWidth("120px").setResizable(true);

        positionGrid.addColumn(PositionOpening::getPassedCount).setHeader("Passed").setWidth("100px").setResizable(true);

        positionGrid.addColumn(PositionOpening::getHiredCount).setHeader("Hired").setWidth("100px").setResizable(true);

        positionGrid.addColumn(PositionOpening::getRemainingHeadcount).setHeader("Remaining").setWidth("120px").setResizable(true);

        positionGrid.addColumn(o -> o.getStatus().name()).setHeader("Status").setWidth("120px").setResizable(true);

        positionGrid.addColumn(o -> o.isActive() ? "Active" : "Inactive").setHeader("Record Status").setWidth("130px").setResizable(true);

        positionGrid.addComponentColumn(position -> {
            Button toggle = new Button(position.isActive() ? "Deactivate" : "Activate");

            if (position.isActive()) {
                toggle.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            } else {
                toggle.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            }

            toggle.addClickListener(e -> {
                if (position.isActive()) {
                    positionOpeningService.deactivate(position);
                } else {
                    positionOpeningService.activate(position);
                }

                init();
            });

            HorizontalLayout wrap = new HorizontalLayout(toggle);
            wrap.setWidthFull();
            wrap.setJustifyContentMode(JustifyContentMode.CENTER);
            wrap.setAlignItems(Alignment.CENTER);

            return wrap;

        }).setHeader("Actions").setWidth("180px").setResizable(true);

        actionLayout = new HorizontalLayout();
        actionLayout.setWidthFull();

        addButton = new Button("Add");
        addButton.setIcon(VaadinIcon.PLUS.create());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(e -> openDialog(new PositionOpening()));

        editButton = new Button("Edit");
        editButton.setIcon(VaadinIcon.PENCIL.create());
        editButton.addClickListener(e -> onEdit());

        actionLayout.add(addButton, editButton);

        add(filterLayout, positionGrid, actionLayout);
    }

    private void onSearch(String value) {
        positionGrid.setItems(positionOpeningService.search(value));
    }

    @PostConstruct
    private void init() {
        positionGrid.setItems(positionOpeningService.search(null));
    }

    private void onEdit() {
        PositionOpening selected = positionGrid.asSingleSelect().getValue();

        if (selected == null) {
            Notification.show("Please select a position.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_WARNING);

            return;
        }

        openDialog(selected);
    }

    private void openDialog(PositionOpening positionOpening) {
        PositionOpeningFormDialog dialog = new PositionOpeningFormDialog(positionOpening, clientService, saved -> {
            try {
                positionOpeningService.save(saved);
                init();

            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 4000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        dialog.open();
    }
}