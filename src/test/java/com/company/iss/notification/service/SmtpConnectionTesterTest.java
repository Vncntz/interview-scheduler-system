package com.company.iss.notification.service;

import com.company.iss.notification.entity.NotificationSettings;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpConnectionTesterTest {

    @Test
    void successfulTestCallsConnectionOnly() throws MessagingException {
        Fixture fixture = fixture();

        SmtpDiagnosticResult result = fixture.tester().test(fixture.settings());

        assertEquals(SmtpDiagnosticResult.Code.CONNECTED, result.code());
        verify(fixture.sender()).testConnection();
    }

    @Test
    void authenticationConnectionAndTlsFailuresUseSafeCategories() throws MessagingException {
        assertFailure(
                new AuthenticationFailedException("secret provider response"),
                SmtpDiagnosticResult.Code.AUTHENTICATION_FAILED
        );
        assertFailure(
                new MessagingException("raw network detail", new ConnectException("refused")),
                SmtpDiagnosticResult.Code.CONNECTION_FAILED
        );
        assertFailure(
                new MessagingException("raw TLS detail", new SSLHandshakeException("certificate")),
                SmtpDiagnosticResult.Code.TLS_FAILED
        );
    }

    @Test
    void incompleteConfigurationDoesNotCreateClient() {
        SmtpClientFactory factory = mock(SmtpClientFactory.class);
        SmtpConfigurationValidator validator = mock(SmtpConfigurationValidator.class);
        NotificationSettings settings = new NotificationSettings();
        doThrow(new SmtpConfigurationException(
                SmtpConfigurationValidator.Failure.HOST_MISSING,
                "SMTP host is required."
        )).when(validator).requireDiagnosticReady(settings);

        SmtpDiagnosticResult result = new SmtpConnectionTester(factory, validator).test(settings);

        assertEquals(SmtpDiagnosticResult.Code.CONFIGURATION_INCOMPLETE, result.code());
        org.mockito.Mockito.verifyNoInteractions(factory);
    }

    private void assertFailure(Exception exception, SmtpDiagnosticResult.Code expected)
            throws MessagingException {
        Fixture fixture = fixture();
        doThrow(exception).when(fixture.sender()).testConnection();

        SmtpDiagnosticResult result = fixture.tester().test(fixture.settings());

        assertEquals(expected, result.code());
        org.junit.jupiter.api.Assertions.assertFalse(result.message().contains("raw"));
        org.junit.jupiter.api.Assertions.assertFalse(result.message().contains("secret"));
    }

    private Fixture fixture() {
        NotificationSettings settings = new NotificationSettings();
        SmtpClientFactory factory = mock(SmtpClientFactory.class);
        SmtpConfigurationValidator validator = mock(SmtpConfigurationValidator.class);
        JavaMailSenderImpl sender = mock(JavaMailSenderImpl.class);
        when(factory.create(settings)).thenReturn(sender);
        return new Fixture(settings, sender, new SmtpConnectionTester(factory, validator));
    }

    private record Fixture(
            NotificationSettings settings,
            JavaMailSenderImpl sender,
            SmtpConnectionTester tester
    ) {
    }
}
