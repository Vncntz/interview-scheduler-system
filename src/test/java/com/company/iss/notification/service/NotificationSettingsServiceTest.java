package com.company.iss.notification.service;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.branch.entity.Branch;
import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.config.SmtpProvider;
import com.company.iss.notification.config.SmtpSecurity;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.entity.NotificationSettingsAudit;
import com.company.iss.notification.repository.NotificationSettingsAuditRepository;
import com.company.iss.notification.repository.NotificationSettingsRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationSettingsServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-29T00:00:00Z"),
            ZoneOffset.UTC
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCanSaveCompleteEmailConfigurationAndAppendAudit() {
        Fixture fixture = fixture(Role.ADMIN, "runtime-only-test-fixture");
        NotificationSettings settings = completeSettings();
        when(fixture.repository().findByActiveTrue()).thenReturn(Optional.of(settings));
        when(fixture.repository().saveAndFlush(settings)).thenReturn(settings);

        assertDoesNotThrow(() -> fixture.service().save(settings));

        verify(fixture.repository()).saveAndFlush(settings);
        verify(fixture.audits()).append(any(NotificationSettingsAudit.class));
    }

    @Test
    void recruiterCannotSaveGlobalNotificationConfiguration() {
        Fixture fixture = fixture(Role.RECRUITER, "runtime-only-test-fixture");

        assertThrows(AccessDeniedException.class, () -> fixture.service().save(completeSettings()));

        verify(fixture.repository(), never()).saveAndFlush(any());
        verify(fixture.audits(), never()).append(any());
    }

    @Test
    void enablingEmailWithoutRuntimePasswordIsRejected() {
        Fixture fixture = fixture(Role.ADMIN, "");
        NotificationSettings settings = completeSettings();

        assertThrows(BusinessRuleViolationException.class, () -> fixture.service().save(settings));

        verify(fixture.repository(), never()).saveAndFlush(any());
    }

    @Test
    void disabledEmailMayBeSavedWithIncompleteSmtpConfiguration() {
        Fixture fixture = fixture(Role.ADMIN, "");
        NotificationSettings settings = completeSettings();
        settings.setEmailEnabled(false);
        settings.setSmtpHost(null);
        settings.setSmtpUsername(null);
        settings.setSmtpFromAddress(null);
        when(fixture.repository().findByActiveTrue()).thenReturn(Optional.of(settings));
        when(fixture.repository().saveAndFlush(settings)).thenReturn(settings);

        assertDoesNotThrow(() -> fixture.service().save(settings));
    }

    @Test
    void unsupportedSmsAndArbitrarySecondActiveRowAreRejected() {
        Fixture fixture = fixture(Role.ADMIN, "runtime-only-test-fixture");
        NotificationSettings sms = completeSettings();
        sms.setSmsEnabled(true);
        assertThrows(BusinessRuleViolationException.class, () -> fixture.service().save(sms));

        NotificationSettings current = completeSettings();
        NotificationSettings replacement = completeSettings();
        replacement.setId(2L);
        when(fixture.repository().findByActiveTrue()).thenReturn(Optional.of(current));
        assertThrows(BusinessRuleViolationException.class, () -> fixture.service().save(replacement));
    }

    @Test
    void optimisticConflictIsConvertedToUserSafeBusinessError() {
        Fixture fixture = fixture(Role.ADMIN, "runtime-only-test-fixture");
        NotificationSettings settings = completeSettings();
        when(fixture.repository().findByActiveTrue()).thenReturn(Optional.of(settings));
        when(fixture.repository().saveAndFlush(settings))
                .thenThrow(new OptimisticLockingFailureException("raw conflict"));

        assertThrows(BusinessRuleViolationException.class, () -> fixture.service().save(settings));
        verify(fixture.audits(), never()).append(any());
    }

    @Test
    void auditFailureIsNotSwallowedByTransactionalSave() {
        Fixture fixture = fixture(Role.ADMIN, "runtime-only-test-fixture");
        NotificationSettings settings = completeSettings();
        when(fixture.repository().findByActiveTrue()).thenReturn(Optional.of(settings));
        when(fixture.repository().saveAndFlush(settings)).thenReturn(settings);
        doThrow(new IllegalStateException("audit persistence failed"))
                .when(fixture.audits()).append(any());

        assertThrows(IllegalStateException.class, () -> fixture.service().save(settings));
    }

    private Fixture fixture(Role role, String password) {
        NotificationSettingsRepository repository = mock(NotificationSettingsRepository.class);
        NotificationSettingsAuditRepository audits = mock(NotificationSettingsAuditRepository.class);
        NotificationRuntimeProperties properties = new NotificationRuntimeProperties();
        properties.getSmtp().setPassword(password);
        SmtpConfigurationValidator validator = new SmtpConfigurationValidator(properties);
        return new Fixture(
                repository,
                audits,
                new NotificationSettingsService(
                        repository,
                        audits,
                        properties,
                        validator,
                        securityService(role),
                        CLOCK
                )
        );
    }

    private SecurityService securityService(Role role) {
        User actor = new User();
        actor.setId(99L);
        actor.setEmail("actor@example.test");
        actor.setRole(role);
        actor.setActive(true);
        actor.setMustChangePassword(false);
        if (role == Role.RECRUITER) {
            Branch branch = new Branch();
            branch.setId(1L);
            actor.setBranch(branch);
        }
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.getEmail(), "ignored", List.of())
        );
        return new SecurityService(users, CLOCK);
    }

    private NotificationSettings completeSettings() {
        NotificationSettings settings = new NotificationSettings();
        settings.setId(1L);
        settings.setVersion(0L);
        settings.setCompanyName("ISS Notifications");
        settings.setEmailEnabled(true);
        settings.setSmsEnabled(false);
        settings.setSmtpProvider(SmtpProvider.CUSTOM);
        settings.setSmtpHost("smtp.example.test");
        settings.setSmtpPort(587);
        settings.setSmtpSecurity(SmtpSecurity.STARTTLS);
        settings.setSmtpUsername("mailer@example.test");
        settings.setSmtpFromName("Interview Scheduler");
        settings.setSmtpFromAddress("notifications@example.test");
        settings.setActive(true);
        return settings;
    }

    private record Fixture(
            NotificationSettingsRepository repository,
            NotificationSettingsAuditRepository audits,
            NotificationSettingsService service
    ) {
    }
}
