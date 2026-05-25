package com.company.iss.applicant.entity;

import com.company.iss.position.entity.PositionOpening;
import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "applicants")
@Getter
@Setter
public class Applicant extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(length = 100)
    private String middleName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 30)
    private String mobileNumber;

    @ManyToOne
    private PositionOpening positionOpening;

    @Column(length = 100)
    private String source;

    @Column(length = 1000)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicantStatus status = ApplicantStatus.NEW;

    @Column(nullable = false)
    private boolean active = true;

    public String getFullName() {
        StringBuilder fullName = new StringBuilder();

        if (firstName != null) {
            fullName.append(firstName).append(" ");
        }

        if (middleName != null && !middleName.isBlank()) {
            fullName.append(middleName).append(" ");
        }

        if (lastName != null) {
            fullName.append(lastName);
        }

        return fullName.toString().trim();
    }
}