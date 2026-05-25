package com.company.iss.booking.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.service.ApplicantService;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.notification.entity.NotificationEvent;
import com.company.iss.notification.service.NotificationService;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.repository.ScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookingService {
    @Autowired
    private NotificationService notificationService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ApplicantService applicantService;

    public List<Booking> search(String keyword) {

        if (keyword == null || keyword.isBlank()) {
            return bookingRepository.findAll();
        }

        return bookingRepository.search(keyword);
    }

    public Booking createBooking(Applicant applicant, Schedule schedule, String remarks) {
        validateBooking(applicant, schedule);

        Booking booking = new Booking();

        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(schedule.getRecruiter());
        booking.setRemarks(remarks);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setBookedDateTime(LocalDateTime.now());
        booking.setBookingReference(generateBookingReference());

        schedule.setBookedCount(schedule.getBookedCount() + 1);

        if (schedule.getBookedCount() >= schedule.getSlotCapacity()) {
            schedule.setStatus(ScheduleStatus.FULL);
        }

        scheduleRepository.save(schedule);

        applicantService.updateStatus(applicant, ApplicantStatus.SCHEDULED);

        Booking saved = bookingRepository.save(booking);

        notificationService.send(NotificationEvent.BOOKING_CREATED, saved);

        return saved;
    }

    public void markAttended(Booking booking) {
        booking.setStatus(BookingStatus.ATTENDED);
        bookingRepository.save(booking);
    }

    public void markNoShow(Booking booking) {
        booking.setStatus(BookingStatus.NO_SHOW);
        bookingRepository.save(booking);
    }

    public void markPassed(Booking booking) {
        booking.setStatus(BookingStatus.PASSED);
        bookingRepository.save(booking);
    }

    public void markFailed(Booking booking) {
        booking.setStatus(BookingStatus.FAILED);
        bookingRepository.save(booking);
    }

    public void cancel(Booking booking) {

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return;
        }

        Schedule schedule = booking.getSchedule();

        if (schedule.getBookedCount() > 0) {
            schedule.setBookedCount(schedule.getBookedCount() - 1);
        }

        if (schedule.getStatus() == ScheduleStatus.FULL) {
            schedule.setStatus(ScheduleStatus.OPEN);
        }

        booking.setStatus(BookingStatus.CANCELLED);

        Booking saved = bookingRepository.save(booking);

        notificationService.send(NotificationEvent.BOOKING_CANCELLED, saved);
    }

    private void validateBooking(Applicant applicant, Schedule schedule) {

        if (applicant == null) {
            throw new RuntimeException("Applicant is required.");
        }

        if (!applicant.isActive()) {
            throw new RuntimeException("Applicant is inactive.");
        }

        Optional<Booking> activeBooking = bookingRepository.findFirstByApplicantAndStatusIn(applicant, List.of(BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED));

        if (activeBooking.isPresent()) {
            throw new RuntimeException("Applicant already has an active booking.");
        }

        if (schedule == null) {
            throw new RuntimeException("Schedule is required.");
        }

        if (!schedule.isActive()) {
            throw new RuntimeException("Schedule is inactive.");
        }

        if (schedule.getStatus() != ScheduleStatus.OPEN) {
            throw new RuntimeException("Schedule is not open.");
        }

        if (schedule.getBookedCount() >= schedule.getSlotCapacity()) {
            throw new RuntimeException("Schedule is already full.");
        }

        Optional<Booking> existing = bookingRepository.findByApplicantAndSchedule(applicant, schedule);

        if (existing.isPresent()) {
            throw new RuntimeException("Applicant is already booked for this schedule.");
        }
    }

    private String generateBookingReference() {
        return "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public void confirm(Booking booking) {
        booking.setStatus(BookingStatus.CONFIRMED);

        Booking saved = bookingRepository.save(booking);

        notificationService.send(NotificationEvent.BOOKING_CONFIRMED, saved);
    }
}