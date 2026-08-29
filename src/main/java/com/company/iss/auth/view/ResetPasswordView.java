package com.company.iss.auth.view;

import com.company.iss.auth.exception.InvalidPasswordResetTokenException;
import com.company.iss.auth.service.PasswordResetService;
import com.company.iss.shared.view.UserSafeNotifier;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("reset-password")
@PageTitle("Reset Password")
@AnonymousAllowed
public class ResetPasswordView extends VerticalLayout implements BeforeEnterObserver {

    private final PasswordResetService passwordResetService;
    private String token;

    public ResetPasswordView(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
        PasswordField password = new PasswordField("New password");
        PasswordField confirmation = new PasswordField("Confirm new password");
        password.setWidthFull();
        confirmation.setWidthFull();
        Button reset = new Button("Reset password", event -> {
            try {
                passwordResetService.resetPassword(token, password.getValue(), confirmation.getValue());
                Notification success = Notification.show(
                        "Password reset successfully. Sign in with your new password.",
                        4000,
                        Notification.Position.TOP_CENTER
                );
                success.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                UI.getCurrent().getPage().setLocation("/login?password-changed");
            } catch (InvalidPasswordResetTokenException exception) {
                Notification error = Notification.show(
                        "This password reset link is invalid or has expired.",
                        5000,
                        Notification.Position.TOP_CENTER
                );
                error.addThemeVariants(NotificationVariant.LUMO_ERROR);
            } catch (RuntimeException exception) {
                UserSafeNotifier.showError(exception);
            }
        });
        reset.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        setWidth("min(100%, 32rem)");
        getStyle().set("margin", "4rem auto");
        add(
                new H2("Reset your password"),
                new Paragraph("Choose 15–64 characters within the 72-byte BCrypt limit."),
                password,
                confirmation,
                reset
        );
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        token = event.getLocation().getQueryParameters().getSingleParameter("token").orElse(null);
    }
}
