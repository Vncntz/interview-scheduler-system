package com.company.iss.booking.entity;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.auth.entity.User;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter
@Setter
public class Booking extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String bookingReference;

    @ManyToOne
    private Applicant applicant;

    @ManyToOne
    private Schedule schedule;

    @ManyToOne
    private User recruiter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status = BookingStatus.BOOKED;

    @Column(length = 1000)
    private String remarks;

    @Column(nullable = false)
    private LocalDateTime bookedDateTime = LocalDateTime.now();
}