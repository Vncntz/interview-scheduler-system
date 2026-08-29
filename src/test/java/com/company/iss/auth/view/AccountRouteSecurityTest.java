package com.company.iss.auth.view;

import com.company.iss.shared.view.MainLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AccountRouteSecurityTest {

    @Test
    void profileAndChangePasswordAreOperationsOnlyAndResetIsAnonymous() {
        Route profile = ProfileView.class.getAnnotation(Route.class);
        assertEquals("profile", profile.value());
        assertEquals(MainLayout.class, profile.layout());
        assertEquals(List.of("ADMIN", "RECRUITER"), List.of(ProfileView.class.getAnnotation(RolesAllowed.class).value()));

        Route change = ChangePasswordView.class.getAnnotation(Route.class);
        assertEquals("change-password", change.value());
        assertEquals(List.of("ADMIN", "RECRUITER"), List.of(ChangePasswordView.class.getAnnotation(RolesAllowed.class).value()));

        assertEquals("reset-password", ResetPasswordView.class.getAnnotation(Route.class).value());
        assertNotNull(ResetPasswordView.class.getAnnotation(AnonymousAllowed.class));
    }
}
