package com.company.iss.notification.repository;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.notification.config.SmtpProvider;
import com.company.iss.notification.config.SmtpSecurity;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.entity.NotificationSettingsAudit;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.repository.CrudRepository;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class NotificationSettingsAuditRepositoryTest {

    @Autowired NotificationSettingsRepository settingsRepository;
    @Autowired NotificationSettingsAuditRepository auditRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager entityManager;

    @Test
    void persistedAuditIsIgnoredByDirtyChecking() throws ReflectiveOperationException {
        User actor = userRepository.saveAndFlush(actor());
        NotificationSettings settings = settingsRepository.saveAndFlush(settings());
        auditRepository.append(NotificationSettingsAudit.settingsUpdated(
                settings,
                actor,
                LocalDateTime.of(2026, 8, 31, 10, 0),
                "SMTP_SERVER,SENDER_IDENTITY"
        ));
        entityManager.flush();
        entityManager.clear();

        NotificationSettingsAudit persisted = auditRepository
                .findBySettingsIdOrderByOccurredAtAscIdAsc(settings.getId()).getFirst();
        Field changedFields = NotificationSettingsAudit.class.getDeclaredField("changedFields");
        changedFields.setAccessible(true);
        changedFields.set(persisted, "TAMPERED");
        entityManager.flush();
        entityManager.clear();

        assertEquals(
                "SMTP_SERVER,SENDER_IDENTITY",
                auditRepository.findBySettingsIdOrderByOccurredAtAscIdAsc(settings.getId())
                        .getFirst().getChangedFields()
        );
    }

    @Test
    void repositoryContractExposesOnlyAppendAndQueries() {
        Set<String> methods = Arrays.stream(NotificationSettingsAuditRepository.class.getMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertTrue(methods.containsAll(Set.of(
                "append",
                "count",
                "findBySettingsIdOrderByOccurredAtAscIdAsc"
        )));
        assertFalse(CrudRepository.class.isAssignableFrom(NotificationSettingsAuditRepository.class));
        assertFalse(methods.stream().anyMatch(name -> name.startsWith("save")
                || name.startsWith("delete") || name.startsWith("update")));
    }

    private User actor() {
        User user = new User();
        user.setEmail("notification-audit@example.test");
        user.setPasswordHash("test-only-hash");
        user.setFullName("Notification Audit Admin");
        user.setRole(Role.ADMIN);
        user.setActive(true);
        return user;
    }

    private NotificationSettings settings() {
        NotificationSettings settings = new NotificationSettings();
        settings.setCompanyName("ISS Notifications");
        settings.setEmailEnabled(false);
        settings.setSmsEnabled(false);
        settings.setSmtpProvider(SmtpProvider.CUSTOM);
        settings.setSmtpSecurity(SmtpSecurity.STARTTLS);
        settings.setActive(true);
        return settings;
    }
}
