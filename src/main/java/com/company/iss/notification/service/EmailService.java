package com.company.iss.notification.service;

import com.company.iss.notification.entity.NotificationSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
public class EmailService {

    @Autowired
    private NotificationSettingsService notificationSettingsService;

    @Async
    public void send(String to, String subject, String body) {
        NotificationSettings settings = notificationSettingsService.getSettings();

        if (!Boolean.TRUE.equals(settings.getEmailEnabled())) {
            return;
        }

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        mailSender.setHost(settings.getSmtpHost());

        mailSender.setPort(settings.getSmtpPort());

        mailSender.setUsername(settings.getSmtpUsername());

        mailSender.setPassword(settings.getSmtpPassword());

        Properties props = mailSender.getJavaMailProperties();

        props.put("mail.smtp.auth", "true");

        props.put("mail.smtp.starttls.enable", "true");

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(settings.getSmtpUsername());

        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }
}