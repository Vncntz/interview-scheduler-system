package com.company.iss.auth.service;

import com.company.iss.auth.config.AccountSecurityProperties;
import com.company.iss.auth.entity.AccountSecurityAudit;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.event.CredentialsChangedEvent;
import com.company.iss.auth.repository.AccountSecurityAuditRepository;
import com.company.iss.auth.repository.PasswordResetRequestRepository;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountLifecycleServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordResetRequestRepository requestRepository;
    @Mock AccountSecurityAuditRepository auditRepository;
    @Mock SecurityService securityService;
    @Mock ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC);
    private BCryptPasswordEncoder encoder;
    private AccountLifecycleService service;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoder();
        AccountSecurityProperties properties = new AccountSecurityProperties();
        service = new AccountLifecycleService(
                userRepository,
                requestRepository,
                auditRepository,
                securityService,
                new PasswordPolicy(properties, encoder),
                encoder,
                eventPublisher,
                clock
        );
    }

    @Test
    void rejectedSelfChangeDoesNotWriteAuditOrExpireSessions() {
        User user = recruiter();
        user.setPasswordHash(encoder.encode("existing secure password"));
        when(securityService.requireAuthenticatedActiveUser()).thenReturn(user);
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        assertThrows(
                BusinessRuleViolationException.class,
                () -> service.changeCurrentPassword("wrong password", "new secure password", "new secure password")
        );

        verify(auditRepository, never()).append(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void successfulSelfChangeClearsSecurityStateAndForcesFreshAuthentication() {
        User user = recruiter();
        user.setPasswordHash(encoder.encode("existing secure password"));
        user.setMustChangePassword(true);
        user.setFailedLoginAttempts(3);
        user.setLockoutUntil(LocalDateTime.of(2026, 8, 28, 10, 10));
        when(securityService.requireAuthenticatedActiveUser()).thenReturn(user);
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(requestRepository.findActiveByTargetUserIdForUpdate(user.getId())).thenReturn(List.of());

        service.changeCurrentPassword(
                "existing secure password", "a completely new password", "a completely new password"
        );

        assertFalse(user.isMustChangePassword());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockoutUntil());
        assertTrue(encoder.matches("a completely new password", user.getPasswordHash()));
        verify(auditRepository).append(any(AccountSecurityAudit.class));
        verify(eventPublisher).publishEvent(new CredentialsChangedEvent(user.getId()));
    }

    @Test
    void adminUnlockClearsRecruiterLockAndAppendsAudit() {
        User actor = recruiter();
        actor.setRole(Role.ADMIN);
        User target = recruiter();
        target.setFailedLoginAttempts(5);
        target.setLockoutUntil(LocalDateTime.of(2026, 8, 28, 2, 15));
        when(securityService.requireAdmin()).thenReturn(actor);
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));

        service.unlockRecruiter(target.getId());

        assertEquals(0, target.getFailedLoginAttempts());
        assertNull(target.getLockoutUntil());
        verify(auditRepository).append(any(AccountSecurityAudit.class));
    }

    @Test
    void adminDeactivationInvalidatesResetRequestsAndExpiresSessionsAfterCommit() {
        User actor = recruiter();
        actor.setRole(Role.ADMIN);
        User target = recruiter();
        com.company.iss.auth.entity.PasswordResetRequest request =
                com.company.iss.auth.entity.PasswordResetRequest.issue(
                        target,
                        actor,
                        "public-request-id",
                        "a".repeat(64),
                        LocalDateTime.of(2026, 8, 28, 2, 30)
                );
        when(securityService.requireAdmin()).thenReturn(actor);
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(requestRepository.findActiveByTargetUserIdForUpdate(target.getId())).thenReturn(List.of(request));

        service.setRecruiterActive(target.getId(), false);

        assertFalse(target.isActive());
        assertEquals(LocalDateTime.of(2026, 8, 28, 2, 0), request.getInvalidatedAt());
        verify(eventPublisher).publishEvent(new CredentialsChangedEvent(target.getId()));
        verify(auditRepository).append(any(AccountSecurityAudit.class));
    }

    @Test
    void nonAdminAccountCommandIsRejectedBeforeTargetLookup() {
        when(securityService.requireAdmin()).thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class, () -> service.unlockRecruiter(10L));

        verify(userRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(auditRepository, eventPublisher);
    }

    private User recruiter() {
        User user = new User();
        user.setId(10L);
        user.setEmail("recruiter@example.test");
        user.setFullName("Recruiter User");
        user.setRole(Role.RECRUITER);
        user.setActive(true);
        return user;
    }
}
