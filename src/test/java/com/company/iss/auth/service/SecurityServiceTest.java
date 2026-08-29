package com.company.iss.auth.service;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    @Mock UserRepository userRepository;
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void operationsBoundaryRejectsForcedPasswordChangeAndCurrentLockout() {
        User user = authenticatedUser();
        SecurityService service = serviceFor(user);
        user.setMustChangePassword(true);
        assertThrows(AccessDeniedException.class, service::requireOperationsUser);

        user.setMustChangePassword(false);
        user.setLockoutUntil(LocalDateTime.of(2026, 8, 28, 2, 15));
        assertThrows(AccessDeniedException.class, service::requireOperationsUser);
    }

    @Test
    void activeAdminWithoutForcedChangePassesBoundary() {
        User user = authenticatedUser();
        assertSame(user, serviceFor(user).requireAdmin());
    }

    @Test
    void customUnauthorizedMessageAppliesOnlyToWrongRole() {
        User applicant = authenticatedUser();
        applicant.setRole(Role.APPLICANT);
        AccessDeniedException wrongRole = assertThrows(
                AccessDeniedException.class,
                () -> serviceFor(applicant).requireOperationsUser("Booking role denied.")
        );
        assertEquals("Booking role denied.", wrongRole.getMessage());

        User inactive = authenticatedUser();
        inactive.setActive(false);
        AccessDeniedException inactiveFailure = assertThrows(
                AccessDeniedException.class,
                () -> serviceFor(inactive).requireOperationsUser("Booking role denied.")
        );
        assertEquals("An active authenticated user is required.", inactiveFailure.getMessage());
    }

    @Test
    void noArgBoundaryKeepsDefaultRoleMessageAndRecruiterBranchRule() {
        User applicant = authenticatedUser();
        applicant.setRole(Role.APPLICANT);
        AccessDeniedException wrongRole = assertThrows(
                AccessDeniedException.class,
                () -> serviceFor(applicant).requireOperationsUser()
        );
        assertEquals("You are not authorized to manage recruitment operations.", wrongRole.getMessage());

        User recruiter = authenticatedUser();
        recruiter.setRole(Role.RECRUITER);
        AccessDeniedException missingBranch = assertThrows(
                AccessDeniedException.class,
                () -> serviceFor(recruiter).requireOperationsUser("Booking role denied.")
        );
        assertEquals("Your recruiter account is not assigned to a branch.", missingBranch.getMessage());
    }

    private SecurityService serviceFor(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user.getEmail(), "n/a", java.util.List.of())
        );
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        return new SecurityService(userRepository, clock);
    }

    private User authenticatedUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("admin@example.test");
        user.setRole(Role.ADMIN);
        user.setActive(true);
        return user;
    }
}
