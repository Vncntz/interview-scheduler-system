package com.company.iss.notification.service;

import com.company.iss.booking.entity.Booking;
import com.company.iss.notification.entity.NotificationChannel;
import com.company.iss.notification.entity.NotificationEvent;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.entity.NotificationTemplate;
import com.company.iss.notification.dto.HiringNotificationContext;
import com.company.iss.notification.dto.PasswordResetNotificationContext;
import com.company.iss.notification.dto.InterviewReminderContext;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationSettingsService notificationSettingsService;
    private final NotificationTemplateService notificationTemplateService;
    private final TemplateRenderService templateRenderService;
    private final EmailService emailService;
    private final SmtpConfigurationValidator smtpConfigurationValidator;

    public NotificationService(
            NotificationSettingsService notificationSettingsService,
            NotificationTemplateService notificationTemplateService,
            TemplateRenderService templateRenderService,
            EmailService emailService,
            SmtpConfigurationValidator smtpConfigurationValidator
    ) {
        this.notificationSettingsService = notificationSettingsService;
        this.notificationTemplateService = notificationTemplateService;
        this.templateRenderService = templateRenderService;
        this.emailService = emailService;
        this.smtpConfigurationValidator = smtpConfigurationValidator;
    }

    public void send(NotificationEvent event, Booking booking) {
        NotificationSettings settings = notificationSettingsService.getSettings();

        if (!Boolean.TRUE.equals(settings.getEmailEnabled())) {
            return;
        }

        NotificationTemplate template = notificationTemplateService.findTemplate(event, NotificationChannel.EMAIL);

        if (template == null) {
            return;
        }

        if (!template.getActive()) {
            return;
        }

        if (booking.getApplicant().getEmail() == null || booking.getApplicant().getEmail().isBlank()) {
            return;
        }

        String subject = templateRenderService.render(template.getSubject(), booking);

        String body = templateRenderService.render(template.getBody(), booking);

        emailService.send(booking.getApplicant().getEmail(), subject, body);
    }

    public void sendHiring(NotificationEvent event, HiringNotificationContext context) {
        NotificationSettings settings = notificationSettingsService.getSettings();
        if (!Boolean.TRUE.equals(settings.getEmailEnabled())) {
            return;
        }

        NotificationTemplate template = notificationTemplateService.findTemplate(event, NotificationChannel.EMAIL);
        if (template == null || !template.getActive()
                || context.recipientEmail() == null || context.recipientEmail().isBlank()) {
            return;
        }

        String subject = templateRenderService.render(template.getSubject(), context);
        String body = templateRenderService.render(template.getBody(), context);
        emailService.send(context.recipientEmail(), subject, body);
    }

    public void sendPasswordReset(PasswordResetNotificationContext context) {
        NotificationSettings settings = notificationSettingsService.getSettings();
        if (!Boolean.TRUE.equals(settings.getEmailEnabled())) {
            return;
        }
        NotificationTemplate template = notificationTemplateService.findTemplate(
                NotificationEvent.PASSWORD_RESET, NotificationChannel.EMAIL
        );
        if (template == null || !template.getActive()
                || context.recipientEmail() == null || context.recipientEmail().isBlank()) {
            return;
        }
        emailService.send(
                context.recipientEmail(),
                templateRenderService.render(template.getSubject(), context),
                templateRenderService.render(template.getBody(), context)
        );
    }

    public ReminderNotificationResult sendInterviewReminder(InterviewReminderContext context) {
        NotificationSettings settings = notificationSettingsService.getSettings();
        if (!Boolean.TRUE.equals(settings.getEmailEnabled())) {
            return ReminderNotificationResult.skipped("EMAIL_DISABLED");
        }

        NotificationTemplate template = notificationTemplateService.findTemplate(
                context.reminderType().notificationEvent(), NotificationChannel.EMAIL
        );
        if (template == null) {
            return ReminderNotificationResult.skipped("TEMPLATE_MISSING");
        }
        if (!Boolean.TRUE.equals(template.getActive())) {
            return ReminderNotificationResult.skipped("TEMPLATE_DISABLED");
        }
        try {
            smtpConfigurationValidator.validateRecipient(context.recipientEmail());
        } catch (SmtpConfigurationException exception) {
            return ReminderNotificationResult.skipped("INVALID_RECIPIENT");
        }

        String subject;
        String body;
        try {
            subject = templateRenderService.render(template.getSubject(), context);
            body = templateRenderService.render(template.getBody(), context);
        } catch (RuntimeException exception) {
            return ReminderNotificationResult.skipped("TEMPLATE_RENDER_FAILED");
        }
        return emailService.sendSynchronously(context.recipientEmail(), subject, body);
    }
}
