package com.company.iss.recruiter.service;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.branch.entity.Branch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecruiterService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public List<User> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return userRepository.findByRole(Role.RECRUITER);
        }

        return userRepository.findByRoleAndFullNameContainingIgnoreCase(
                Role.RECRUITER,
                keyword
        );
    }

    public List<User> findByBranch(Branch branch) {
        return userRepository.findByBranchAndRole(branch, Role.RECRUITER);
    }

    public User save(User user, String temporaryPassword) {
        validate(user);

        if (user.getId() == null) {
            if (userRepository.existsByEmail(user.getEmail())) {
                throw new RuntimeException("Email already exists.");
            }

            user.setRole(Role.RECRUITER);
            user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
            user.setMustChangePassword(true);
            user.setActive(true);
        }

        return userRepository.save(user);
    }

    public void activate(User user) {
        user.setActive(true);
        userRepository.save(user);
    }

    public void deactivate(User user) {
        user.setActive(false);
        userRepository.save(user);
    }

    public void resetPassword(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
    }

    private void validate(User user) {
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            throw new RuntimeException("Full name is required.");
        }

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new RuntimeException("Email is required.");
        }

        if (user.getBranch() == null) {
            throw new RuntimeException("Branch is required.");
        }
    }
}