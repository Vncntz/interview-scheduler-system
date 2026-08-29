package com.company.iss.auth.service;

import com.company.iss.auth.config.AccountSecurityProperties;
import com.company.iss.auth.dto.PasswordResetDelivery;
import com.company.iss.auth.entity.AccountSecurityAudit;
import com.company.iss.auth.entity.AccountSecurityEvent;
import com.company.iss.auth.entity.PasswordResetRequest;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.event.CredentialsChangedEvent;
import com.company.iss.auth.event.PasswordResetRequestedEvent;
import com.company.iss.auth.exception.InvalidPasswordResetTokenException;
import com.company.iss.auth.repository.AccountSecurityAuditRepository;
import com.company.iss.auth.repository.PasswordResetRequestRepository;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.notification.service.PasswordResetNotificationReadiness;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetRequestRepository requestRepository;
    private final AccountSecurityAuditRepository auditRepository;
    private final SecurityService securityService;
    private final PasswordResetTokenService tokenService;
    private final PasswordResetNotificationReadiness notificationReadiness;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final AccountSecurityProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetRequestRepository requestRepository,
            AccountSecurityAuditRepository auditRepository,
            SecurityService securityService,
            PasswordResetTokenService tokenService,
            PasswordResetNotificationReadiness notificationReadiness,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            AccountSecurityProperties properties,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.auditRepository = auditRepository;
        this.securityService = securityService;
        this.tokenService = tokenService;
        this.notificationReadiness = notificationReadiness;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void requestRecruiterReset(Long recruiterId) {
        User actor = securityService.requireAdmin();
        User target = userRepository.findByIdForUpdate(recruiterId)
                .orElseThrow(() -> new BusinessRuleViolationException("Recruiter account was not found."));
        if (target.getRole() != Role.RECRUITER || !target.isActive() || target.getEmail() == null
                || target.getEmail().isBlank()) {
            throw new BusinessRuleViolationException("Password reset is not available for this recruiter account.");
        }
        try {
            tokenService.requireConfigured();
        } catch (IllegalStateException exception) {
            throw new BusinessRuleViolationException("Password reset is not configured.");
        }
        notificationReadiness.requireReady();

        LocalDateTime now = now();
        requestRepository.findActiveByTargetUserIdForUpdate(target.getId())
                .forEach(request -> request.invalidate(now));
        String publicId = tokenService.newPublicRequestId();
        String token = tokenService.tokenFor(publicId);
        PasswordResetRequest request = PasswordResetRequest.issue(
                target,
                actor,
                publicId,
                tokenService.hash(token),
                now.plus(properties.getPasswordReset().getTtl())
        );
        requestRepository.save(request);
        auditRepository.append(AccountSecurityAudit.record(
                target, actor, AccountSecurityEvent.PASSWORD_RESET_REQUESTED, now, "ADMIN_INITIATED"
        ));
        eventPublisher.publishEvent(new PasswordResetRequestedEvent(request.getId()));
    }

    @Transactional
    public void resetPassword(String token, String newPassword, String confirmation) {
        passwordPolicy.validate(newPassword, confirmation);
        String publicId = tokenService.verifiedPublicRequestId(token)
                .orElseThrow(InvalidPasswordResetTokenException::new);
        Long targetUserId = requestRepository.findTargetUserIdByPublicRequestId(publicId)
                .orElseThrow(InvalidPasswordResetTokenException::new);

        User target = userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(InvalidPasswordResetTokenException::new);
        PasswordResetRequest request = requestRepository.findByPublicRequestIdForUpdate(publicId)
                .orElseThrow(InvalidPasswordResetTokenException::new);
        LocalDateTime now = now();
        if (!target.isActive() || !request.isUsableAt(now)
                || !tokenService.hashMatches(token, request.getTokenHash())) {
            throw new InvalidPasswordResetTokenException();
        }
        passwordPolicy.rejectCurrentPassword(newPassword, target.getPasswordHash());

        target.setPasswordHash(passwordEncoder.encode(newPassword));
        target.setMustChangePassword(false);
        target.setFailedLoginAttempts(0);
        target.setLockoutUntil(null);
        request.consume(now);
        requestRepository.findActiveByTargetUserIdForUpdate(target.getId()).stream()
                .filter(other -> !other.getId().equals(request.getId()))
                .forEach(other -> other.invalidate(now));
        auditRepository.append(AccountSecurityAudit.record(
                target, target, AccountSecurityEvent.PASSWORD_RESET_COMPLETED, now, "SIGNED_LINK"
        ));
        eventPublisher.publishEvent(new CredentialsChangedEvent(target.getId()));
    }

    @Transactional(readOnly = true)
    public PasswordResetDelivery prepareDelivery(Long requestId) {
        PasswordResetRequest request = requestRepository.findById(requestId).orElse(null);
        LocalDateTime now = now();
        if (request == null || !request.getTargetUser().isActive() || !request.isUsableAt(now)) {
            return null;
        }
        String token = tokenService.tokenFor(request.getPublicRequestId());
        if (!tokenService.hashMatches(token, request.getTokenHash())) {
            return null;
        }
        return new PasswordResetDelivery(
                request.getTargetUser().getEmail(),
                request.getTargetUser().getFullName(),
                tokenService.resetLink(token),
                properties.getPasswordReset().getTtl().toMinutes()
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
