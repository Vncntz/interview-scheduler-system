package com.company.iss.auth.view;

import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.shared.view.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.time.Clock;
import java.time.LocalDateTime;

@Route(value = "profile", layout = MainLayout.class)
@PageTitle("My Profile")
@RolesAllowed({"ADMIN", "RECRUITER"})
public class ProfileView extends VerticalLayout {

    public ProfileView(SecurityService securityService, Clock clock) {
        User user = securityService.requireAuthenticatedActiveUser();
        FormLayout form = new FormLayout(
                readOnly("Full name", user.getFullName()),
                readOnly("Email", user.getEmail()),
                readOnly("Role", user.getRole().name()),
                readOnly("Branch", user.getBranch() == null ? "—" : user.getBranch().getBranchName()),
                readOnly("Account state", accountState(user, clock)),
                readOnly("Password change required", user.isMustChangePassword() ? "Yes" : "No"),
                readOnly("Last login", user.getLastLoginAt() == null ? "Never" : user.getLastLoginAt().toString())
        );
        Button changePassword = new Button("Change password", event -> getUI()
                .ifPresent(ui -> ui.navigate(ChangePasswordView.class)));
        changePassword.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(new H2("My Profile"), form, changePassword);
        setMaxWidth("60rem");
    }

    private TextField readOnly(String label, String value) {
        TextField field = new TextField(label);
        field.setValue(value == null ? "" : value);
        field.setReadOnly(true);
        return field;
    }

    private String accountState(User user, Clock clock) {
        if (!user.isActive()) {
            return "Inactive";
        }
        if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(LocalDateTime.now(clock))) {
            return "Locked until " + user.getLockoutUntil();
        }
        return "Active";
    }
}
