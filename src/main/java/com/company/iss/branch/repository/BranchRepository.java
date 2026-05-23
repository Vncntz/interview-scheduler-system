package com.company.iss.branch.repository;

import com.company.iss.branch.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    Optional<Branch> findByBranchCode(String branchCode);

    boolean existsByBranchCode(String branchCode);

    List<Branch> findByActiveTrue();

    List<Branch> findByBranchNameContainingIgnoreCase(String keyword);
}