package com.company.iss.notification.service;

import com.company.iss.booking.entity.Booking;
import com.company.iss.notification.entity.NotificationChannel;
import com.company.iss.notification.entity.NotificationEvent;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.entity.NotificationTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Autowired
    private NotificationSettingsService notificationSettingsService;

    @Autowired
    private NotificationTemplateService notificationTemplateService;

    @Autowired
    private TemplateRenderService templateRenderService;

    @Autowired
    private EmailService emailService;

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
}