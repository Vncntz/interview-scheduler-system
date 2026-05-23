package com.company.iss.dashboard.view;

import com.company.iss.shared.view.MainLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class DashboardView extends VerticalLayout {

    public DashboardView() {
        add(new H2("Dashboard"));
    }
}