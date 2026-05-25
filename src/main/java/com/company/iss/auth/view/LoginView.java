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

        LoginI18n.Header header = new LoginI18n.Header();

        header.setTitle("ISS");
        header.setDescription("Interview Scheduler System");

        i18n.setHeader(header);

        LoginI18n.Form form = i18n.getForm();

        form.setTitle("Sign In");
        form.setUsername("Email");
        form.setPassword("Password");
        form.setSubmit("Sign In");
        form.setForgotPassword("");

        LoginI18n.ErrorMessage error = i18n.getErrorMessage();

        error.setTitle("Login Failed");
        error.setMessage("Invalid email or password.");

        i18n.setAdditionalInformation("Contact your administrator for access.");

        setI18n(i18n);
        setAction("login");
        setOpened(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {

            setError(true);
        }
    }
}