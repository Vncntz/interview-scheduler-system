package com.company.iss.recruiter.view;

import com.company.iss.auth.entity.User;
import com.company.iss.branch.service.BranchService;
import com.company.iss.recruiter.dialog.RecruiterFormDialog;
import com.company.iss.recruiter.service.RecruiterService;
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

@Route(value = "recruiters", layout = MainLayout.class)
@PageTitle("Recruiter Management")
@RolesAllowed("ADMIN")
public class RecruiterView extends VerticalLayout {

    @Autowired
    private RecruiterService recruiterService;

    @Autowired
    private BranchService branchService;

    private Grid<User> recruiterGrid;

    private HorizontalLayout filterLayout;
    private TextField searchField;
    private Button searchButton;

    private HorizontalLayout actionLayout;
    private Button addButton;
    private Button editButton;

    public RecruiterView() {
        setSizeFull();

        filterLayout = new HorizontalLayout();

        searchField = new TextField();
        searchField.setPlaceholder("Search Recruiter");

        searchButton = new Button("Search Recruiter");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.setIcon(VaadinIcon.SEARCH.create());
        searchButton.addClickListener(e -> {
            onSearch(searchField.getValue());
        });

        filterLayout.add(searchField, searchButton);
        filterLayout.setWidthFull();
        filterLayout.setJustifyContentMode(JustifyContentMode.END);

        recruiterGrid = new Grid<>();
        recruiterGrid.setHeightFull();
        recruiterGrid.setWidth("100%");
        recruiterGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS, GridVariant.LUMO_COMPACT);
        recruiterGrid.addColumn(o -> o.getFullName()).setHeader("Full Name").setWidth("250px").setResizable(true).setFlexGrow(1);
        recruiterGrid.addColumn(o -> o.getEmail()).setHeader("Email").setWidth("250px").setResizable(true);
        recruiterGrid.addColumn(o -> o.getBranch() != null ? o.getBranch().getBranchName() : "").setHeader("Branch").setWidth("220px").setResizable(true);
        recruiterGrid.addColumn(o -> o.getLastLoginAt() != null ? o.getLastLoginAt().toString() : "Never").setHeader("Last Login").setWidth("150px").setResizable(true);
        recruiterGrid.addColumn(o -> o.isActive() ? "Active" : "Inactive").setHeader("Status").setWidth("130px").setResizable(true);
        recruiterGrid.addComponentColumn(user -> {
            Button toggle = new Button(user.isActive() ? "Deactivate" : "Activate");

            if (user.isActive()) {
                toggle.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            } else {
                toggle.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            }

            toggle.addClickListener(e -> {
                if (user.isActive()) {
                    recruiterService.deactivate(user);
                } else {
                    recruiterService.activate(user);
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
            openDialog(new User());
        });

        editButton = new Button("Edit");
        editButton.setIcon(VaadinIcon.PENCIL.create());
        editButton.addClickListener(e -> {
            onEdit();
        });

        actionLayout.add(addButton, editButton);

        add(filterLayout, recruiterGrid, actionLayout);
    }

    private void onSearch(String value) {
        init();
    }

    private void onEdit() {
        User selected = recruiterGrid.asSingleSelect().getValue();

        if (selected != null) {
            openDialog(selected);
        } else {
            Notification.show("Please select a recruiter from the table first.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_WARNING);
        }
    }

    @PostConstruct
    private void init() {
        recruiterGrid.setItems(recruiterService.search(searchField.getValue()));
    }

    private void openDialog(User user) {
        RecruiterFormDialog dialog = new RecruiterFormDialog(user, branchService, (savedUser, temporaryPassword) -> {
            try {
                recruiterService.save(savedUser, temporaryPassword);

                init();

                Notification.show("Recruiter saved successfully!", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            } catch (Exception ex) {
                Notification.show("Error saving record: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        dialog.open();
    }
}