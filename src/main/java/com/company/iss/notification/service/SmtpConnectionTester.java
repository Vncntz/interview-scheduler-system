package com.company.iss.notification.service;

import com.company.iss.notification.entity.NotificationSettings;
import jakarta.mail.AuthenticationFailedException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

@Component
public class SmtpConnectionTester {

    private final SmtpClientFactory clientFactory;
    private final SmtpConfigurationValidator validator;

    public SmtpConnectionTester(
            SmtpClientFactory clientFactory,
            SmtpConfigurationValidator validator
    ) {
        this.clientFactory = clientFactory;
        this.validator = validator;
    }

    public SmtpDiagnosticResult test(NotificationSettings settings) {
        try {
            validator.requireDiagnosticReady(settings);
            JavaMailSenderImpl sender = clientFactory.create(settings);
            sender.testConnection();
            return new SmtpDiagnosticResult(
                    SmtpDiagnosticResult.Code.CONNECTED,
                    "Connected successfully."
            );
        } catch (SmtpConfigurationException exception) {
            return incomplete(exception);
        } catch (Exception exception) {
            return classify(exception, false);
        }
    }

    SmtpDiagnosticResult classify(Throwable exception, boolean sending) {
        if (contains(exception, AuthenticationFailedException.class)
                || contains(exception, MailAuthenticationException.class)) {
            return new SmtpDiagnosticResult(
                    SmtpDiagnosticResult.Code.AUTHENTICATION_FAILED,
                    "Authentication failed. Check the SMTP username and runtime password."
            );
        }
        if (contains(exception, SSLException.class)) {
            return new SmtpDiagnosticResult(
                    SmtpDiagnosticResult.Code.TLS_FAILED,
                    "TLS negotiation failed. Check the SMTP security mode and server certificate."
            );
        }
        if (contains(exception, ConnectException.class)
                || contains(exception, SocketTimeoutException.class)
                || contains(exception, UnknownHostException.class)
                || hasSimpleName(exception, "MailConnectException")) {
            return new SmtpDiagnosticResult(
                    SmtpDiagnosticResult.Code.CONNECTION_FAILED,
                    "SMTP server is unreachable or timed out."
            );
        }
        return new SmtpDiagnosticResult(
                sending ? SmtpDiagnosticResult.Code.SEND_FAILED : SmtpDiagnosticResult.Code.CONNECTION_FAILED,
                sending
                        ? "The SMTP server could not send the test email."
                        : "The SMTP connection test failed."
        );
    }

    SmtpDiagnosticResult incomplete(SmtpConfigurationException exception) {
        SmtpDiagnosticResult.Code code = exception.getFailure()
                == SmtpConfigurationValidator.Failure.RECIPIENT_INVALID
                ? SmtpDiagnosticResult.Code.INVALID_RECIPIENT
                : SmtpDiagnosticResult.Code.CONFIGURATION_INCOMPLETE;
        return new SmtpDiagnosticResult(code, exception.getMessage());
    }

    private boolean contains(Throwable exception, Class<? extends Throwable> type) {
        Throwable current = exception;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasSimpleName(Throwable exception, String simpleName) {
        Throwable current = exception;
        while (current != null) {
            if (simpleName.equals(current.getClass().getSimpleName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
