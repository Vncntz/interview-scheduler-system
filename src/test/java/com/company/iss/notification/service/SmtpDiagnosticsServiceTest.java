package com.company.iss.notification.service;

import com.company.iss.auth.service.SecurityService;
import com.company.iss.notification.entity.NotificationSettings;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpDiagnosticsServiceTest {

    @Test
    void connectionTestDoesNotSendEmail() {
        Fixture fixture = fixture();
        NotificationSettings candidate = new NotificationSettings();
        when(fixture.connectionTester().test(candidate)).thenReturn(new SmtpDiagnosticResult(
                SmtpDiagnosticResult.Code.CONNECTED,
                "Connected successfully."
        ));

        SmtpDiagnosticResult result = fixture.service().testConnection(candidate);

        assertEquals(SmtpDiagnosticResult.Code.CONNECTED, result.code());
        verify(fixture.connectionTester()).test(candidate);
        verify(fixture.emailService(), never()).sendTestEmail(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void testEmailSendsExactlyOneLabeledMessageThroughEmailService() {
        Fixture fixture = fixture();
        NotificationSettings candidate = new NotificationSettings();

        SmtpDiagnosticResult result = fixture.service().sendTestEmail(candidate, "admin@example.test");

        assertEquals(SmtpDiagnosticResult.Code.TEST_EMAIL_SENT, result.code());
        verify(fixture.emailService()).sendTestEmail(candidate, "admin@example.test");
    }

    @Test
    void adminAuthorizationIsRequiredBeforeDiagnostics() {
        Fixture fixture = fixture();
        doThrow(new AccessDeniedException("denied")).when(fixture.securityService()).requireAdmin();

        assertThrows(AccessDeniedException.class,
                () -> fixture.service().testConnection(new NotificationSettings()));

        org.mockito.Mockito.verifyNoInteractions(fixture.connectionTester());
    }

    private Fixture fixture() {
        SecurityService securityService = mock(SecurityService.class);
        SmtpConnectionTester connectionTester = mock(SmtpConnectionTester.class);
        SmtpConfigurationValidator validator = mock(SmtpConfigurationValidator.class);
        EmailService emailService = mock(EmailService.class);
        return new Fixture(
                securityService,
                connectionTester,
                emailService,
                new SmtpDiagnosticsService(securityService, connectionTester, validator, emailService)
        );
    }

    private record Fixture(
            SecurityService securityService,
            SmtpConnectionTester connectionTester,
            EmailService emailService,
            SmtpDiagnosticsService service
    ) {
    }
}
