package com.company.iss.auth.view;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route("")
@PageTitle("Interview Scheduler System")
@RolesAllowed({"ADMIN", "RECRUITER"})
public class RoleLandingView extends Div implements BeforeEnterObserver {

    private final SecurityService securityService;

    public RoleLandingView(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        User user = securityService.requireOperationsUser();
        event.forwardTo(user.getRole() == Role.ADMIN ? "dashboard" : "workbench");
    }
}
