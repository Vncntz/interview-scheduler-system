package com.company.iss.shared.view;

import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

@PermitAll
public class MainLayout extends AppLayout {

    private SecurityService securityService;

    public MainLayout() {
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
        initializeLayout();
    }

    private void initializeLayout() {
        User currentUser = securityService.getCurrentUser();

        createNavbar(currentUser);
        createDrawer(currentUser);

        setPrimarySection(Section.DRAWER);
    }

    private void createNavbar(User user) {
        DrawerToggle toggle = new DrawerToggle();

        Span pageTitle = new Span("Interview Scheduler System");
        pageTitle.getStyle().set("font-size", "1rem").set("font-weight", "600");

        MenuBar profileMenu = new MenuBar();
        MenuItem userMenu = profileMenu.addItem(user.getFullName());

        userMenu.getSubMenu().addItem("My Profile");
        userMenu.getSubMenu().addItem("Change Password");
        userMenu.getSubMenu().addItem("Logout", e -> securityService.logout());

        HorizontalLayout navbar = new HorizontalLayout(toggle, pageTitle, profileMenu);

        navbar.setWidthFull();
        navbar.expand(pageTitle);
        navbar.setAlignItems(FlexComponent.Alignment.CENTER);
        navbar.setPadding(true);
        navbar.getStyle().set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

        addToNavbar(navbar);
    }

    private void createDrawer(User user) {
        VerticalLayout drawerContent = new VerticalLayout();
        drawerContent.setPadding(false);
        drawerContent.setSpacing(false);
        drawerContent.setSizeFull();

        drawerContent.add(createUserCard(user), createSideNav());

        Scroller scroller = new Scroller(drawerContent);
        scroller.setSizeFull();

        addToDrawer(scroller);
    }



    private HorizontalLayout createUserCard(User user) {
        Avatar avatar = new Avatar(user.getFullName());
        avatar.setThemeName("small");

        VerticalLayout details = new VerticalLayout();
        details.setSpacing(false);
        details.setPadding(false);

        Span name = new Span(user.getFullName());
        name.getStyle().set("font-weight", "600");

        Span email = new Span(user.getEmail());
        email.getStyle().set("font-size", "0.75rem").set("color", "var(--lumo-secondary-text-color)");

        Span role = new Span("ROLE: " +user.getRole().name());
        role.getStyle().set("font-size", "0.7rem").set("padding", "2px 8px").set("border-radius", "12px").set("background", "var(--lumo-contrast-10pct)");

        details.add(name, email, role);

        HorizontalLayout userCard = new HorizontalLayout(avatar, details);
        userCard.setWidthFull();
        userCard.setAlignItems(FlexComponent.Alignment.CENTER);
        userCard.setPadding(true);

        return userCard;
    }

    private SideNav createSideNav() {
        SideNav nav = new SideNav();

        nav.addItem(new SideNavItem("Dashboard", "/", VaadinIcon.DASHBOARD.create()), new SideNavItem("Recruiters", "/recruiters", VaadinIcon.USERS.create()), new SideNavItem("Branches", "/branches", VaadinIcon.OFFICE.create()), new SideNavItem("Applicants", "/applicants", VaadinIcon.USER_CARD.create()), new SideNavItem("Scheduling", "/scheduling", VaadinIcon.CALENDAR.create()), new SideNavItem("Bookings", "/bookings", VaadinIcon.CLIPBOARD_CHECK.create()), new SideNavItem("Audit Logs", "/audit", VaadinIcon.FILE_TEXT.create()), new SideNavItem("Settings", "/settings", VaadinIcon.COG.create()));

        nav.setWidthFull();

        return nav;
    }
}