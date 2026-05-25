package com.company.iss.position.entity;

import com.company.iss.client.entity.Client;
import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "position_openings")
@Getter
@Setter
public class PositionOpening extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String title;

    @ManyToOne
    private Client client;

    @Column(nullable = false, length = 200)
    private String workLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EmploymentType employmentType;

    @Column(nullable = false)
    private Integer requiredHeadcount;

    @Column(nullable = false)
    private Integer appliedCount = 0;

    @Column(nullable = false)
    private Integer interviewedCount = 0;

    @Column(nullable = false)
    private Integer passedCount = 0;

    @Column(nullable = false)
    private Integer hiredCount = 0;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PositionStatus status = PositionStatus.OPEN;

    @Column(nullable = false)
    private boolean active = true;

    public Integer getRemainingHeadcount() {
        return requiredHeadcount - hiredCount;
    }
}