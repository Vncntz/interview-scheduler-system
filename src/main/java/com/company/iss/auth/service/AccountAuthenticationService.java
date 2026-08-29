package com.company.iss.auth.service;

import com.company.iss.auth.config.AccountSecurityProperties;
import com.company.iss.auth.entity.AccountSecurityAudit;
import com.company.iss.auth.entity.AccountSecurityEvent;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.event.CredentialsChangedEvent;
import com.company.iss.auth.repository.AccountSecurityAuditRepository;
import com.company.iss.auth.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AccountAuthenticationService {

    private static final String BAD_CREDENTIALS_MESSAGE = "Invalid username or password.";

    private final UserRepository userRepository;
    private final AccountSecurityAuditRepository auditRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountSecurityProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AccountAuthenticationService(
            UserRepository userRepository,
            AccountSecurityAuditRepository auditRepository,
            PasswordEncoder passwordEncoder,
            AccountSecurityProperties properties,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("authentication timing placeholder");
    }

    @Transactional(noRollbackFor = AuthenticationException.class)
    public UserDetails authenticate(String email, String password) {
        String submittedEmail = email == null ? "" : email;
        String submittedPassword = password == null ? "" : password;
        User user = userRepository.findByEmailForUpdate(submittedEmail).orElse(null);
        if (user == null) {
            passwordEncoder.matches(submittedPassword, dummyPasswordHash);
            throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (!user.isActive()) {
            throw new DisabledException(BAD_CREDENTIALS_MESSAGE);
        }
        if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(now)) {
            throw new LockedException(BAD_CREDENTIALS_MESSAGE);
        }

        boolean expiredLockout = user.getLockoutUntil() != null;
        if (!passwordEncoder.matches(submittedPassword, user.getPasswordHash())) {
            int attempts = expiredLockout ? 1 : user.getFailedLoginAttempts() + 1;
            user.setLockoutUntil(null);
            user.setFailedLoginAttempts(attempts);
            appendAudit(user, null, AccountSecurityEvent.LOGIN_FAILED, now, "BAD_CREDENTIALS");
            if (attempts >= properties.getLockout().getMaxFailedAttempts()) {
                user.setLockoutUntil(now.plus(properties.getLockout().getDuration()));
                appendAudit(user, null, AccountSecurityEvent.ACCOUNT_LOCKED, now, "FAILED_LOGIN_THRESHOLD");
                eventPublisher.publishEvent(new CredentialsChangedEvent(user.getId()));
            }
            throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
        }

        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        user.setLastLoginAt(now);
        appendAudit(user, user, AccountSecurityEvent.LOGIN_SUCCEEDED, now, "AUTHENTICATED");

        if (user.getRole() == null) {
            throw new AuthenticationServiceException(BAD_CREDENTIALS_MESSAGE);
        }
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                "",
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    private void appendAudit(
            User target,
            User actor,
            AccountSecurityEvent event,
            LocalDateTime occurredAt,
            String reasonCode
    ) {
        auditRepository.append(AccountSecurityAudit.record(target, actor, event, occurredAt, reasonCode));
    }
}
