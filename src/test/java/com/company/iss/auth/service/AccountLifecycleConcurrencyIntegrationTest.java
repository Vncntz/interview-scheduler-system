package com.company.iss.auth.service;

import com.company.iss.auth.config.AccountSecurityProperties;
import com.company.iss.auth.entity.AccountSecurityEvent;
import com.company.iss.auth.entity.PasswordResetRequest;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.exception.InvalidPasswordResetTokenException;
import com.company.iss.auth.repository.AccountSecurityAuditRepository;
import com.company.iss.auth.repository.PasswordResetRequestRepository;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.notification.service.PasswordResetNotificationReadiness;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({
        AccountAuthenticationService.class,
        AccountAuthenticationProvider.class,
        PasswordResetService.class,
        PasswordResetTokenService.class,
        PasswordPolicy.class,
        AccountLifecycleConcurrencyIntegrationTest.TestBeans.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccountLifecycleConcurrencyIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired PasswordResetRequestRepository requestRepository;
    @Autowired AccountSecurityAuditRepository auditRepository;
    @Autowired org.springframework.security.authentication.AuthenticationManager authenticationManager;
    @Autowired PasswordResetService resetService;
    @Autowired PasswordResetTokenService tokenService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired BlockingPasswordEncoder blockingPasswordEncoder;

    @MockitoBean SecurityService securityService;
    @MockitoBean PasswordResetNotificationReadiness readiness;
    @MockitoBean ApplicationEventPublisher eventPublisher;

    @Test
    void concurrentBadCredentialsAreSerializedWithoutLostUpdates() throws Exception {
        User recruiter = userRepository.saveAndFlush(recruiter("lockout-concurrency@example.test"));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(5)) {
            List<Callable<Void>> attempts = java.util.stream.IntStream.range(0, 5)
                    .mapToObj(index -> (Callable<Void>) () -> {
                        start.await();
                        try {
                            authenticationManager.authenticate(
                                    org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                                            .unauthenticated(recruiter.getEmail(), "wrong password")
                            );
                        } catch (org.springframework.security.core.AuthenticationException ignored) {
                            // Each wrong attempt is expected to fail authentication.
                        }
                        return null;
                    })
                    .toList();
            var futures = attempts.stream().map(executor::submit).toList();
            start.countDown();
            for (var future : futures) {
                future.get();
            }
        }

        User persisted = userRepository.findById(recruiter.getId()).orElseThrow();
        assertEquals(5, persisted.getFailedLoginAttempts());
        assertEquals(LocalDateTime.of(2026, 8, 28, 2, 15), persisted.getLockoutUntil());
        assertEquals(6, auditRepository.findByTargetUserIdOrderByOccurredAtDesc(recruiter.getId()).size());
    }

    @Test
    void fifthFailureCommitsLockBeforeWaitingValidLoginCanAuthenticate() throws Exception {
        User newRecruiter = recruiter("fifth-first@example.test");
        newRecruiter.setFailedLoginAttempts(4);
        User recruiter = userRepository.saveAndFlush(newRecruiter);
        blockingPasswordEncoder.block("wrong password");

        try (var executor = Executors.newFixedThreadPool(2)) {
            var fifthFailure = executor.submit(() -> authenticationFailure(
                    recruiter.getEmail(), "wrong password"
            ));
            blockingPasswordEncoder.awaitBlockedMatch();
            CountDownLatch validStarted = new CountDownLatch(1);
            var waitingValid = executor.submit(() -> {
                validStarted.countDown();
                return authenticationFailure(recruiter.getEmail(), "existing secure password");
            });
            assertTrue(validStarted.await(5, TimeUnit.SECONDS));
            blockingPasswordEncoder.release();

            assertTrue(fifthFailure.get() instanceof org.springframework.security.authentication.BadCredentialsException);
            assertTrue(waitingValid.get() instanceof org.springframework.security.authentication.LockedException);
        } finally {
            blockingPasswordEncoder.release();
        }

        User persisted = userRepository.findById(recruiter.getId()).orElseThrow();
        assertEquals(5, persisted.getFailedLoginAttempts());
        assertEquals(LocalDateTime.of(2026, 8, 28, 2, 15), persisted.getLockoutUntil());
        var audits = auditRepository.findByTargetUserIdOrderByOccurredAtDesc(recruiter.getId());
        assertEquals(1, audits.stream().filter(audit -> audit.getEvent() == AccountSecurityEvent.LOGIN_FAILED).count());
        assertEquals(1, audits.stream().filter(audit -> audit.getEvent() == AccountSecurityEvent.ACCOUNT_LOCKED).count());
        assertEquals(0, audits.stream().filter(audit -> audit.getEvent() == AccountSecurityEvent.LOGIN_SUCCEEDED).count());
    }

    @Test
    void validLoginCommitsResetBeforeWaitingBadPasswordBecomesAttemptOne() throws Exception {
        User newRecruiter = recruiter("valid-first@example.test");
        newRecruiter.setFailedLoginAttempts(4);
        User recruiter = userRepository.saveAndFlush(newRecruiter);
        blockingPasswordEncoder.block("existing secure password");

        try (var executor = Executors.newFixedThreadPool(2)) {
            var valid = executor.submit(() -> {
                try {
                    return authenticationManager.authenticate(
                            org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                                    .unauthenticated(recruiter.getEmail(), "existing secure password")
                    );
                } catch (org.springframework.security.core.AuthenticationException exception) {
                    return exception;
                }
            });
            blockingPasswordEncoder.awaitBlockedMatch();
            CountDownLatch badStarted = new CountDownLatch(1);
            var waitingBad = executor.submit(() -> {
                badStarted.countDown();
                return authenticationFailure(recruiter.getEmail(), "wrong password");
            });
            assertTrue(badStarted.await(5, TimeUnit.SECONDS));
            blockingPasswordEncoder.release();

            assertTrue(valid.get() instanceof org.springframework.security.core.Authentication);
            assertTrue(waitingBad.get() instanceof org.springframework.security.authentication.BadCredentialsException);
        } finally {
            blockingPasswordEncoder.release();
        }

        User persisted = userRepository.findById(recruiter.getId()).orElseThrow();
        assertEquals(1, persisted.getFailedLoginAttempts());
        assertEquals(null, persisted.getLockoutUntil());
        assertEquals(LocalDateTime.of(2026, 8, 28, 2, 0), persisted.getLastLoginAt());
        var audits = auditRepository.findByTargetUserIdOrderByOccurredAtDesc(recruiter.getId());
        assertEquals(1, audits.stream().filter(audit -> audit.getEvent() == AccountSecurityEvent.LOGIN_FAILED).count());
        assertEquals(1, audits.stream().filter(audit -> audit.getEvent() == AccountSecurityEvent.LOGIN_SUCCEEDED).count());
    }

    @Test
    void concurrentResetConsumptionHasExactlyOneWinner() throws Exception {
        User admin = userRepository.saveAndFlush(admin());
        User recruiter = userRepository.saveAndFlush(recruiter("reset-concurrency@example.test"));
        String publicId = tokenService.newPublicRequestId();
        String token = tokenService.tokenFor(publicId);
        PasswordResetRequest request = requestRepository.saveAndFlush(PasswordResetRequest.issue(
                recruiter,
                admin,
                publicId,
                tokenService.hash(token),
                LocalDateTime.of(2026, 8, 28, 2, 30)
        ));

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> consume = () -> {
                start.await();
                try {
                    resetService.resetPassword(token, "a completely new password", "a completely new password");
                    return true;
                } catch (InvalidPasswordResetTokenException exception) {
                    return false;
                }
            };
            var first = executor.submit(consume);
            var second = executor.submit(consume);
            start.countDown();
            assertEquals(1, java.util.stream.Stream.of(first.get(), second.get()).filter(Boolean::booleanValue).count());
        }

        PasswordResetRequest persistedRequest = requestRepository.findById(request.getId()).orElseThrow();
        User persistedUser = userRepository.findById(recruiter.getId()).orElseThrow();
        assertNotNull(persistedRequest.getConsumedAt());
        assertTrue(passwordEncoder.matches("a completely new password", persistedUser.getPasswordHash()));
        assertEquals(
                1,
                auditRepository.findByTargetUserIdOrderByOccurredAtDesc(recruiter.getId()).stream()
                        .filter(audit -> audit.getEvent() == AccountSecurityEvent.PASSWORD_RESET_COMPLETED)
                        .count()
        );
    }

    private User recruiter(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("existing secure password"));
        user.setFullName("Recruiter User");
        user.setRole(Role.RECRUITER);
        user.setActive(true);
        return user;
    }

    private User admin() {
        User user = new User();
        user.setEmail("concurrency-admin@example.test");
        user.setPasswordHash(passwordEncoder.encode("existing secure password"));
        user.setFullName("Admin User");
        user.setRole(Role.ADMIN);
        user.setActive(true);
        return user;
    }

    private org.springframework.security.core.AuthenticationException authenticationFailure(
            String email,
            String password
    ) {
        try {
            authenticationManager.authenticate(
                    org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                            .unauthenticated(email, password)
            );
            throw new AssertionError("Authentication unexpectedly succeeded.");
        } catch (org.springframework.security.core.AuthenticationException exception) {
            return exception;
        }
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        AccountSecurityProperties accountSecurityProperties() {
            AccountSecurityProperties properties = new AccountSecurityProperties();
            properties.getPasswordReset().setPublicBaseUrl("https://iss.example.test");
            properties.getPasswordReset().setSigningSecret(
                    Base64.getEncoder().encodeToString(new byte[32])
            );
            return properties;
        }

        @Bean
        BlockingPasswordEncoder passwordEncoder() {
            return new BlockingPasswordEncoder();
        }

        @Bean
        org.springframework.security.authentication.AuthenticationManager authenticationManager(
                AccountAuthenticationProvider provider
        ) {
            return new org.springframework.security.authentication.ProviderManager(provider);
        }

        @Bean
        Clock accountSecurityClock() {
            return Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        SecureRandom accountSecurityRandom() {
            return new SecureRandom();
        }
    }

    static final class BlockingPasswordEncoder implements PasswordEncoder {

        private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();
        private volatile String blockedRawPassword;
        private volatile CountDownLatch entered = new CountDownLatch(0);
        private volatile CountDownLatch release = new CountDownLatch(0);

        void block(String rawPassword) {
            blockedRawPassword = rawPassword;
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        void awaitBlockedMatch() throws InterruptedException {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
        }

        void release() {
            release.countDown();
            blockedRawPassword = null;
        }

        @Override
        public String encode(CharSequence rawPassword) {
            return delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            if (rawPassword != null && rawPassword.toString().equals(blockedRawPassword)) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release password verification.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Password verification was interrupted.", exception);
                }
            }
            return delegate.matches(rawPassword, encodedPassword);
        }
    }
}
