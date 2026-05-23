package com.company.iss.auth.view;

import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.login.LoginOverlay;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@AnonymousAllowed
public class LoginView extends LoginOverlay implements BeforeEnterObserver {

    public LoginView() {
        LoginI18n i18n = LoginI18n.createDefault();

        // Header
        LoginI18n.Header header = new LoginI18n.Header();
        header.setTitle("ISS");
        header.setDescription("Interview Scheduler System");
        i18n.setHeader(header);

        // Form
        LoginI18n.Form form = i18n.getForm();
        form.setTitle("Sign In");
        form.setUsername("Email");
        form.setPassword("Password");
        form.setSubmit("Sign In");
        form.setForgotPassword("Forgot password?");
        i18n.setForm(form);

        // Error Message
        LoginI18n.ErrorMessage errorMessage = i18n.getErrorMessage();
        errorMessage.setTitle("Invalid email or password");
        errorMessage.setMessage(
                "Please check your credentials and try again."
        );
        i18n.setErrorMessage(errorMessage);

        // Footer info
        i18n.setAdditionalInformation(
                "Contact your administrator if you need help accessing your account."
        );

        setI18n(i18n);

        setAction("login");
        setOpened(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (event.getLocation()
                .getQueryParameters()
                .getParameters()
                .containsKey("error")) {
            setError(true);
        }
    }
}