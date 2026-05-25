package com.company.iss.applicant.view;

import com.company.iss.applicant.dialog.ApplicantFormDialog;
import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.service.ApplicantService;
import com.company.iss.booking.dialog.BookingFormDialog;
import com.company.iss.booking.service.BookingService;
import com.company.iss.position.service.PositionOpeningService;
import com.company.iss.schedule.service.ScheduleService;
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

@Route(value = "applicants", layout = MainLayout.class)
@PageTitle("Applicant Management")
@RolesAllowed({"ADMIN", "RECRUITER"})
public class ApplicantView extends VerticalLayout {

    @Autowired
    private ApplicantService applicantService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private ScheduleService scheduleService;
    @Autowired
    private PositionOpeningService positionOpeningService;

    private Grid<Applicant> applicantGrid;

    private HorizontalLayout filterLayout;
    private TextField searchField;
    private Button searchButton;

    private HorizontalLayout actionLayout;
    private Button addButton;
    private Button editButton;
    private Button bookButton;

    public ApplicantView() {
        setSizeFull();

        filterLayout = new HorizontalLayout();

        searchField = new TextField();
        searchField.setPlaceholder("Search Applicant");

        searchButton = new Button("Search");
        searchButton.setIcon(VaadinIcon.SEARCH.create());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> onSearch(searchField.getValue()));

        filterLayout.add(searchField, searchButton);
        filterLayout.setWidthFull();
        filterLayout.setJustifyContentMode(JustifyContentMode.END);

        applicantGrid = new Grid<>();
        applicantGrid.setHeightFull();
        applicantGrid.setWidth("100%");

        applicantGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS, GridVariant.LUMO_COMPACT);

        applicantGrid.addColumn(Applicant::getFullName).setHeader("Full Name").setWidth("220px").setResizable(true);
        applicantGrid.addColumn(Applicant::getEmail).setHeader("Email").setWidth("220px").setResizable(true);
        applicantGrid.addColumn(Applicant::getMobileNumber).setHeader("Mobile").setWidth("150px").setResizable(true);
        applicantGrid.addColumn(o -> o.getPositionOpening().getTitle()).setHeader("Position").setWidth("180px").setResizable(true);
        applicantGrid.addColumn(o -> o.getPositionOpening().getClient().getCompanyName()).setHeader("Client").setWidth("220px").setResizable(true);
        applicantGrid.addColumn(o -> o.getPositionOpening().getWorkLocation()).setHeader("Work Location").setWidth("220px").setResizable(true);
        applicantGrid.addColumn(o -> o.getStatus().name()).setHeader("Status").setWidth("140px").setResizable(true);
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
                    applicantService.deactivate(applicant);
                } else {
                    applicantService.activate(applicant);
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
        addButton.addClickListener(e -> openDialog(new Applicant()));

        editButton = new Button("Edit");
        editButton.setIcon(VaadinIcon.PENCIL.create());
        editButton.addClickListener(e -> onEdit());
        bookButton = new Button("Book Schedule");
        bookButton.setIcon(VaadinIcon.CALENDAR.create());
        bookButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        bookButton.addClickListener(e -> onBook());

        actionLayout.add(addButton, editButton, bookButton);

        add(filterLayout, applicantGrid, actionLayout);
    }

    private void onSearch(String value) {
        applicantGrid.setItems(applicantService.search(value));
    }

    @PostConstruct
    private void init() {
        applicantGrid.setItems(applicantService.search(null));
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
        ApplicantFormDialog dialog = new ApplicantFormDialog(applicant, positionOpeningService, savedApplicant -> {
            try {
                applicantService.save(savedApplicant);

                Notification.show("Applicant saved successfully.", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                init();

            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 5000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
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
                bookingService.createBooking(applicant, schedule, remarks);

                Notification.show("Interview booked successfully.", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                init();

            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 4000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
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
}