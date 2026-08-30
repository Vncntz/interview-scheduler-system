package com.company.iss.applicant.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ApplicantRepository extends JpaRepository<Applicant, Long> {

    Optional<Applicant> findByEmail(String email);

    @EntityGraph(attributePaths = {"branch", "positionOpening", "positionOpening.client"})
    @Query("select a from Applicant a where a.id = :id")
    Optional<Applicant> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"branch", "positionOpening", "positionOpening.client"})
    @Query("select a from Applicant a where a.id = :id and a.branch.id = :branchId")
    Optional<Applicant> findDetailedByIdAndBranchId(@Param("id") Long id, @Param("branchId") Long branchId);

    Long countByStatus(ApplicantStatus status);

    @EntityGraph(attributePaths = {"branch", "positionOpening", "positionOpening.client"})
    @Query("""
            select a from Applicant a
            where (:branchId is null or a.branch.id = :branchId)
              and (:keyword is null
                   or lower(a.firstName) like concat('%', :keyword, '%')
                   or lower(a.lastName) like concat('%', :keyword, '%')
                   or lower(a.email) like concat('%', :keyword, '%'))
              and (:status is null or a.status = :status)
            order by a.lastName asc, a.firstName asc, a.id asc
            """)
    List<Applicant> findGridPage(
            @Param("branchId") Long branchId,
            @Param("keyword") String keyword,
            @Param("status") ApplicantStatus status,
            Pageable pageable
    );

    @Query("""
            select count(a) from Applicant a
            where (:branchId is null or a.branch.id = :branchId)
              and (:keyword is null
                   or lower(a.firstName) like concat('%', :keyword, '%')
                   or lower(a.lastName) like concat('%', :keyword, '%')
                   or lower(a.email) like concat('%', :keyword, '%'))
              and (:status is null or a.status = :status)
            """)
    long countGrid(
            @Param("branchId") Long branchId,
            @Param("keyword") String keyword,
            @Param("status") ApplicantStatus status
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
