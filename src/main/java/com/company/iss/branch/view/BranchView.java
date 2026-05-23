package com.company.iss.branch.view;

import com.company.iss.branch.dialog.BranchFormDialog;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.service.BranchService;
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

@Route(value = "branches", layout = MainLayout.class)
@PageTitle("Branch Management")
@RolesAllowed("ADMIN")
public class BranchView extends VerticalLayout {

    @Autowired
    private BranchService branchService;

    private Grid<Branch> branchGrid;
    private HorizontalLayout filterLayout;
    private TextField searchField;
    private Button searchButton;

    private HorizontalLayout actionLayout;
    private Button addButton;
    private Button deleteButton;
    private Button editButton;

    public BranchView() {
        setSizeFull();

        filterLayout = new HorizontalLayout();

        searchField = new TextField();
        searchField.setPlaceholder("Search");

        searchButton = new Button("Search Branch Name");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.setIcon(VaadinIcon.SEARCH.create());
        searchButton.addClickListener(e -> {
            onSearch(searchField.getValue());
        });

        filterLayout.add(searchField, searchButton);
        filterLayout.setWidthFull();
        filterLayout.setJustifyContentMode(JustifyContentMode.END);

        branchGrid = new Grid<>();
        branchGrid.setHeightFull();
        branchGrid.setWidth("100%");
        branchGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS, GridVariant.LUMO_COMPACT);
        branchGrid.addColumn(o -> o.getBranchCode()).setHeader("Code").setWidth("140px").setResizable(true);
        branchGrid.addColumn(o -> o.getBranchName()).setHeader("Branch Name").setWidth("250px").setResizable(true).setFlexGrow(1);
        branchGrid.addColumn(o -> o.getCity()).setHeader("City").setWidth("180px").setResizable(true);
        branchGrid.addColumn(o -> o.getProvince()).setHeader("Province").setWidth("180px").setResizable(true);
        branchGrid.addColumn(o -> o.isActive() ? "Active" : "Inactive").setHeader("Status").setWidth("130px").setResizable(true);
        branchGrid.addComponentColumn(branch -> {
            Button toggle = new Button(branch.isActive() ? "Deactivate" : "Activate");

            if (branch.isActive()) {
                toggle.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            } else {
                toggle.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            }

            toggle.addClickListener(e -> {
                if (branch.isActive()) {
                    branchService.deactivate(branch);
                } else {
                    branchService.activate(branch);
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
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.setIcon(VaadinIcon.PLUS.create());
        addButton.addClickListener(e -> {
            openBranchDialog(new Branch());
        });

        editButton = new Button("Edit");
        editButton.setIcon(VaadinIcon.PENCIL.create());
        editButton.addClickListener(e -> {
            onEdit();
        });

        deleteButton = new Button("Delete");
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteButton.setIcon(VaadinIcon.TRASH.create());
        deleteButton.addClickListener(e -> {
            onDelete();
        });

        actionLayout.add(addButton, editButton, deleteButton);

        add(filterLayout, branchGrid, actionLayout);
    }

    private void onDelete() {
        Notification.show("To be Implemented.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_WARNING);
    }

    private void onEdit() {
        Branch selected = branchGrid.asSingleSelect().getValue();
        if (selected != null) {
            openBranchDialog(selected);
        } else {
            Notification.show("Please select a branch from the table first.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_WARNING);
        }
    }


    private void onSearch(String value) {
        init();
    }

    @PostConstruct
    private void init() {
        branchGrid.setItems(branchService.search(searchField.getValue()));
    }

    private void openBranchDialog(Branch branch) {
        BranchFormDialog dialog = new BranchFormDialog(branch, savedBranch -> {
            try {
                branchService.save(savedBranch);
                init();
                Notification.show("Branch saved successfully!", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show("Error saving record: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        dialog.open();
    }
}