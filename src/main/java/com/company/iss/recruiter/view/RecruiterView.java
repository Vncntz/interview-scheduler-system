package com.company.iss.recruiter.view;

import com.company.iss.auth.entity.User;
import com.company.iss.branch.service.BranchService;
import com.company.iss.recruiter.dialog.RecruiterFormDialog;
import com.company.iss.recruiter.service.RecruiterService;
import com.company.iss.shared.view.MainLayout;
import com.company.iss.shared.view.UserSafeNotifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
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

import java.time.LocalDateTime;

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
                    confirmDeactivate(user);
                } else {
                    try {
                        recruiterService.activate(user.getId());
                        init();
                    } catch (RuntimeException exception) {
                        UserSafeNotifier.showError(exception);
                    }
                }
            });

            Button reset = new Button("Send reset", e -> confirmReset(user));
            reset.addThemeVariants(ButtonVariant.LUMO_SMALL);
            Button unlock = new Button("Unlock", e -> {
                try {
                    recruiterService.unlock(user.getId());
                    init();
                } catch (RuntimeException exception) {
                    UserSafeNotifier.showError(exception);
                }
            });
            unlock.addThemeVariants(ButtonVariant.LUMO_SMALL);
            unlock.setVisible(user.getLockoutUntil() != null
                    && user.getLockoutUntil().isAfter(LocalDateTime.now()));

            HorizontalLayout wrap = new HorizontalLayout(toggle, reset, unlock);
            wrap.setWidthFull();
            wrap.setJustifyContentMode(JustifyContentMode.CENTER);
            wrap.setAlignItems(Alignment.CENTER);
            return wrap;
        }).setHeader("Actions").setWidth("330px").setResizable(true);

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
                UserSafeNotifier.showError(ex);
            }
        });

        dialog.open();
    }

    private void confirmReset(User user) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Send password reset?");
        dialog.setText("A single-use password reset link will be emailed to this recruiter.");
        dialog.setCancelable(true);
        dialog.setConfirmText("Send reset link");
        dialog.addConfirmListener(event -> {
            try {
                recruiterService.requestPasswordReset(user.getId());
                Notification.show(
                        "If delivery is configured, the recruiter will receive a password reset link.",
                        4000,
                        Notification.Position.TOP_CENTER
                ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (RuntimeException exception) {
                UserSafeNotifier.showError(exception);
            }
        });
        dialog.open();
    }

    private void confirmDeactivate(User user) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Deactivate recruiter?");
        dialog.setText("The recruiter will lose access and all known sessions will be expired.");
        dialog.setCancelable(true);
        dialog.setConfirmText("Deactivate");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> {
            try {
                recruiterService.deactivate(user.getId());
                init();
            } catch (RuntimeException exception) {
                UserSafeNotifier.showError(exception);
            }
        });
        dialog.open();
    }
}
