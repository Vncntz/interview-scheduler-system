package com.company.iss.schedule.view;

import com.company.iss.branch.service.BranchService;
import com.company.iss.recruiter.service.RecruiterService;
import com.company.iss.schedule.dialog.BulkScheduleDialog;
import com.company.iss.schedule.dialog.ScheduleFormDialog;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.service.ScheduleService;
import com.company.iss.shared.util.DateTimeUtil;
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

@Route(value = "scheduling", layout = MainLayout.class)
@PageTitle("Schedule Management")
@RolesAllowed("ADMIN")
public class ScheduleView extends VerticalLayout {

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private BranchService branchService;

    @Autowired
    private RecruiterService recruiterService;

    private Grid<Schedule> scheduleGrid;

    private HorizontalLayout filterLayout;
    private TextField searchField;
    private Button searchButton;

    private HorizontalLayout actionLayout;
    private Button addButton;
    private Button editButton;
    private Button bulkGenerateButton;
    private Button deleteButton;

    public ScheduleView() {
        setSizeFull();

        filterLayout = new HorizontalLayout();

        searchField = new TextField();
        searchField.setPlaceholder("Search Schedule");

        searchButton = new Button("Search");
        searchButton.setIcon(VaadinIcon.SEARCH.create());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> onSearch(searchField.getValue()));

        filterLayout.add(searchField, searchButton);
        filterLayout.setWidthFull();
        filterLayout.setJustifyContentMode(JustifyContentMode.END);

        scheduleGrid = new Grid<>();
        scheduleGrid.setHeightFull();
        scheduleGrid.setWidth("100%");
        scheduleGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS, GridVariant.LUMO_COMPACT);

        scheduleGrid.addColumn(o -> o.getScheduleDate()).setHeader("Date").setWidth("80px").setResizable(true);
        scheduleGrid.addColumn(o -> DateTimeUtil.formatTime(o.getStartTime())).setHeader("Start").setWidth("70px").setResizable(true);
        scheduleGrid.addColumn(o -> DateTimeUtil.formatTime(o.getEndTime())).setHeader("End").setWidth("70px").setResizable(true);
        scheduleGrid.addColumn(o -> o.getBranch().getBranchName()).setHeader("Branch").setWidth("150px").setResizable(true);
        scheduleGrid.addColumn(o -> o.getRecruiter().getFullName()).setHeader("Recruiter").setWidth("150px").setResizable(true);
        scheduleGrid.addColumn(o -> o.getInterviewMode().name()).setHeader("Mode").setWidth("50px").setResizable(true);
        scheduleGrid.addColumn(o -> o.getSlotCapacity()).setHeader("Capacity").setWidth("50px").setResizable(true);
        scheduleGrid.addColumn(o -> o.getBookedCount()).setHeader("Booked").setWidth("50px").setResizable(true);
        scheduleGrid.addColumn(o -> o.getStatus().name()).setHeader("Status").setWidth("60px").setResizable(true);
        scheduleGrid.addComponentColumn(schedule -> {

            HorizontalLayout actions = new HorizontalLayout();
            actions.setWidthFull();
            actions.setJustifyContentMode(JustifyContentMode.CENTER);
            actions.setAlignItems(Alignment.CENTER);

            if (schedule.getStatus() == ScheduleStatus.OPEN) {

                Button closeButton = new Button("Close");
                closeButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

                closeButton.addClickListener(e -> {
                    scheduleService.close(schedule);
                    init();
                });

                Button cancelButton = new Button("Cancel");
                cancelButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);

                cancelButton.addClickListener(e -> {
                    try {
                        scheduleService.cancel(schedule);
                        init();
                    } catch (Exception ex) {
                        Notification.show(ex.getMessage(), 5000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
                    }
                });

                actions.add(closeButton, cancelButton);

            } else if (schedule.getStatus() == ScheduleStatus.CLOSED) {

                Button reopenButton = new Button("Reopen");
                reopenButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

                reopenButton.addClickListener(e -> {
                    scheduleService.reopen(schedule);
                    init();
                });

                Button cancelButton = new Button("Cancel");
                cancelButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);

                cancelButton.addClickListener(e -> {
                    try {
                        scheduleService.cancel(schedule);
                        init();
                    } catch (Exception ex) {
                        Notification.show(ex.getMessage(), 5000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
                    }
                });

                actions.add(reopenButton, cancelButton);

            } else if (schedule.getStatus() == ScheduleStatus.FULL) {

                Button fullButton = new Button("Full");
                fullButton.setEnabled(false);

                actions.add(fullButton);

            } else if (schedule.getStatus() == ScheduleStatus.CANCELLED) {

                Button cancelledButton = new Button("Cancelled");
                cancelledButton.setEnabled(false);

                actions.add(cancelledButton);
            }

            return actions;

        }).setHeader("Actions");

        actionLayout = new HorizontalLayout();
        actionLayout.setWidthFull();

        addButton = new Button("Add");
        addButton.setIcon(VaadinIcon.PLUS.create());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(e -> openDialog(new Schedule()));

        editButton = new Button("Edit");
        editButton.setIcon(VaadinIcon.PENCIL.create());
        editButton.addClickListener(e -> onEdit());

        bulkGenerateButton = new Button("Bulk Generate");
        bulkGenerateButton.setIcon(VaadinIcon.CALENDAR.create());
        bulkGenerateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        bulkGenerateButton.addClickListener(e -> {
            openBulkDialog();
        });

        deleteButton = new Button("Delete");
        deleteButton.setIcon(VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteButton.addClickListener(e -> onDelete());

        actionLayout.add(addButton, editButton, bulkGenerateButton, deleteButton);

        add(filterLayout, scheduleGrid, actionLayout);
    }

    private void onDelete() {
        Schedule selected = scheduleGrid.asSingleSelect().getValue();

        if (selected == null) {
            Notification.show("Please select a schedule first.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        try {
            scheduleService.delete(selected);

            Notification.show("Schedule deleted successfully.", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            init();

        } catch (Exception ex) {
            Notification.show(ex.getMessage(), 5000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openBulkDialog() {
        BulkScheduleDialog dialog = new BulkScheduleDialog(branchService, recruiterService, scheduleService, this::init);
        dialog.open();
    }

    private void onSearch(String value) {
        scheduleGrid.setItems(scheduleService.search(value));
    }

    private void onEdit() {
        Schedule selected = scheduleGrid.asSingleSelect().getValue();

        if (selected != null) {
            openDialog(selected);
        } else {
            Notification.show("Please select a schedule first.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_WARNING);
        }
    }

    @PostConstruct
    private void init() {
        scheduleGrid.setItems(scheduleService.search(null));
    }

    private void openDialog(Schedule schedule) {
        ScheduleFormDialog dialog = new ScheduleFormDialog(schedule, branchService, recruiterService, savedSchedule -> {
            try {
                scheduleService.save(savedSchedule);

                Notification.show("Schedule saved successfully.", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                init();

            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 5000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        dialog.open();
    }
}