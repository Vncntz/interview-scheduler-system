package com.company.iss.notification.service;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.branch.entity.Branch;
import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.repository.NotificationSettingsRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationSettingsServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCanSaveCompleteEmailConfiguration() {
        NotificationSettingsRepository repository = mock(NotificationSettingsRepository.class);
        NotificationRuntimeProperties properties = new NotificationRuntimeProperties();
        properties.getSmtp().setPassword("runtime-only-test-fixture");
        NotificationSettingsService service = service(repository, properties, Role.ADMIN);
        NotificationSettings settings = completeSettings();
        settings.setEmailEnabled(true);
        when(repository.save(settings)).thenReturn(settings);

        assertDoesNotThrow(() -> service.save(settings));

        verify(repository).save(settings);
    }

    @Test
    void recruiterCannotSaveGlobalNotificationConfiguration() {
        NotificationSettingsRepository repository = mock(NotificationSettingsRepository.class);
        NotificationSettingsService service = service(
                repository,
                new NotificationRuntimeProperties(),
                Role.RECRUITER
        );

        assertThrows(AccessDeniedException.class, () -> service.save(completeSettings()));

        verify(repository, never()).save(any());
    }

    @Test
    void enablingEmailWithoutRuntimePasswordIsRejected() {
        NotificationSettingsRepository repository = mock(NotificationSettingsRepository.class);
        NotificationSettingsService service = service(
                repository, new NotificationRuntimeProperties(), Role.ADMIN
        );
        NotificationSettings settings = completeSettings();
        settings.setEmailEnabled(true);

        assertThrows(BusinessRuleViolationException.class, () -> service.save(settings));

        verify(repository, never()).save(any());
    }

    @Test
    void enablingUnsupportedSmsIsRejectedEvenWhenMetadataIsPresent() {
        NotificationSettingsRepository repository = mock(NotificationSettingsRepository.class);
        NotificationSettingsService service = service(
                repository, new NotificationRuntimeProperties(), Role.ADMIN
        );
        NotificationSettings settings = completeSettings();
        settings.setSmsEnabled(true);
        settings.setSmsProvider("provider");
        settings.setSmsSenderName("sender");

        assertThrows(BusinessRuleViolationException.class, () -> service.save(settings));

        verify(repository, never()).save(any());
    }

    private NotificationSettingsService service(
            NotificationSettingsRepository settingsRepository,
            NotificationRuntimeProperties properties,
            Role role
    ) {
        User actor = new User();
        actor.setEmail("actor@example.test");
        actor.setRole(role);
        actor.setActive(true);
        actor.setMustChangePassword(false);
        if (role == Role.RECRUITER) {
            Branch branch = new Branch();
            branch.setId(1L);
            actor.setBranch(branch);
        }

        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.getEmail(), "ignored", List.of())
        );
        SecurityService securityService = new SecurityService(
                userRepository,
                Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC)
        );
        return new NotificationSettingsService(settingsRepository, properties, securityService);
    }

    private NotificationSettings completeSettings() {
        NotificationSettings settings = new NotificationSettings();
        settings.setCompanyName("ISS Notifications");
        settings.setEmailEnabled(false);
        settings.setSmsEnabled(false);
        settings.setSmtpHost("smtp.example.test");
        settings.setSmtpPort(587);
        settings.setSmtpUsername("mailer@example.test");
        return settings;
    }
}
