package com.company.iss.auth.view;

import com.company.iss.auth.service.AccountLifecycleService;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.shared.view.UserSafeNotifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route("change-password")
@PageTitle("Change Password")
@RolesAllowed({"ADMIN", "RECRUITER"})
public class ChangePasswordView extends VerticalLayout {

    public ChangePasswordView(AccountLifecycleService accountLifecycleService, SecurityService securityService) {
        PasswordField currentPassword = new PasswordField("Current password");
        PasswordField newPassword = new PasswordField("New password");
        PasswordField confirmation = new PasswordField("Confirm new password");
        currentPassword.setWidthFull();
        newPassword.setWidthFull();
        confirmation.setWidthFull();

        Button change = new Button("Change password", event -> {
            try {
                accountLifecycleService.changeCurrentPassword(
                        currentPassword.getValue(), newPassword.getValue(), confirmation.getValue()
                );
                securityService.logoutAfterPasswordChange();
            } catch (RuntimeException exception) {
                UserSafeNotifier.showError(exception);
            }
        });
        change.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button signOut = new Button("Sign out", event -> securityService.logout());
        signOut.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        setWidth("min(100%, 32rem)");
        getStyle().set("margin", "4rem auto");
        add(
                new H2("Change your password"),
                new Paragraph("Use 15–64 characters. Passwords are limited to 72 UTF-8 bytes."),
                currentPassword,
                newPassword,
                confirmation,
                change,
                signOut
        );
    }
}
