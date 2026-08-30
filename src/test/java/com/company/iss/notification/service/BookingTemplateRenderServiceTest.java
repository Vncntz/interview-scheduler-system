package com.company.iss.notification.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.auth.entity.User;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.client.entity.Client;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BookingTemplateRenderServiceTest {

    @Test
    void rendersInterviewStageInBookingTemplates() {
        Client client = new Client();
        client.setCompanyName("Acme");
        PositionOpening position = new PositionOpening();
        position.setTitle("Engineer");
        position.setClient(client);
        position.setWorkLocation("Manila");
        Applicant applicant = new Applicant();
        applicant.setFirstName("Alex");
        applicant.setLastName("Candidate");
        applicant.setPositionOpening(position);
        User recruiter = new User();
        recruiter.setFullName("Rita Recruiter");
        Schedule schedule = new Schedule();
        schedule.setScheduleDate(LocalDate.of(2026, 9, 10));
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setInterviewMode(InterviewMode.ONLINE);
        Booking booking = Booking.forInterviewStage(InterviewStage.FINAL);
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(recruiter);
        booking.setBookingReference("BK-FINAL");

        String rendered = new TemplateRenderService().render(
                "Your {{interviewStage}} interview is scheduled.",
                booking
        );

        assertEquals("Your FINAL interview is scheduled.", rendered);
    }
}
