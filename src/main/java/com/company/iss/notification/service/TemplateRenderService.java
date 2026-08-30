package com.company.iss.notification.service;

import com.company.iss.booking.entity.Booking;
import com.company.iss.notification.dto.HiringNotificationContext;
import com.company.iss.notification.dto.PasswordResetNotificationContext;
import org.springframework.stereotype.Service;

@Service
public class TemplateRenderService {

    public String render(String template, Booking booking) {
        if (template == null) {
            return "";
        }

        return template.replace("{{applicantName}}", safe(booking.getApplicant().getFullName())).replace("{{bookingReference}}", safe(booking.getBookingReference())).replace("{{position}}", safe(booking.getApplicant().getPositionOpening().getTitle())).replace("{{client}}", safe(booking.getApplicant().getPositionOpening().getClient().getCompanyName())).replace("{{workLocation}}", safe(booking.getApplicant().getPositionOpening().getWorkLocation())).replace("{{date}}", safe(String.valueOf(booking.getSchedule().getScheduleDate()))).replace("{{time}}", safe(String.valueOf(booking.getSchedule().getStartTime()))).replace("{{recruiter}}", safe(booking.getRecruiter().getFullName())).replace("{{interviewMode}}", safe(booking.getSchedule().getInterviewMode().name())).replace("{{interviewStage}}", booking.getInterviewStage() == null ? "" : booking.getInterviewStage().name());
    }

    public String render(String template, HiringNotificationContext context) {
        if (template == null) {
            return "";
        }

        return template
                .replace("{{applicantName}}", safe(context.applicantName()))
                .replace("{{position}}", safe(context.position()))
                .replace("{{client}}", safe(context.client()))
                .replace("{{workLocation}}", safe(context.workLocation()));
    }

    public String render(String template, PasswordResetNotificationContext context) {
        if (template == null) {
            return "";
        }
        return template
                .replace("{{userName}}", safe(context.userName()))
                .replace("{{resetLink}}", safe(context.resetLink()))
                .replace("{{expiresInMinutes}}", String.valueOf(context.expiresInMinutes()));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
