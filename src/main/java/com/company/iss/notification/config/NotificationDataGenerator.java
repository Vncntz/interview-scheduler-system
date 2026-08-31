package com.company.iss.notification.config;

import com.company.iss.notification.entity.NotificationChannel;
import com.company.iss.notification.entity.NotificationEvent;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.entity.NotificationTemplate;
import com.company.iss.notification.repository.NotificationSettingsRepository;
import com.company.iss.notification.repository.NotificationTemplateRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class NotificationDataGenerator {

    private final NotificationSettingsRepository notificationSettingsRepository;
    private final NotificationTemplateRepository notificationTemplateRepository;

    public NotificationDataGenerator(
            NotificationSettingsRepository notificationSettingsRepository,
            NotificationTemplateRepository notificationTemplateRepository
    ) {
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.notificationTemplateRepository = notificationTemplateRepository;
    }

    @PostConstruct
    public void init() {
        seedSettings();
        seedTemplates();
    }

    private void seedSettings() {
        if (notificationSettingsRepository.count() > 0) {
            return;
        }

        NotificationSettings settings = new NotificationSettings();

        settings.setCompanyName("ISS Notifications");
        settings.setEmailEnabled(false);
        settings.setSmsEnabled(false);
        settings.setSmtpHost("smtp.gmail.com");
        settings.setSmtpPort(587);
        settings.setSmtpProvider(SmtpProvider.GMAIL);
        settings.setSmtpSecurity(SmtpSecurity.STARTTLS);

        notificationSettingsRepository.save(settings);
    }

    private void seedTemplates() {
        createTemplateIfMissing(NotificationEvent.BOOKING_CREATED, "Interview Schedule Confirmation", """
                Good day {{applicantName}},
                
                Your {{interviewStage}} interview has been successfully scheduled.
                
                Booking Reference: {{bookingReference}}
                Interview Stage: {{interviewStage}}
                Position: {{position}}
                Client: {{client}}
                Work Location: {{workLocation}}
                Interview Date: {{date}}
                Interview Time: {{time}}
                Recruiter: {{recruiter}}
                Interview Mode: {{interviewMode}}
                
                Thank you.
                """);

        createTemplateIfMissing(NotificationEvent.BOOKING_CONFIRMED, "Interview Booking Confirmed", """
                Good day {{applicantName}},
                
                Your {{interviewStage}} interview booking has been confirmed.
                
                Position: {{position}}
                Interview Stage: {{interviewStage}}
                Date: {{date}}
                Time: {{time}}
                
                Thank you.
                """);

        createTemplateIfMissing(NotificationEvent.BOOKING_CANCELLED, "Interview Booking Cancelled", """
                Good day {{applicantName}},
                
                Your interview booking has been cancelled.
                
                Booking Reference: {{bookingReference}}
                
                Please contact your recruiter.
                
                Thank you.
                """);

        createTemplateIfMissing(NotificationEvent.BOOKING_RESCHEDULED, "Interview Booking Rescheduled", """
                Good day {{applicantName}},

                Your {{interviewStage}} interview booking has been rescheduled.

                Booking Reference: {{bookingReference}}
                Interview Stage: {{interviewStage}}
                Position: {{position}}
                Interview Date: {{date}}
                Interview Time: {{time}}
                Recruiter: {{recruiter}}
                Interview Mode: {{interviewMode}}

                Thank you.
                """);

        createTemplateIfMissing(NotificationEvent.JOB_OFFERED, "Job Offer - {{position}}", """
                Good day {{applicantName}},

                We are pleased to offer you the {{position}} position for {{client}}.
                Work Location: {{workLocation}}

                Please contact your recruiter to discuss the next steps.

                Thank you.
                """);

        createTemplateIfMissing(NotificationEvent.HIRED, "Welcome to your new role - {{position}}", """
                Good day {{applicantName}},

                Your acceptance for the {{position}} position with {{client}} has been recorded.
                Work Location: {{workLocation}}

                Congratulations and welcome aboard.
                """);

        createTemplateIfMissing(NotificationEvent.PASSWORD_RESET, "Reset your Interview Scheduler password", """
                Good day {{userName}},

                An administrator requested a password reset for your account.

                Reset your password using this link:
                {{resetLink}}

                This link expires in {{expiresInMinutes}} minutes and can be used only once.
                If you did not expect this message, contact your administrator.
                """);
    }

    private void createTemplateIfMissing(NotificationEvent event, String subject, String body) {
        if (notificationTemplateRepository.existsByEventAndChannel(event, NotificationChannel.EMAIL)) {
            return;
        }

        NotificationTemplate template = new NotificationTemplate();

        template.setEvent(event);
        template.setChannel(NotificationChannel.EMAIL);
        template.setSubject(subject);
        template.setBody(body);
        template.setActive(true);

        notificationTemplateRepository.save(template);
    }
}
