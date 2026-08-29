package com.company.iss.auth.service;

import com.company.iss.auth.entity.User;
import com.company.iss.auth.event.CredentialsChangedEvent;
import com.company.iss.auth.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountSessionServiceTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void credentialChangeSnapshotsMatchingSessionsAndExpiresThemOnlyAfterCommit() {
        SessionRegistry registry = mock(SessionRegistry.class);
        UserRepository repository = mock(UserRepository.class);
        UserDetails principal = mock(UserDetails.class);
        SessionInformation first = mock(SessionInformation.class);
        SessionInformation second = mock(SessionInformation.class);
        User user = new User();
        user.setId(10L);
        user.setEmail("recruiter@example.test");
        when(repository.findById(10L)).thenReturn(Optional.of(user));
        when(registry.getAllPrincipals()).thenReturn(List.of(principal));
        when(principal.getUsername()).thenReturn(user.getEmail());
        when(registry.getAllSessions(principal, false)).thenReturn(List.of(first, second));

        beginSynchronization();
        new AccountSessionService(registry, repository).scheduleSessionExpiration(new CredentialsChangedEvent(10L));

        verify(first, never()).expireNow();
        verify(second, never()).expireNow();
        synchronization().afterCommit();
        verify(first).expireNow();
        verify(second).expireNow();
    }

    @Test
    void sessionRegisteredAfterSnapshotSurvivesAndRegistryIsNotRediscoveredAfterCommit() {
        SessionRegistry registry = mock(SessionRegistry.class);
        UserRepository repository = mock(UserRepository.class);
        UserDetails principal = mock(UserDetails.class);
        SessionInformation captured = mock(SessionInformation.class);
        SessionInformation registeredLater = mock(SessionInformation.class);
        User user = user(10L, "recruiter@example.test");
        List<SessionInformation> currentSessions = new ArrayList<>(List.of(captured));
        when(repository.findById(10L)).thenReturn(Optional.of(user));
        when(registry.getAllPrincipals()).thenReturn(List.of(principal));
        when(principal.getUsername()).thenReturn(user.getEmail());
        when(registry.getAllSessions(principal, false)).thenAnswer(invocation -> currentSessions);

        beginSynchronization();
        new AccountSessionService(registry, repository).scheduleSessionExpiration(new CredentialsChangedEvent(10L));
        currentSessions.add(registeredLater);
        synchronization().afterCommit();

        verify(captured).expireNow();
        verify(registeredLater, never()).expireNow();
        verify(registry, times(1)).getAllPrincipals();
        verify(registry, times(1)).getAllSessions(principal, false);
        verify(repository, times(1)).findById(10L);
    }

    @Test
    void rollbackDoesNotExpireCapturedSessions() {
        SessionRegistry registry = mock(SessionRegistry.class);
        UserRepository repository = mock(UserRepository.class);
        UserDetails principal = mock(UserDetails.class);
        SessionInformation captured = mock(SessionInformation.class);
        User user = user(10L, "recruiter@example.test");
        when(repository.findById(10L)).thenReturn(Optional.of(user));
        when(registry.getAllPrincipals()).thenReturn(List.of(principal));
        when(principal.getUsername()).thenReturn(user.getEmail());
        when(registry.getAllSessions(principal, false)).thenReturn(List.of(captured));

        beginSynchronization();
        new AccountSessionService(registry, repository).scheduleSessionExpiration(new CredentialsChangedEvent(10L));
        synchronization().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(captured, never()).expireNow();
    }

    @Test
    void missingUserDoesNotInspectOrMutateRegistry() {
        SessionRegistry registry = mock(SessionRegistry.class);
        UserRepository repository = mock(UserRepository.class);
        when(repository.findById(99L)).thenReturn(Optional.empty());

        new AccountSessionService(registry, repository).scheduleSessionExpiration(new CredentialsChangedEvent(99L));

        verifyNoInteractions(registry);
    }

    @Test
    void eventOutsideSynchronizedTransactionIsRejectedClearly() {
        SessionRegistry registry = mock(SessionRegistry.class);
        UserRepository repository = mock(UserRepository.class);
        when(repository.findById(10L)).thenReturn(Optional.of(user(10L, "recruiter@example.test")));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new AccountSessionService(registry, repository)
                        .scheduleSessionExpiration(new CredentialsChangedEvent(10L))
        );

        assertEquals(
                "Credential changes must be published inside a synchronized transaction.",
                failure.getMessage()
        );
        verifyNoInteractions(registry);
    }

    @Test
    void oneExpirationFailureDoesNotPreventRemainingCapturedSessions() {
        SessionRegistry registry = mock(SessionRegistry.class);
        UserRepository repository = mock(UserRepository.class);
        UserDetails principal = mock(UserDetails.class);
        SessionInformation failing = mock(SessionInformation.class);
        SessionInformation succeeding = mock(SessionInformation.class);
        User user = user(10L, "recruiter@example.test");
        when(repository.findById(10L)).thenReturn(Optional.of(user));
        when(registry.getAllPrincipals()).thenReturn(List.of(principal));
        when(principal.getUsername()).thenReturn(user.getEmail());
        when(registry.getAllSessions(principal, false)).thenReturn(List.of(failing, succeeding));
        doThrow(new IllegalStateException("test failure")).when(failing).expireNow();

        beginSynchronization();
        new AccountSessionService(registry, repository).scheduleSessionExpiration(new CredentialsChangedEvent(10L));

        assertDoesNotThrow(() -> synchronization().afterCommit());
        verify(failing).expireNow();
        verify(succeeding).expireNow();
    }

    private void beginSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    private TransactionSynchronization synchronization() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());
        return synchronizations.getFirst();
    }

    private User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }
}
