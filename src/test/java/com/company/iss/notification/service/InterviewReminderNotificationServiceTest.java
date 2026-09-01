package com.company.iss.notification.service;

import com.company.iss.notification.dto.InterviewReminderContext;
import com.company.iss.notification.entity.InterviewReminderType;
import com.company.iss.notification.entity.NotificationChannel;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.entity.NotificationTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewReminderNotificationServiceTest {

    @Mock NotificationSettingsService settingsService;
    @Mock NotificationTemplateService templateService;
    @Mock EmailService emailService;
    @Mock SmtpConfigurationValidator validator;

    private TemplateRenderService renderService;
    private NotificationService service;
    private NotificationSettings settings;

    @BeforeEach
    void setUp() {
        renderService = new TemplateRenderService();
        service = new NotificationService(settingsService, templateService, renderService, emailService, validator);
        settings = new NotificationSettings();
        settings.setEmailEnabled(true);
        when(settingsService.getSettings()).thenReturn(settings);
    }

    @Test
    void reminderUsesMatchingTemplateCurrentRecipientAndRenderedContext() {
        NotificationTemplate template = template(true);
        when(templateService.findTemplate(
                context().reminderType().notificationEvent(), NotificationChannel.EMAIL
        )).thenReturn(template);
        when(emailService.sendSynchronously(
                "candidate@example.test",
                "Reminder September 2, 2026 8:00 AM",
                "Alex INITIAL ONLINE Manila Makati"
        )).thenReturn(ReminderNotificationResult.sent());

        ReminderNotificationResult result = service.sendInterviewReminder(context());

        assertEquals(ReminderNotificationResult.Disposition.SENT, result.disposition());
        verify(emailService).sendSynchronously(
                "candidate@example.test",
                "Reminder September 2, 2026 8:00 AM",
                "Alex INITIAL ONLINE Manila Makati"
        );
    }

    @Test
    void disabledEmailOrTemplateIsTerminalSkipWithoutSmtpAttempt() {
        settings.setEmailEnabled(false);
        assertEquals("EMAIL_DISABLED", service.sendInterviewReminder(context()).reason());
        verify(emailService, never()).sendSynchronously(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );

        settings.setEmailEnabled(true);
        when(templateService.findTemplate(
                context().reminderType().notificationEvent(), NotificationChannel.EMAIL
        )).thenReturn(template(false));
        assertEquals("TEMPLATE_DISABLED", service.sendInterviewReminder(context()).reason());
    }

    private NotificationTemplate template(boolean active) {
        NotificationTemplate template = new NotificationTemplate();
        template.setActive(active);
        template.setSubject("Reminder {{date}} {{time}}");
        template.setBody("{{applicantName}} {{interviewStage}} {{interviewMode}} {{branch}} {{workLocation}}");
        return template;
    }

    private InterviewReminderContext context() {
        return new InterviewReminderContext(
                1L,
                2L,
                0,
                InterviewReminderType.REMINDER_24H,
                "test-claim-token",
                "candidate@example.test",
                "Alex",
                "BK-1",
                ZonedDateTime.of(2026, 9, 2, 8, 0, 0, 0, ZoneId.of("Asia/Manila")),
                "INITIAL",
                "ONLINE",
                "Recruiter",
                "Manila",
                "Developer",
                "Client",
                "Makati"
        );
    }
}
