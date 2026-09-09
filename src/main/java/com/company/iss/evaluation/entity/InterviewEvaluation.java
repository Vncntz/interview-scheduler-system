package com.company.iss.evaluation.entity;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.auth.entity.User;
import com.company.iss.booking.entity.Booking;
import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "interview_evaluations",
        uniqueConstraints = @UniqueConstraint(name = "uk_interview_evaluation_booking", columnNames = "booking_id")
)
@Getter
@Immutable
public class InterviewEvaluation extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @ManyToOne
    @JoinColumn(name = "applicant_id", updatable = false)
    private Applicant applicant;

    @ManyToOne
    @JoinColumn(name = "evaluator_id", updatable = false)
    private User evaluator;

    @Column(nullable = false, updatable = false)
    private Integer communicationScore;

    @Column(nullable = false, updatable = false)
    private Integer technicalScore;

    @Column(nullable = false, updatable = false)
    private Integer attitudeScore;

    @Column(length = 1000, updatable = false)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private InterviewResult result;

    @Column(nullable = false, updatable = false)
    private LocalDateTime evaluationDate;

    protected InterviewEvaluation() {
    }

    private InterviewEvaluation(
            Booking booking,
            Applicant applicant,
            User evaluator,
            Integer communicationScore,
            Integer technicalScore,
            Integer attitudeScore,
            InterviewResult result,
            String remarks,
            LocalDateTime evaluationDate
    ) {
        this.booking = Objects.requireNonNull(booking, "booking is required");
        this.applicant = applicant;
        this.evaluator = evaluator;
        this.communicationScore = Objects.requireNonNull(communicationScore, "communicationScore is required");
        this.technicalScore = Objects.requireNonNull(technicalScore, "technicalScore is required");
        this.attitudeScore = Objects.requireNonNull(attitudeScore, "attitudeScore is required");
        this.result = Objects.requireNonNull(result, "result is required");
        this.remarks = remarks;
        this.evaluationDate = Objects.requireNonNull(evaluationDate, "evaluationDate is required");
    }

    public static InterviewEvaluation record(
            Booking booking,
            Applicant applicant,
            User evaluator,
            Integer communicationScore,
            Integer technicalScore,
            Integer attitudeScore,
            InterviewResult result,
            String remarks,
            LocalDateTime evaluationDate
    ) {
        return new InterviewEvaluation(
                booking, applicant, evaluator, communicationScore, technicalScore,
                attitudeScore, result, remarks, evaluationDate
        );
    }
}
