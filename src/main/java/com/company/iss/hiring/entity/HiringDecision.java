package com.company.iss.hiring.entity;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.auth.entity.User;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "hiring_decisions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_hiring_decision_applicant", columnNames = "applicant_id"),
                @UniqueConstraint(name = "uk_hiring_decision_evaluation", columnNames = "evaluation_id")
        }
)
@Getter
@Setter
public class HiringDecision extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private InterviewEvaluation evaluation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id", nullable = false)
    private PositionOpening position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HiringDecisionStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offered_by_id", nullable = false)
    private User offeredBy;

    @Column(nullable = false)
    private LocalDateTime offeredAt;

    @Column(length = 1000)
    private String offeredRemarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    private LocalDateTime resolvedAt;

    @Column(length = 1000)
    private String resolutionRemarks;
}
