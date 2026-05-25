package com.company.iss.client.view;

import com.company.iss.client.dialog.ClientFormDialog;
import com.company.iss.client.entity.Client;
import com.company.iss.client.service.ClientService;
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

@Route(value = "clients", layout = MainLayout.class)
@PageTitle("Client Management")
@RolesAllowed("ADMIN")
public class ClientView extends VerticalLayout {

    @Autowired
    private ClientService clientService;

    private Grid<Client> clientGrid;

    private HorizontalLayout filterLayout;
    private TextField searchField;
    private Button searchButton;

    private HorizontalLayout actionLayout;
    private Button addButton;
    private Button editButton;

    public ClientView() {
        setSizeFull();

        filterLayout = new HorizontalLayout();

        searchField = new TextField();
        searchField.setPlaceholder("Search Client");

        searchButton = new Button("Search");
        searchButton.setIcon(VaadinIcon.SEARCH.create());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> onSearch(searchField.getValue()));

        filterLayout.add(searchField, searchButton);
        filterLayout.setWidthFull();
        filterLayout.setJustifyContentMode(JustifyContentMode.END);

        clientGrid = new Grid<>();
        clientGrid.setHeightFull();
        clientGrid.setWidth("100%");

        clientGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS, GridVariant.LUMO_COMPACT);

        clientGrid.addColumn(Client::getCompanyName).setHeader("Company Name").setWidth("220px").setResizable(true);

        clientGrid.addColumn(Client::getAddress).setHeader("Address").setWidth("280px").setResizable(true);

        clientGrid.addColumn(Client::getContactPerson).setHeader("Contact Person").setWidth("180px").setResizable(true);

        clientGrid.addColumn(Client::getContactNumber).setHeader("Contact Number").setWidth("150px").setResizable(true);

        clientGrid.addColumn(Client::getEmail).setHeader("Email").setWidth("220px").setResizable(true);

        clientGrid.addColumn(o -> o.isActive() ? "Active" : "Inactive").setHeader("Status").setWidth("120px").setResizable(true);

        clientGrid.addComponentColumn(client -> {
            Button toggle = new Button(client.isActive() ? "Deactivate" : "Activate");

            if (client.isActive()) {
                toggle.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            } else {
                toggle.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            }

            toggle.addClickListener(e -> {
                if (client.isActive()) {
                    clientService.deactivate(client);
                } else {
                    clientService.activate(client);
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
        addButton.addClickListener(e -> openDialog(new Client()));

        editButton = new Button("Edit");
        editButton.setIcon(VaadinIcon.PENCIL.create());
        editButton.addClickListener(e -> onEdit());

        actionLayout.add(addButton, editButton);

        add(filterLayout, clientGrid, actionLayout);
    }

    private void onSearch(String value) {
        clientGrid.setItems(clientService.search(value));
    }

    @PostConstruct
    private void init() {
        clientGrid.setItems(clientService.search(null));
    }

    private void onEdit() {
        Client selected = clientGrid.asSingleSelect().getValue();

        if (selected == null) {
            Notification.show("Please select a client first.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        openDialog(selected);
    }

    private void openDialog(Client client) {
        ClientFormDialog dialog = new ClientFormDialog(client, savedClient -> {
            try {
                clientService.save(savedClient);

                Notification.show("Client saved successfully.", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                init();

            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 5000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        dialog.open();
    }
}