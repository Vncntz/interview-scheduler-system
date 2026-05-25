package com.company.iss.evaluation.entity;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.auth.entity.User;
import com.company.iss.booking.entity.Booking;
import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_evaluations")
@Getter
@Setter
public class InterviewEvaluation extends BaseEntity {

    @ManyToOne
    private Booking booking;

    @ManyToOne
    private Applicant applicant;

    @ManyToOne
    private User evaluator;

    @Column(nullable = false)
    private Integer communicationScore;

    @Column(nullable = false)
    private Integer technicalScore;

    @Column(nullable = false)
    private Integer attitudeScore;

    @Column(length = 1000)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewResult result;

    @Column(nullable = false)
    private LocalDateTime evaluationDate = LocalDateTime.now();
}