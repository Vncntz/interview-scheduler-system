package com.company.iss.auth.service;

import com.company.iss.auth.entity.User;
import com.company.iss.auth.event.CredentialsChangedEvent;
import com.company.iss.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

@Service
public class AccountSessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountSessionService.class);

    private final SessionRegistry sessionRegistry;
    private final UserRepository userRepository;

    public AccountSessionService(SessionRegistry sessionRegistry, UserRepository userRepository) {
        this.sessionRegistry = sessionRegistry;
        this.userRepository = userRepository;
    }

    @EventListener
    public void scheduleSessionExpiration(CredentialsChangedEvent event) {
        User user = userRepository.findById(event.userId()).orElse(null);
        if (user == null) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "Credential changes must be published inside a synchronized transaction."
            );
        }

        List<SessionInformation> sessionsToExpire = new ArrayList<>();
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UserDetails details && user.getEmail().equals(details.getUsername())) {
                sessionsToExpire.addAll(sessionRegistry.getAllSessions(principal, false));
            }
        }
        List<SessionInformation> capturedSessions = List.copyOf(sessionsToExpire);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (SessionInformation session : capturedSessions) {
                    try {
                        session.expireNow();
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Unable to expire one credential-change session after commit.");
                    }
                }
            }
        });
    }
}
