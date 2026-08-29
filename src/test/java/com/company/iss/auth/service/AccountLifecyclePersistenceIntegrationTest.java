package com.company.iss.auth.service;

import com.company.iss.auth.config.AccountSecurityProperties;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({
        AccountLifecycleService.class,
        AccountAuthenticationService.class,
        AccountAuthenticationProvider.class,
        PasswordPolicy.class,
        AccountLifecyclePersistenceIntegrationTest.TestBeans.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccountLifecyclePersistenceIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired AccountLifecycleService accountLifecycleService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuthenticationManager authenticationManager;
    @Autowired EntityManager entityManager;

    @MockitoBean SecurityService securityService;
    @MockitoBean ApplicationEventPublisher eventPublisher;

    @Test
    void successfulPasswordChangePersistsClearedSecurityStateAndAllowsNewAuthentication() {
        User recruiter = new User();
        recruiter.setEmail("password-change-persistence@example.test");
        recruiter.setPasswordHash(passwordEncoder.encode("existing secure password"));
        recruiter.setFullName("Password Change Recruiter");
        recruiter.setRole(Role.RECRUITER);
        recruiter.setActive(true);
        recruiter.setMustChangePassword(true);
        recruiter.setFailedLoginAttempts(4);
        recruiter.setLockoutUntil(LocalDateTime.of(2026, 8, 28, 2, 15));
        recruiter = userRepository.saveAndFlush(recruiter);
        when(securityService.requireAuthenticatedActiveUser()).thenReturn(recruiter);

        accountLifecycleService.changeCurrentPassword(
                "existing secure password",
                "a completely new password",
                "a completely new password"
        );
        entityManager.clear();

        User persisted = userRepository.findById(recruiter.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("a completely new password", persisted.getPasswordHash()));
        assertFalse(persisted.isMustChangePassword());
        assertEquals(0, persisted.getFailedLoginAttempts());
        assertNull(persisted.getLockoutUntil());

        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        persisted.getEmail(),
                        "a completely new password"
                )
        );
        assertTrue(authentication.isAuthenticated());
        assertEquals(persisted.getEmail(), authentication.getName());
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        AccountSecurityProperties accountSecurityProperties() {
            return new AccountSecurityProperties();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        AuthenticationManager authenticationManager(AccountAuthenticationProvider provider) {
            return new org.springframework.security.authentication.ProviderManager(provider);
        }

        @Bean
        Clock accountSecurityClock() {
            return Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC);
        }
    }
}
