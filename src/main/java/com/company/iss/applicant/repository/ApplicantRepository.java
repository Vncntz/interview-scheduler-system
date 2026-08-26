package com.company.iss.applicant.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ApplicantRepository extends JpaRepository<Applicant, Long> {

    List<Applicant> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String firstName, String lastName, String email);

    Optional<Applicant> findByEmail(String email);

    Long countByStatus(ApplicantStatus status);

    @EntityGraph(attributePaths = {"branch", "positionOpening", "positionOpening.client"})
    List<Applicant> findByBranchIdOrderByLastNameAscFirstNameAsc(Long branchId);

    @EntityGraph(attributePaths = {"branch", "positionOpening", "positionOpening.client"})
    List<Applicant> findByBranchIdAndFirstNameContainingIgnoreCaseOrBranchIdAndLastNameContainingIgnoreCaseOrBranchIdAndEmailContainingIgnoreCase(
            Long firstNameBranchId, String firstName,
            Long lastNameBranchId, String lastName,
            Long emailBranchId, String email
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"branch", "positionOpening"})
    @Query("select a from Applicant a where a.id = :id")
    Optional<Applicant> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"branch", "positionOpening"})
    @Query("select a from Applicant a where a.id = :id and a.branch.id = :branchId")
    Optional<Applicant> findByIdAndBranchIdForUpdate(@Param("id") Long id, @Param("branchId") Long branchId);

}
