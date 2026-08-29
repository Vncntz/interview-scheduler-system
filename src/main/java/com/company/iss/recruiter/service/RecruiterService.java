package com.company.iss.recruiter.service;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.auth.service.AccountLifecycleService;
import com.company.iss.auth.service.PasswordPolicy;
import com.company.iss.auth.service.PasswordResetService;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.branch.entity.Branch;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RecruiterService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SecurityService securityService;
    private final AccountLifecycleService accountLifecycleService;
    private final PasswordResetService passwordResetService;

    public RecruiterService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            SecurityService securityService,
            AccountLifecycleService accountLifecycleService,
            PasswordResetService passwordResetService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.securityService = securityService;
        this.accountLifecycleService = accountLifecycleService;
        this.passwordResetService = passwordResetService;
    }

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

    @Transactional
    public User save(User user, String temporaryPassword) {
        securityService.requireAdmin();
        validate(user);

        if (user.getId() == null) {
            if (userRepository.existsByEmail(user.getEmail())) {
                throw new BusinessRuleViolationException("Email already exists.");
            }

            user.setRole(Role.RECRUITER);
            passwordPolicy.validate(temporaryPassword, temporaryPassword);
            user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
            user.setMustChangePassword(true);
            user.setActive(true);
            return userRepository.save(user);
        }
        User persisted = userRepository.findById(user.getId())
                .orElseThrow(() -> new BusinessRuleViolationException("Recruiter account was not found."));
        if (persisted.getRole() != Role.RECRUITER) {
            throw new BusinessRuleViolationException("Only recruiter accounts can be managed here.");
        }
        if (!persisted.getEmail().equals(user.getEmail()) && userRepository.existsByEmail(user.getEmail())) {
            throw new BusinessRuleViolationException("Email already exists.");
        }
        persisted.setFullName(user.getFullName());
        persisted.setEmail(user.getEmail());
        persisted.setBranch(user.getBranch());
        return userRepository.save(persisted);
    }

    public void activate(Long recruiterId) {
        accountLifecycleService.setRecruiterActive(recruiterId, true);
    }

    public void deactivate(Long recruiterId) {
        accountLifecycleService.setRecruiterActive(recruiterId, false);
    }

    public void unlock(Long recruiterId) {
        accountLifecycleService.unlockRecruiter(recruiterId);
    }

    public void requestPasswordReset(Long recruiterId) {
        passwordResetService.requestRecruiterReset(recruiterId);
    }

    private void validate(User user) {
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            throw new BusinessRuleViolationException("Full name is required.");
        }

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessRuleViolationException("Email is required.");
        }

        if (user.getBranch() == null) {
            throw new BusinessRuleViolationException("Branch is required.");
        }
    }
}
