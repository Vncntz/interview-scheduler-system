package com.company.iss.dashboard.view;

import com.company.iss.auth.view.RoleLandingView;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkbenchRouteSecurityTest {

    @Test
    void routesAndRolesExposeSeparateAdminAndRecruiterLandings() {
        assertEquals("dashboard", DashboardView.class.getAnnotation(Route.class).value());
        assertEquals(List.of("ADMIN"), List.of(DashboardView.class.getAnnotation(RolesAllowed.class).value()));
        assertEquals("workbench", RecruiterWorkbenchView.class.getAnnotation(Route.class).value());
        assertEquals(List.of("RECRUITER"), List.of(RecruiterWorkbenchView.class.getAnnotation(RolesAllowed.class).value()));
        assertEquals("", RoleLandingView.class.getAnnotation(Route.class).value());
        assertEquals(
                List.of("ADMIN", "RECRUITER"),
                List.of(RoleLandingView.class.getAnnotation(RolesAllowed.class).value())
        );
    }
}
