package com.company.iss.hiring.view;

import com.company.iss.shared.view.MainLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HiringDecisionRouteSecurityTest {

    @Test
    void routeIsDedicatedToOperationsRolesAndUsesMainLayout() {
        Route route = HiringDecisionView.class.getAnnotation(Route.class);
        assertEquals("hiring-decisions", route.value());
        assertEquals(MainLayout.class, route.layout());
        assertEquals("Final Hiring Decisions", HiringDecisionView.class.getAnnotation(PageTitle.class).value());
        assertEquals(
                List.of("ADMIN", "RECRUITER"),
                List.of(HiringDecisionView.class.getAnnotation(RolesAllowed.class).value())
        );
    }
}
