package com.company.iss.applicant.view;

import com.company.iss.applicant.dialog.ApplicantFormDialog;
import com.company.iss.applicant.dto.ApplicantGridFilter;
import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.service.ApplicantService;
import com.company.iss.booking.dialog.BookingFormDialog;
import com.company.iss.booking.service.BookingService;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.auth.entity.Role;
import com.company.iss.branch.service.BranchService;
import com.company.iss.position.service.PositionOpeningService;
import com.company.iss.schedule.service.ScheduleService;
import com.company.iss.shared.view.MainLayout;
import com.company.iss.shared.view.UserSafeNotifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "applicants", layout = MainLayout.class)
@PageTitle("Applicant Management")
@RolesAllowed({"ADMIN", "RECRUITER"})
public class ApplicantView extends VerticalLayout {

    private static final int GRID_PAGE_SIZE = 50;

    private final ApplicantService applicantService;
    private final BookingService bookingService;
    private final ScheduleService scheduleService;
    private final PositionOpeningService positionOpeningService;
    private final SecurityService securityService;
    private final BranchService branchService;

    private Grid<Applicant> applicantGrid;
    private CallbackDataProvider<Applicant, Void> dataProvider;

    private HorizontalLayout filterLayout;
    private TextField searchField;
    private ComboBox<ApplicantStatus> statusFilter;
    private Button searchButton;

    private HorizontalLayout actionLayout;
    private Button addButton;
    private Button editButton;
    private Button bookButton;

    public ApplicantView(
            ApplicantService applicantService,
            BookingService bookingService,
            ScheduleService scheduleService,
            PositionOpeningService positionOpeningService,
            SecurityService securityService,
            BranchService branchService
    ) {
        this.applicantService = applicantService;
        this.bookingService = bookingService;
        this.scheduleService = scheduleService;
        this.positionOpeningService = positionOpeningService;
        this.securityService = securityService;
        this.branchService = branchService;

        setSizeFull();

        filterLayout = new HorizontalLayout();

        searchField = new TextField();
        searchField.setPlaceholder("Search Applicant");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(event -> refreshGrid());

        statusFilter = new ComboBox<>();
        statusFilter.setPlaceholder("Applicant Status");
        statusFilter.setItems(ApplicantStatus.values());
        statusFilter.setClearButtonVisible(true);
        statusFilter.addValueChangeListener(event -> refreshGrid());

        searchButton = new Button("Search");
        searchButton.setIcon(VaadinIcon.SEARCH.create());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> refreshGrid());

        filterLayout.add(searchField, statusFilter, searchButton);
        filterLayout.setWidthFull();
        filterLayout.setJustifyContentMode(JustifyContentMode.END);

        applicantGrid = new Grid<>();
        applicantGrid.setHeightFull();
        applicantGrid.setWidth("100%");
        applicantGrid.setPageSize(GRID_PAGE_SIZE);

        applicantGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS, GridVariant.LUMO_COMPACT);

        applicantGrid.addColumn(Applicant::getFullName).setHeader("Full Name").setWidth("220px").setResizable(true);
        applicantGrid.addColumn(Applicant::getEmail).setHeader("Email").setWidth("220px").setResizable(true);
        applicantGrid.addColumn(Applicant::getMobileNumber).setHeader("Mobile").setWidth("150px").setResizable(true);
        applicantGrid.addColumn(o -> o.getBranch() == null ? "Unassigned" : o.getBranch().getBranchName()).setHeader("Branch").setWidth("160px").setResizable(true);
        applicantGrid.addColumn(this::positionTitle).setHeader("Position").setWidth("180px").setResizable(true);
        applicantGrid.addColumn(this::clientName).setHeader("Client").setWidth("220px").setResizable(true);
        applicantGrid.addColumn(this::workLocation).setHeader("Work Location").setWidth("220px").setResizable(true);
        applicantGrid.addColumn(o -> o.getStatus() == null ? "" : o.getStatus().name()).setHeader("Status").setWidth("140px").setResizable(true);
        applicantGrid.addColumn(o -> o.isActive() ? "Active" : "Inactive").setHeader("Record Status").setWidth("130px").setResizable(true);
        applicantGrid.addComponentColumn(applicant -> {

            Button toggle = new Button(applicant.isActive() ? "Deactivate" : "Activate");

            if (applicant.isActive()) {
                toggle.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            } else {
                toggle.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            }

            toggle.addClickListener(e -> {
                if (applicant.isActive()) {
                    applicantService.deactivate(applicant.getId());
                } else {
                    applicantService.activate(applicant.getId());
                }

                refreshGrid();
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
        addButton.addClickListener(e -> openDialog(new Applicant()));

        editButton = new Button("Edit");
        editButton.setIcon(VaadinIcon.PENCIL.create());
        editButton.addClickListener(e -> onEdit());
        bookButton = new Button("Book Schedule");
        bookButton.setIcon(VaadinIcon.CALENDAR.create());
        bookButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        bookButton.addClickListener(e -> onBook());

        actionLayout.add(addButton, editButton, bookButton);

        dataProvider = DataProvider.fromCallbacks(
                query -> applicantService.findGridPage(
                        currentFilter(), query.getPage(), query.getPageSize()
                ).stream(),
                query -> toIntCount(applicantService.countGrid(currentFilter()))
        );
        applicantGrid.setDataProvider(dataProvider);

        add(filterLayout, applicantGrid, actionLayout);
    }

    private ApplicantGridFilter currentFilter() {
        return new ApplicantGridFilter(searchField.getValue(), statusFilter.getValue());
    }

    private void refreshGrid() {
        applicantGrid.deselectAll();
        dataProvider.refreshAll();
    }

    private void onEdit() {
        Applicant selected = applicantGrid.asSingleSelect().getValue();

        if (selected == null) {
            Notification.show("Please select an applicant first.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        openDialog(selected);
    }

    private void openDialog(Applicant applicant) {
        var actor = securityService.requireOperationsUser();
        ApplicantFormDialog dialog = new ApplicantFormDialog(
                applicant,
                positionOpeningService,
                actor,
                actor.getRole() == Role.ADMIN ? branchService.findAll() : java.util.List.of(actor.getBranch()),
                savedApplicant -> {
            try {
                applicantService.save(savedApplicant);

                Notification.show("Applicant saved successfully.", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                refreshGrid();

            } catch (Exception ex) {
                UserSafeNotifier.showError(ex);
            }
                });

        dialog.open();
    }

    private void openBookingDialog(Applicant applicant) {

        if (!applicant.isActive()) {
            Notification.show("Inactive applicant cannot be booked.", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_WARNING);

            return;
        }

        BookingFormDialog dialog = new BookingFormDialog(applicant, scheduleService, (schedule, remarks) -> {
            try {
                bookingService.createBooking(applicant.getId(), schedule.getId(), remarks);

                Notification.show("Interview booked successfully.", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                refreshGrid();

            } catch (Exception ex) {
                UserSafeNotifier.showError(ex);
            }
        });

        dialog.open();
    }

    private void onBook() {
        Applicant selected = applicantGrid.asSingleSelect().getValue();

        if (selected == null) {
            Notification.show("Please select an applicant first.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_WARNING);

            return;
        }

        openBookingDialog(selected);
    }

    private int toIntCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private String positionTitle(Applicant applicant) {
        return applicant.getPositionOpening() == null ? "" : applicant.getPositionOpening().getTitle();
    }

    private String clientName(Applicant applicant) {
        return applicant.getPositionOpening() == null || applicant.getPositionOpening().getClient() == null
                ? ""
                : applicant.getPositionOpening().getClient().getCompanyName();
    }

    private String workLocation(Applicant applicant) {
        return applicant.getPositionOpening() == null ? "" : applicant.getPositionOpening().getWorkLocation();
    }
}
