package com.company.iss.auth.repository;

import com.company.iss.auth.entity.AccountSecurityAudit;
import com.company.iss.auth.entity.AccountSecurityEvent;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
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
class AccountSecurityAuditRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired AccountSecurityAuditRepository auditRepository;
    @Autowired EntityManager entityManager;

    @Test
    void persistedAuditIsIgnoredByDirtyChecking() throws ReflectiveOperationException {
        User user = userRepository.saveAndFlush(user());
        auditRepository.append(AccountSecurityAudit.record(
                user,
                user,
                AccountSecurityEvent.PASSWORD_CHANGED,
                LocalDateTime.of(2026, 8, 28, 10, 0),
                "SELF_SERVICE"
        ));
        entityManager.flush();
        entityManager.clear();

        AccountSecurityAudit persisted = auditRepository
                .findByTargetUserIdOrderByOccurredAtDesc(user.getId()).getFirst();
        Field reason = AccountSecurityAudit.class.getDeclaredField("reasonCode");
        reason.setAccessible(true);
        reason.set(persisted, "TAMPERED");
        entityManager.flush();
        entityManager.clear();

        assertEquals(
                "SELF_SERVICE",
                auditRepository.findByTargetUserIdOrderByOccurredAtDesc(user.getId()).getFirst().getReasonCode()
        );
    }

    @Test
    void repositoryContractExposesOnlyAppendAndQueries() {
        Set<String> methodNames = Arrays.stream(AccountSecurityAuditRepository.class.getMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertTrue(methodNames.containsAll(Set.of("append", "count", "findByTargetUserIdOrderByOccurredAtDesc")));
        assertFalse(CrudRepository.class.isAssignableFrom(AccountSecurityAuditRepository.class));
        assertFalse(methodNames.stream().anyMatch(name -> name.startsWith("save")
                || name.startsWith("delete") || name.startsWith("update")));
    }

    private User user() {
        User user = new User();
        user.setEmail("audit-user@example.test");
        user.setPasswordHash("test-only-hash");
        user.setFullName("Audit User");
        user.setRole(Role.ADMIN);
        user.setActive(true);
        return user;
    }
}
