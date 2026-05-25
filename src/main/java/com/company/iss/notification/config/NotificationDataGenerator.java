package com.company.iss.notification.config;

import com.company.iss.notification.entity.NotificationChannel;
import com.company.iss.notification.entity.NotificationEvent;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.entity.NotificationTemplate;
import com.company.iss.notification.repository.NotificationSettingsRepository;
import com.company.iss.notification.repository.NotificationTemplateRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotificationDataGenerator {

    @Autowired
    private NotificationSettingsRepository notificationSettingsRepository;

    @Autowired
    private NotificationTemplateRepository notificationTemplateRepository;

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
        settings.setEmailEnabled(true);
        settings.setSmsEnabled(false);
        settings.setSmtpHost("smtp.gmail.com");
        settings.setSmtpPort(587);

        notificationSettingsRepository.save(settings);
    }

    private void seedTemplates() {
        if (notificationTemplateRepository.count() > 0) {
            return;
        }

        createTemplate(NotificationEvent.BOOKING_CREATED, "Interview Schedule Confirmation", """
                Good day {{applicantName}},
                
                Your interview has been successfully scheduled.
                
                Booking Reference: {{bookingReference}}
                Position: {{position}}
                Client: {{client}}
                Work Location: {{workLocation}}
                Interview Date: {{date}}
                Interview Time: {{time}}
                Recruiter: {{recruiter}}
                Interview Mode: {{interviewMode}}
                
                Thank you.
                """);

        createTemplate(NotificationEvent.BOOKING_CONFIRMED, "Interview Booking Confirmed", """
                Good day {{applicantName}},
                
                Your interview booking has been confirmed.
                
                Position: {{position}}
                Date: {{date}}
                Time: {{time}}
                
                Thank you.
                """);

        createTemplate(NotificationEvent.BOOKING_CANCELLED, "Interview Booking Cancelled", """
                Good day {{applicantName}},
                
                Your interview booking has been cancelled.
                
                Booking Reference: {{bookingReference}}
                
                Please contact your recruiter.
                
                Thank you.
                """);
    }

    private void createTemplate(NotificationEvent event, String subject, String body) {
        NotificationTemplate template = new NotificationTemplate();

        template.setEvent(event);
        template.setChannel(NotificationChannel.EMAIL);
        template.setSubject(subject);
        template.setBody(body);
        template.setActive(true);

        notificationTemplateRepository.save(template);
    }
}