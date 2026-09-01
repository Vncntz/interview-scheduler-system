package com.company.iss.notification.service;

import com.company.iss.auth.service.SecurityService;
import com.company.iss.notification.entity.NotificationSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

@Service
public class SmtpDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(SmtpDiagnosticsService.class);

    private final SecurityService securityService;
    private final SmtpConnectionTester connectionTester;
    private final SmtpConfigurationValidator validator;
    private final EmailService emailService;

    public SmtpDiagnosticsService(
            SecurityService securityService,
            SmtpConnectionTester connectionTester,
            SmtpConfigurationValidator validator,
            EmailService emailService
    ) {
        this.securityService = securityService;
        this.connectionTester = connectionTester;
        this.validator = validator;
        this.emailService = emailService;
    }

    public SmtpDiagnosticResult testConnection(NotificationSettings candidate) {
        securityService.requireAdmin();
        SmtpDiagnosticResult result = connectionTester.test(candidate);
        logResult("connection-test", null, result);
        return result;
    }

    public SmtpDiagnosticResult sendTestEmail(NotificationSettings candidate, String recipient) {
        securityService.requireAdmin();
        try {
            validator.requireDiagnosticReady(candidate);
            validator.validateRecipient(recipient);
            emailService.sendTestEmail(candidate, recipient);
            SmtpDiagnosticResult result = new SmtpDiagnosticResult(
                    SmtpDiagnosticResult.Code.TEST_EMAIL_SENT,
                    "Test email sent successfully."
            );
            logResult("test-email", recipient, result);
            return result;
        } catch (SmtpConfigurationException exception) {
            SmtpDiagnosticResult result = connectionTester.incomplete(exception);
            logResult("test-email", recipient, result);
            return result;
        } catch (MailException exception) {
            SmtpDiagnosticResult result = connectionTester.classify(exception, true);
            logResult("test-email", recipient, result);
            return result;
        }
    }

    private void logResult(String operation, String recipient, SmtpDiagnosticResult result) {
        String maskedRecipient = recipient == null ? "<none>" : EmailService.maskRecipient(recipient);
        if (result.success()) {
            log.info(
                    "[EMAIL] SMTP diagnostic completed operation={} recipient={} result={}",
                    operation,
                    maskedRecipient,
                    result.code()
            );
        } else {
            log.warn(
                    "[EMAIL] SMTP diagnostic failed operation={} recipient={} result={}",
                    operation,
                    maskedRecipient,
                    result.code()
            );
        }
    }
}
