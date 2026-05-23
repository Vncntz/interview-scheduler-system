package com.company.iss.branch.service;

import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BranchService {

    @Autowired
    private BranchRepository branchRepository;

    public List<Branch> findAll() {
        return branchRepository.findAll();
    }

    public List<Branch> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return branchRepository.findAll();
        }

        return branchRepository.findByBranchNameContainingIgnoreCase(keyword);
    }

    public Branch save(Branch branch) {
        validate(branch);

        if (branch.getId() == null &&
                branchRepository.existsByBranchCode(branch.getBranchCode())) {
            throw new RuntimeException("Branch code already exists.");
        }

        return branchRepository.save(branch);
    }

    public void deactivate(Branch branch) {
        branch.setActive(false);
        branchRepository.save(branch);
    }

    public void activate(Branch branch) {
        branch.setActive(true);
        branchRepository.save(branch);
    }

    private void validate(Branch branch) {
        if (branch.getBranchCode() == null || branch.getBranchCode().isBlank()) {
            throw new RuntimeException("Branch code is required.");
        }

        if (branch.getBranchName() == null || branch.getBranchName().isBlank()) {
            throw new RuntimeException("Branch name is required.");
        }

        if (branch.getAddress() == null || branch.getAddress().isBlank()) {
            throw new RuntimeException("Address is required.");
        }

        if (branch.getCity() == null || branch.getCity().isBlank()) {
            throw new RuntimeException("City is required.");
        }

        if (branch.getProvince() == null || branch.getProvince().isBlank()) {
            throw new RuntimeException("Province is required.");
        }
    }

    public void delete(Branch selected) {
    }
}