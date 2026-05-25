package com.company.iss.notification.service;

import com.company.iss.notification.entity.NotificationChannel;
import com.company.iss.notification.entity.NotificationEvent;
import com.company.iss.notification.entity.NotificationTemplate;
import com.company.iss.notification.repository.NotificationTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationTemplateService {

    @Autowired
    private NotificationTemplateRepository notificationTemplateRepository;

    public NotificationTemplate save(NotificationTemplate template) {
        validate(template);

        return notificationTemplateRepository.save(template);
    }

    public NotificationTemplate findTemplate(NotificationEvent event, NotificationChannel channel) {
        return notificationTemplateRepository.findByEventAndChannelAndActiveTrue(event, channel).orElse(null);
    }

    public List<NotificationTemplate> findAll() {
        return notificationTemplateRepository.findAllByOrderByEventAsc();
    }

    private void validate(NotificationTemplate template) {
        if (template.getEvent() == null) {
            throw new RuntimeException("Notification event is required.");
        }

        if (template.getChannel() == null) {
            throw new RuntimeException("Notification channel is required.");
        }

        if (template.getBody() == null || template.getBody().isBlank()) {
            throw new RuntimeException("Template body is required.");
        }
    }
}