package com.company.iss.schedule.view;

import com.company.iss.branch.service.BranchService;
import com.company.iss.recruiter.service.RecruiterService;
import com.company.iss.schedule.dialog.BulkScheduleDialog;
import com.company.iss.schedule.dialog.ScheduleFormDialog;
import com.company.iss.schedule.dto.ScheduleGridFilter;
import com.company.iss.schedule.dto.ScheduleGridSort;
import com.company.iss.schedule.dto.ScheduleGridSortOrder;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.service.ScheduleService;
import com.company.iss.shared.util.DateTimeUtil;
import com.company.iss.shared.view.MainLayout;
import com.company.iss.shared.view.UserSafeNotifier;
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
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "scheduling", layout = MainLayout.class)
@PageTitle("Schedule Management")
@RolesAllowed("ADMIN")
public class ScheduleView extends VerticalLayout {

    private static final int GRID_PAGE_SIZE = 50;

    private final ScheduleService scheduleService;
    private final BranchService branchService;
    private final RecruiterService recruiterService;

    private Grid<Schedule> scheduleGrid;
    private CallbackDataProvider<Schedule, Void> dataProvider;

    private HorizontalLayout filterLayout;
    private TextField searchField;
    private Button searchButton;

    private HorizontalLayout actionLayout;
    private Button addButton;
    private Button editButton;
    private Button bulkGenerateButton;
    private Button deleteButton;

    public ScheduleView(
            ScheduleService scheduleService,
            BranchService branchService,
            RecruiterService recruiterService
    ) {
        this.scheduleService = scheduleService;
        this.branchService = branchService;
        this.recruiterService = recruiterService;
        setSizeFull();

        filterLayout = new HorizontalLayout();

        searchField = new TextField();
        searchField.setPlaceholder("Search Schedule");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(event -> refreshGrid());

        searchButton = new Button("Search");
        searchButton.setIcon(VaadinIcon.SEARCH.create());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> refreshGrid());

        filterLayout.add(searchField, searchButton);
        filterLayout.setWidthFull();
        filterLayout.setJustifyContentMode(JustifyContentMode.END);

        scheduleGrid = new Grid<>();
        scheduleGrid.setHeightFull();
        scheduleGrid.setWidth("100%");
        scheduleGrid.setPageSize(GRID_PAGE_SIZE);
        scheduleGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS, GridVariant.LUMO_COMPACT);

        scheduleGrid.addColumn(Schedule::getScheduleDate).setHeader("Date").setSortProperty("date").setWidth("80px").setResizable(true);
        scheduleGrid.addColumn(o -> DateTimeUtil.formatTime(o.getStartTime())).setHeader("Start").setSortProperty("startTime").setWidth("70px").setResizable(true);
        scheduleGrid.addColumn(o -> DateTimeUtil.formatTime(o.getEndTime())).setHeader("End").setSortProperty("endTime").setWidth("70px").setResizable(true);
        scheduleGrid.addColumn(o -> o.getBranch() == null ? "" : o.getBranch().getBranchName()).setHeader("Branch").setSortProperty("branch").setWidth("150px").setResizable(true);
        scheduleGrid.addColumn(o -> o.getRecruiter() == null ? "" : o.getRecruiter().getFullName()).setHeader("Recruiter").setSortProperty("recruiter").setWidth("150px").setResizable(true);
        scheduleGrid.addColumn(o -> o.getInterviewMode() == null ? "" : o.getInterviewMode().name()).setHeader("Mode").setSortProperty("mode").setWidth("50px").setResizable(true);
        scheduleGrid.addColumn(Schedule::getSlotCapacity).setHeader("Capacity").setSortProperty("capacity").setWidth("50px").setResizable(true);
        scheduleGrid.addColumn(Schedule::getBookedCount).setHeader("Booked").setSortProperty("booked").setWidth("50px").setResizable(true);
        scheduleGrid.addColumn(o -> o.getStatus() == null ? "" : o.getStatus().name()).setHeader("Status").setSortProperty("status").setWidth("60px").setResizable(true);
        scheduleGrid.addComponentColumn(schedule -> {

            HorizontalLayout actions = new HorizontalLayout();
            actions.setWidthFull();
            actions.setJustifyContentMode(JustifyContentMode.CENTER);
            actions.setAlignItems(Alignment.CENTER);

            if (schedule.getStatus() == ScheduleStatus.OPEN) {

                Button closeButton = new Button("Close");
                closeButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

                closeButton.addClickListener(e -> {
                    scheduleService.close(schedule.getId());
                    refreshGrid();
                });

                Button cancelButton = new Button("Cancel");
                cancelButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);

                cancelButton.addClickListener(e -> {
                    try {
                        scheduleService.cancel(schedule.getId());
                        refreshGrid();
                    } catch (Exception ex) {
                        UserSafeNotifier.showError(ex);
                    }
                });

                actions.add(closeButton, cancelButton);

            } else if (schedule.getStatus() == ScheduleStatus.CLOSED) {

                Button reopenButton = new Button("Reopen");
                reopenButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

                reopenButton.addClickListener(e -> {
                    scheduleService.reopen(schedule.getId());
                    refreshGrid();
                });

                Button cancelButton = new Button("Cancel");
                cancelButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);

                cancelButton.addClickListener(e -> {
                    try {
                        scheduleService.cancel(schedule.getId());
                        refreshGrid();
                    } catch (Exception ex) {
                        UserSafeNotifier.showError(ex);
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

        dataProvider = DataProvider.fromCallbacks(
                query -> scheduleService.findGridPage(
                        currentFilter(), query.getOffset(), query.getLimit(), mapSortOrders(query.getSortOrders())
                ).stream(),
                query -> toIntCount(scheduleService.countGrid(currentFilter()))
        );
        scheduleGrid.setDataProvider(dataProvider);

        add(filterLayout, scheduleGrid, actionLayout);
    }

    private void onDelete() {
        Schedule selected = scheduleGrid.asSingleSelect().getValue();

        if (selected == null) {
            Notification.show("Please select a schedule first.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        try {
            scheduleService.delete(selected.getId());

            Notification.show("Schedule deleted successfully.", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            refreshGrid();

        } catch (Exception ex) {
            UserSafeNotifier.showError(ex);
        }
    }

    private void openBulkDialog() {
        BulkScheduleDialog dialog = new BulkScheduleDialog(
                branchService, recruiterService, scheduleService, this::refreshGrid
        );
        dialog.open();
    }

    private void onEdit() {
        Schedule selected = scheduleGrid.asSingleSelect().getValue();

        if (selected != null) {
            openDialog(selected);
        } else {
            Notification.show("Please select a schedule first.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_WARNING);
        }
    }

    private ScheduleGridFilter currentFilter() {
        return new ScheduleGridFilter(searchField.getValue());
    }

    private java.util.List<ScheduleGridSortOrder> mapSortOrders(
            java.util.List<com.vaadin.flow.data.provider.QuerySortOrder> sortOrders
    ) {
        return sortOrders.stream()
                .map(order -> ScheduleGridSort.fromKey(order.getSorted())
                        .map(field -> new ScheduleGridSortOrder(
                                field,
                                order.getDirection() == SortDirection.ASCENDING
                                        ? org.springframework.data.domain.Sort.Direction.ASC
                                        : org.springframework.data.domain.Sort.Direction.DESC
                        )))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    private void refreshGrid() {
        scheduleGrid.deselectAll();
        dataProvider.refreshAll();
    }

    private int toIntCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private void openDialog(Schedule schedule) {
        ScheduleFormDialog dialog = new ScheduleFormDialog(schedule, branchService, recruiterService, savedSchedule -> {
            try {
                scheduleService.save(savedSchedule);

                Notification.show("Schedule saved successfully.", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                refreshGrid();

            } catch (Exception ex) {
                UserSafeNotifier.showError(ex);
            }
        });

        dialog.open();
    }
}
