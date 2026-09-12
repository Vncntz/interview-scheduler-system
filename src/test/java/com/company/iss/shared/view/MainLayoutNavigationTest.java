package com.company.iss.shared.view;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.vaadin.flow.component.sidenav.SideNav;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainLayoutNavigationTest {

    @Test
    void reminderDeliveryHealthNavigationIsVisibleOnlyToAdministrators() throws Exception {
        MainLayout layout = new MainLayout();

        SideNav adminNavigation = navigation(layout, user(Role.ADMIN));
        SideNav recruiterNavigation = navigation(layout, user(Role.RECRUITER));

        assertTrue(adminNavigation.getItems().stream().anyMatch(
                item -> "Reminder Delivery Health".equals(item.getLabel())
                        && item.getPath().endsWith("reminder-delivery-health")
        ));
        assertFalse(recruiterNavigation.getItems().stream().anyMatch(
                item -> "Reminder Delivery Health".equals(item.getLabel())
        ));
    }

    private SideNav navigation(MainLayout layout, User user) throws Exception {
        Method createSideNav = MainLayout.class.getDeclaredMethod("createSideNav", User.class);
        createSideNav.setAccessible(true);
        return (SideNav) createSideNav.invoke(layout, user);
    }

    private User user(Role role) {
        User user = new User();
        user.setRole(role);
        return user;
    }
}
