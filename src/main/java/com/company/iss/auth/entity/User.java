package com.company.iss.auth.entity;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.branch.entity.Branch;
import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @ManyToOne
    private Branch branch;

    @Setter(AccessLevel.NONE)
    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(
            name = "applicant_id",
            nullable = true,
            unique = true,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_users_applicant")
    )
    private Applicant applicant;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    private LocalDateTime lockoutUntil;

    @Column(nullable = false)
    private boolean mustChangePassword = false;

    private LocalDateTime lastLoginAt;

    public static User forApplicant(Applicant applicant) {
        User user = new User();
        user.role = Role.APPLICANT;
        user.applicant = Objects.requireNonNull(applicant, "Applicant is required.");
        return user;
    }

    @PrePersist
    @PreUpdate
    private void validateApplicantOwnership() {
        if (role == Role.APPLICANT && applicant == null) {
            throw new IllegalStateException("Applicant users require an applicant ownership link.");
        }
        if (role != Role.APPLICANT && applicant != null) {
            throw new IllegalStateException("Operations users cannot have an applicant ownership link.");
        }
    }
}
