package com.company.iss.auth.service;

import com.company.iss.auth.config.AccountSecurityProperties;
import com.company.iss.auth.entity.AccountSecurityAudit;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordResetRequestRepository requestRepository;
    @Mock AccountSecurityAuditRepository auditRepository;
    @Mock SecurityService securityService;
    @Mock PasswordResetNotificationReadiness readiness;
    @Mock ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC);
    private AccountSecurityProperties properties;
    private PasswordResetTokenService tokenService;
    private BCryptPasswordEncoder encoder;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        properties = new AccountSecurityProperties();
        properties.getPasswordReset().setPublicBaseUrl("https://iss.example.test");
        properties.getPasswordReset().setSigningSecret(Base64.getEncoder().encodeToString(new byte[32]));
        tokenService = new PasswordResetTokenService(properties, new SecureRandom());
        encoder = new BCryptPasswordEncoder();
        service = new PasswordResetService(
                userRepository,
                requestRepository,
                auditRepository,
                securityService,
                tokenService,
                readiness,
                new PasswordPolicy(properties, encoder),
                encoder,
                properties,
                eventPublisher,
                clock
        );
    }

    @Test
    void adminInitiationStoresOnlyHashAndPublishesIdOnlyEvent() {
        User admin = admin();
        User recruiter = recruiter();
        when(securityService.requireAdmin()).thenReturn(admin);
        when(userRepository.findByIdForUpdate(recruiter.getId())).thenReturn(Optional.of(recruiter));
        when(requestRepository.findActiveByTargetUserIdForUpdate(recruiter.getId())).thenReturn(List.of());
        when(requestRepository.save(any())).thenAnswer(invocation -> {
            PasswordResetRequest request = invocation.getArgument(0);
            request.setId(30L);
            return request;
        });

        service.requestRecruiterReset(recruiter.getId());

        ArgumentCaptor<PasswordResetRequest> requestCaptor = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(requestRepository).save(requestCaptor.capture());
        PasswordResetRequest request = requestCaptor.getValue();
        assertEquals(64, request.getTokenHash().length());
        assertNotEquals(tokenService.tokenFor(request.getPublicRequestId()), request.getTokenHash());
        verify(eventPublisher).publishEvent(new PasswordResetRequestedEvent(30L));
        assertEquals(1, PasswordResetRequestedEvent.class.getRecordComponents().length);
        assertEquals(Long.class, PasswordResetRequestedEvent.class.getRecordComponents()[0].getType());
    }

    @Test
    void readinessFailureCreatesNothing() {
        User admin = admin();
        User recruiter = recruiter();
        when(securityService.requireAdmin()).thenReturn(admin);
        when(userRepository.findByIdForUpdate(recruiter.getId())).thenReturn(Optional.of(recruiter));
        doThrow(new BusinessRuleViolationException("not ready")).when(readiness).requireReady();

        assertThrows(BusinessRuleViolationException.class, () -> service.requestRecruiterReset(recruiter.getId()));

        verify(requestRepository, never()).save(any());
        verifyNoInteractions(auditRepository, eventPublisher);
    }

    @Test
    void initiationRotatesPriorRequestAndNeverChangesCredentials() {
        User admin = admin();
        User recruiter = recruiter();
        String originalHash = encoder.encode("existing secure password");
        recruiter.setPasswordHash(originalHash);
        PasswordResetRequest prior = PasswordResetRequest.issue(
                recruiter,
                admin,
                tokenService.newPublicRequestId(),
                "a".repeat(64),
                LocalDateTime.of(2026, 8, 28, 2, 30)
        );
        prior.setId(20L);
        when(securityService.requireAdmin()).thenReturn(admin);
        when(userRepository.findByIdForUpdate(recruiter.getId())).thenReturn(Optional.of(recruiter));
        when(requestRepository.findActiveByTargetUserIdForUpdate(recruiter.getId())).thenReturn(List.of(prior));
        when(requestRepository.save(any())).thenAnswer(invocation -> {
            PasswordResetRequest request = invocation.getArgument(0);
            request.setId(30L);
            return request;
        });

        service.requestRecruiterReset(recruiter.getId());

        assertEquals(LocalDateTime.of(2026, 8, 28, 2, 0), prior.getInvalidatedAt());
        assertEquals(originalHash, recruiter.getPasswordHash());
        assertFalse(recruiter.isMustChangePassword());
    }

    @Test
    void administratorCannotBeResetThroughRecruiterCommand() {
        User actor = admin();
        User targetAdmin = admin();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("other-admin@example.test");
        when(securityService.requireAdmin()).thenReturn(actor);
        when(userRepository.findByIdForUpdate(targetAdmin.getId())).thenReturn(Optional.of(targetAdmin));

        assertThrows(
                BusinessRuleViolationException.class,
                () -> service.requestRecruiterReset(targetAdmin.getId())
        );

        verify(requestRepository, never()).save(any());
        verifyNoInteractions(readiness, auditRepository, eventPublisher);
    }

    @Test
    void validSingleUseTokenChangesPasswordAndConsumesRequest() {
        User admin = admin();
        User recruiter = recruiter();
        recruiter.setPasswordHash(encoder.encode("existing secure password"));
        recruiter.setMustChangePassword(true);
        recruiter.setFailedLoginAttempts(5);
        recruiter.setLockoutUntil(LocalDateTime.of(2026, 8, 28, 2, 15));
        String publicId = tokenService.newPublicRequestId();
        String token = tokenService.tokenFor(publicId);
        PasswordResetRequest request = PasswordResetRequest.issue(
                recruiter, admin, publicId, tokenService.hash(token), LocalDateTime.of(2026, 8, 28, 2, 30)
        );
        request.setId(30L);
        when(requestRepository.findTargetUserIdByPublicRequestId(publicId)).thenReturn(Optional.of(recruiter.getId()));
        when(userRepository.findByIdForUpdate(recruiter.getId())).thenReturn(Optional.of(recruiter));
        when(requestRepository.findByPublicRequestIdForUpdate(publicId)).thenReturn(Optional.of(request));
        when(requestRepository.findActiveByTargetUserIdForUpdate(recruiter.getId())).thenReturn(List.of(request));

        service.resetPassword(token, "a completely new password", "a completely new password");

        assertTrue(encoder.matches("a completely new password", recruiter.getPasswordHash()));
        assertFalse(recruiter.isMustChangePassword());
        assertEquals(0, recruiter.getFailedLoginAttempts());
        assertNull(recruiter.getLockoutUntil());
        assertEquals(LocalDateTime.of(2026, 8, 28, 2, 0), request.getConsumedAt());
        verify(auditRepository).append(any(AccountSecurityAudit.class));
        verify(eventPublisher).publishEvent(new CredentialsChangedEvent(recruiter.getId()));
    }

    @Test
    void malformedExpiredConsumedAndWrongSignatureTokensShareOneExceptionType() {
        InvalidPasswordResetTokenException malformed = assertThrows(
                InvalidPasswordResetTokenException.class,
                () -> service.resetPassword("malformed", "a completely new password", "a completely new password")
        );

        String validToken = tokenService.tokenFor(tokenService.newPublicRequestId());
        InvalidPasswordResetTokenException wrongSignature = assertThrows(
                InvalidPasswordResetTokenException.class,
                () -> service.resetPassword(
                        validToken.substring(0, validToken.length() - 1) + "x",
                        "a completely new password",
                        "a completely new password"
                )
        );

        User admin = admin();
        User recruiter = recruiter();
        String expiredPublicId = tokenService.newPublicRequestId();
        String expiredToken = tokenService.tokenFor(expiredPublicId);
        PasswordResetRequest expiredRequest = PasswordResetRequest.issue(
                recruiter, admin, expiredPublicId, tokenService.hash(expiredToken), LocalDateTime.of(2026, 8, 28, 2, 0)
        );
        when(requestRepository.findTargetUserIdByPublicRequestId(expiredPublicId))
                .thenReturn(Optional.of(recruiter.getId()));
        when(userRepository.findByIdForUpdate(recruiter.getId())).thenReturn(Optional.of(recruiter));
        when(requestRepository.findByPublicRequestIdForUpdate(expiredPublicId)).thenReturn(Optional.of(expiredRequest));
        InvalidPasswordResetTokenException expired = assertThrows(
                InvalidPasswordResetTokenException.class,
                () -> service.resetPassword(expiredToken, "a completely new password", "a completely new password")
        );

        String consumedPublicId = tokenService.newPublicRequestId();
        String consumedToken = tokenService.tokenFor(consumedPublicId);
        PasswordResetRequest consumedRequest = PasswordResetRequest.issue(
                recruiter, admin, consumedPublicId, tokenService.hash(consumedToken), LocalDateTime.of(2026, 8, 28, 2, 30)
        );
        consumedRequest.consume(LocalDateTime.of(2026, 8, 28, 1, 59));
        when(requestRepository.findTargetUserIdByPublicRequestId(consumedPublicId))
                .thenReturn(Optional.of(recruiter.getId()));
        when(requestRepository.findByPublicRequestIdForUpdate(consumedPublicId)).thenReturn(Optional.of(consumedRequest));
        InvalidPasswordResetTokenException consumed = assertThrows(
                InvalidPasswordResetTokenException.class,
                () -> service.resetPassword(consumedToken, "a completely new password", "a completely new password")
        );

        assertEquals(malformed.getMessage(), wrongSignature.getMessage());
        assertEquals(malformed.getMessage(), expired.getMessage());
        assertEquals(malformed.getMessage(), consumed.getMessage());
        verify(auditRepository, never()).append(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void samePasswordResetIsRejectedWithoutConsumingOrAuditing() {
        User admin = admin();
        User recruiter = recruiter();
        recruiter.setPasswordHash(encoder.encode("existing secure password"));
        String publicId = tokenService.newPublicRequestId();
        String token = tokenService.tokenFor(publicId);
        PasswordResetRequest request = PasswordResetRequest.issue(
                recruiter, admin, publicId, tokenService.hash(token), LocalDateTime.of(2026, 8, 28, 2, 30)
        );
        request.setId(30L);
        when(requestRepository.findTargetUserIdByPublicRequestId(publicId)).thenReturn(Optional.of(recruiter.getId()));
        when(userRepository.findByIdForUpdate(recruiter.getId())).thenReturn(Optional.of(recruiter));
        when(requestRepository.findByPublicRequestIdForUpdate(publicId)).thenReturn(Optional.of(request));

        assertThrows(
                BusinessRuleViolationException.class,
                () -> service.resetPassword(token, "existing secure password", "existing secure password")
        );

        assertNull(request.getConsumedAt());
        verify(auditRepository, never()).append(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private User admin() {
        User user = new User();
        user.setId(1L);
        user.setEmail("admin@example.test");
        user.setFullName("Admin User");
        user.setRole(Role.ADMIN);
        user.setActive(true);
        return user;
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
