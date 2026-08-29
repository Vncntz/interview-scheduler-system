package com.company.iss.auth.service;

import com.company.iss.auth.entity.AccountSecurityAudit;
import com.company.iss.auth.entity.AccountSecurityEvent;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.event.CredentialsChangedEvent;
import com.company.iss.auth.repository.AccountSecurityAuditRepository;
import com.company.iss.auth.repository.PasswordResetRequestRepository;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class AccountLifecycleService {

    private final UserRepository userRepository;
    private final PasswordResetRequestRepository resetRequestRepository;
    private final AccountSecurityAuditRepository auditRepository;
    private final SecurityService securityService;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AccountLifecycleService(
            UserRepository userRepository,
            PasswordResetRequestRepository resetRequestRepository,
            AccountSecurityAuditRepository auditRepository,
            SecurityService securityService,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.resetRequestRepository = resetRequestRepository;
        this.auditRepository = auditRepository;
        this.securityService = securityService;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void changeCurrentPassword(String currentPassword, String newPassword, String confirmation) {
        User authenticated = securityService.requireAuthenticatedActiveUser();
        User user = userRepository.findByIdForUpdate(authenticated.getId())
                .orElseThrow(() -> new AccessDeniedException("An active authenticated user is required."));
        if (!passwordEncoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleViolationException("Current password is incorrect.");
        }
        passwordPolicy.validate(newPassword, confirmation);
        passwordPolicy.rejectCurrentPassword(newPassword, user.getPasswordHash());

        LocalDateTime now = now();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        invalidateOutstandingRequests(user.getId(), now);
        appendAudit(user, user, AccountSecurityEvent.PASSWORD_CHANGED, now, "SELF_SERVICE");
        eventPublisher.publishEvent(new CredentialsChangedEvent(user.getId()));
    }

    @Transactional
    public void unlockRecruiter(Long recruiterId) {
        User actor = securityService.requireAdmin();
        User target = requireRecruiterForUpdate(recruiterId);
        if (target.getFailedLoginAttempts() == 0 && target.getLockoutUntil() == null) {
            return;
        }
        target.setFailedLoginAttempts(0);
        target.setLockoutUntil(null);
        appendAudit(target, actor, AccountSecurityEvent.ACCOUNT_UNLOCKED, now(), "ADMIN_UNLOCK");
    }

    @Transactional
    public void setRecruiterActive(Long recruiterId, boolean active) {
        User actor = securityService.requireAdmin();
        User target = requireRecruiterForUpdate(recruiterId);
        if (target.isActive() == active) {
            return;
        }
        target.setActive(active);
        LocalDateTime now = now();
        appendAudit(
                target,
                actor,
                active ? AccountSecurityEvent.ACCOUNT_ACTIVATED : AccountSecurityEvent.ACCOUNT_DEACTIVATED,
                now,
                active ? "ADMIN_ACTIVATION" : "ADMIN_DEACTIVATION"
        );
        if (!active) {
            invalidateOutstandingRequests(target.getId(), now);
            eventPublisher.publishEvent(new CredentialsChangedEvent(target.getId()));
        }
    }

    private User requireRecruiterForUpdate(Long recruiterId) {
        User target = userRepository.findByIdForUpdate(recruiterId)
                .orElseThrow(() -> new BusinessRuleViolationException("Recruiter account was not found."));
        if (target.getRole() != Role.RECRUITER) {
            throw new BusinessRuleViolationException("Only recruiter accounts can be managed here.");
        }
        return target;
    }

    private void invalidateOutstandingRequests(Long userId, LocalDateTime now) {
        resetRequestRepository.findActiveByTargetUserIdForUpdate(userId)
                .forEach(request -> request.invalidate(now));
    }

    private void appendAudit(
            User target,
            User actor,
            AccountSecurityEvent event,
            LocalDateTime occurredAt,
            String reasonCode
    ) {
        auditRepository.append(AccountSecurityAudit.record(target, actor, event, occurredAt, reasonCode));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
