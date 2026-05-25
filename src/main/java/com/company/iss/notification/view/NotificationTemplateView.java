package com.company.iss.notification.view;

import com.company.iss.notification.dialog.NotificationTemplateFormDialog;
import com.company.iss.notification.entity.NotificationTemplate;
import com.company.iss.notification.service.NotificationTemplateService;
import com.company.iss.shared.view.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Autowired;

import static com.vaadin.flow.component.grid.ColumnTextAlign.CENTER;

@Route(value = "notification-templates", layout = MainLayout.class)
@PageTitle("Notification Templates")
@RolesAllowed("ADMIN")
public class NotificationTemplateView extends VerticalLayout {

    @Autowired
    private NotificationTemplateService notificationTemplateService;

    private Grid<NotificationTemplate> templateGrid;

    public NotificationTemplateView() {
        setSizeFull();

        templateGrid = new Grid<>();
        templateGrid.setSizeFull();

        templateGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS, GridVariant.LUMO_COMPACT);

        templateGrid.addColumn(o -> o.getEvent().name()).setHeader("Event").setWidth("220px").setResizable(true);

        templateGrid.addColumn(o -> o.getChannel().name()).setHeader("Channel").setWidth("140px").setTextAlign(CENTER).setResizable(true);

        templateGrid.addColumn(NotificationTemplate::getSubject).setHeader("Subject").setWidth("320px").setResizable(true);

        templateGrid.addColumn(o -> o.getActive() ? "Active" : "Inactive").setHeader("Status").setWidth("140px").setTextAlign(CENTER).setResizable(true);

        templateGrid.addComponentColumn(template -> {

            Button editButton = new Button("Edit");

            editButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

            editButton.addClickListener(e -> openDialog(template));

            HorizontalLayout actions = new HorizontalLayout(editButton);

            actions.setWidthFull();
            actions.setJustifyContentMode(JustifyContentMode.CENTER);
            actions.setAlignItems(Alignment.CENTER);

            return actions;

        }).setHeader("Actions").setWidth("160px").setResizable(true);

        add(templateGrid);
    }

    @PostConstruct
    private void init() {
        templateGrid.setItems(notificationTemplateService.findAll());
    }

    private void openDialog(NotificationTemplate template) {
        NotificationTemplateFormDialog dialog = new NotificationTemplateFormDialog(template, saved -> {
            try {
                notificationTemplateService.save(saved);

                init();

            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 4000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        dialog.open();
    }
}