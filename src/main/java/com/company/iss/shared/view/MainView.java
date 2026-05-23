package com.company.iss.shared.view;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

@Route("")
@RolesAllowed("ADMIN")
public class MainView extends VerticalLayout {

    public MainView() {
        add(new H1("ISS Dashboard"));
    }
}