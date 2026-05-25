package com.company.iss.schedule.entity;

import com.company.iss.auth.entity.User;
import com.company.iss.branch.entity.Branch;
import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "schedules")
@Getter
@Setter
public class Schedule extends BaseEntity {

    @ManyToOne
    private Branch branch;

    @ManyToOne
    private User recruiter;

    @Column(nullable = false)
    private LocalDate scheduleDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private Integer slotCapacity;

    @Column(nullable = false)
    private Integer bookedCount = 0;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewMode interviewMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleStatus status = ScheduleStatus.OPEN;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;
}