package com.company.iss.notification.service;

import com.company.iss.booking.entity.Booking;
import com.company.iss.notification.dto.HiringNotificationContext;
import com.company.iss.notification.dto.InterviewReminderContext;
import com.company.iss.notification.dto.PasswordResetNotificationContext;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class TemplateRenderService {

    private static final DateTimeFormatter REMINDER_DATE = DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter REMINDER_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

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

    public String render(String template, InterviewReminderContext context) {
        if (template == null) {
            return "";
        }
        return template
                .replace("{{applicantName}}", safe(context.applicantName()))
                .replace("{{bookingReference}}", safe(context.bookingReference()))
                .replace("{{position}}", safe(context.position()))
                .replace("{{client}}", safe(context.client()))
                .replace("{{workLocation}}", safe(context.location()))
                .replace("{{date}}", context.interviewStart().format(REMINDER_DATE))
                .replace("{{time}}", context.interviewStart().format(REMINDER_TIME))
                .replace("{{timeZone}}", context.interviewStart().getZone().getId())
                .replace("{{recruiter}}", safe(context.recruiter()))
                .replace("{{branch}}", safe(context.branch()))
                .replace("{{interviewMode}}", safe(context.interviewMode()))
                .replace("{{interviewStage}}", safe(context.interviewStage()));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
