package com.company.iss.auth.service;

import com.company.iss.auth.config.AccountSecurityProperties;
import com.company.iss.auth.entity.AccountSecurityAudit;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.event.CredentialsChangedEvent;
import com.company.iss.auth.repository.AccountSecurityAuditRepository;
import com.company.iss.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAuthenticationServiceTest {

    @Mock UserRepository userRepository;
    @Mock AccountSecurityAuditRepository auditRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC);
    private BCryptPasswordEncoder encoder;
    private AccountAuthenticationService service;

    @BeforeEach
    void setUp() {
        encoder = spy(new BCryptPasswordEncoder());
        service = new AccountAuthenticationService(
                userRepository,
                auditRepository,
                encoder,
                new AccountSecurityProperties(),
                eventPublisher,
                clock
        );
    }

    @Test
    void unknownAccountPerformsDummyBcryptAndWritesNothing() {
        when(userRepository.findByEmailForUpdate("unknown@example.test")).thenReturn(Optional.empty());

        assertThrows(
                BadCredentialsException.class,
                () -> service.authenticate("unknown@example.test", "submitted password")
        );

        verify(encoder).matches(
                org.mockito.ArgumentMatchers.eq("submitted password"),
                org.mockito.ArgumentMatchers.anyString()
        );
        verifyNoInteractions(auditRepository, eventPublisher);
    }

    @Test
    void inactiveAndCurrentlyLockedAccountsDoNotVerifyOrIncrement() {
        User inactive = user("inactive@example.test");
        inactive.setActive(false);
        User locked = user("locked@example.test");
        locked.setFailedLoginAttempts(5);
        locked.setLockoutUntil(LocalDateTime.of(2026, 8, 28, 2, 15));
        when(userRepository.findByEmailForUpdate(inactive.getEmail())).thenReturn(Optional.of(inactive));
        when(userRepository.findByEmailForUpdate(locked.getEmail())).thenReturn(Optional.of(locked));

        assertThrows(DisabledException.class, () -> service.authenticate(inactive.getEmail(), "submitted password"));
        assertThrows(LockedException.class, () -> service.authenticate(locked.getEmail(), "submitted password"));

        assertEquals(5, locked.getFailedLoginAttempts());
        assertEquals(LocalDateTime.of(2026, 8, 28, 2, 15), locked.getLockoutUntil());
        verify(encoder, never()).matches("submitted password", inactive.getPasswordHash());
        verify(encoder, never()).matches("submitted password", locked.getPasswordHash());
        verifyNoInteractions(auditRepository, eventPublisher);
    }

    @Test
    void wrongPasswordAfterExpiredLockoutRestartsAtOne() {
        User user = user("expired@example.test");
        user.setFailedLoginAttempts(5);
        user.setLockoutUntil(LocalDateTime.of(2026, 8, 28, 1, 59));
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));

        assertThrows(BadCredentialsException.class, () -> service.authenticate(user.getEmail(), "wrong password"));

        assertEquals(1, user.getFailedLoginAttempts());
        assertNull(user.getLockoutUntil());
        verify(auditRepository).append(any(AccountSecurityAudit.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void fifthWrongPasswordCommitsTimedLockAndRequestsSessionExpiry() {
        User user = user("fifth@example.test");
        user.setFailedLoginAttempts(4);
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));

        assertThrows(BadCredentialsException.class, () -> service.authenticate(user.getEmail(), "wrong password"));

        assertEquals(5, user.getFailedLoginAttempts());
        assertEquals(LocalDateTime.of(2026, 8, 28, 2, 15), user.getLockoutUntil());
        verify(eventPublisher).publishEvent(new CredentialsChangedEvent(user.getId()));
    }

    @Test
    void correctPasswordClearsExpiredStateAndReturnsAuthorities() {
        User user = user("success@example.test");
        user.setFailedLoginAttempts(5);
        user.setLockoutUntil(LocalDateTime.of(2026, 8, 28, 1, 59));
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));

        var result = service.authenticate(user.getEmail(), "existing secure password");

        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockoutUntil());
        assertEquals(LocalDateTime.of(2026, 8, 28, 2, 0), user.getLastLoginAt());
        assertEquals("success@example.test", result.getUsername());
        assertTrue(result.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_RECRUITER")));
        verify(auditRepository).append(any(AccountSecurityAudit.class));
    }

    private User user(String email) {
        User user = new User();
        user.setId(10L);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode("existing secure password"));
        user.setFullName("Recruiter User");
        user.setRole(Role.RECRUITER);
        user.setActive(true);
        return user;
    }
}
