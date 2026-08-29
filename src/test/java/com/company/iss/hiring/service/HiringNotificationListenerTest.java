package com.company.iss.hiring.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.client.entity.Client;
import com.company.iss.hiring.entity.HiringDecision;
import com.company.iss.hiring.event.ApplicantHiredEvent;
import com.company.iss.hiring.event.JobOfferIssuedEvent;
import com.company.iss.hiring.repository.HiringDecisionRepository;
import com.company.iss.notification.dto.HiringNotificationContext;
import com.company.iss.notification.entity.NotificationEvent;
import com.company.iss.notification.service.NotificationService;
import com.company.iss.position.entity.PositionOpening;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HiringNotificationListenerTest {

    @Test
    void offerListenerReloadsCommittedDetailAndSendsSafeHiringContext() {
        HiringDecisionRepository repository = mock(HiringDecisionRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        HiringDecision decision = decision();
        when(repository.findDetailedById(42L)).thenReturn(Optional.of(decision));

        new JobOfferIssuedNotificationListener(repository, notificationService)
                .onJobOfferIssued(new JobOfferIssuedEvent(42L));

        verify(notificationService).sendHiring(
                NotificationEvent.JOB_OFFERED,
                new HiringNotificationContext(
                        "alex@example.test",
                        "Alex Candidate",
                        "Engineer",
                        "Example Client",
                        "Singapore"
                )
        );
    }

    @Test
    void hiringNotificationFailureIsContained() {
        HiringDecisionRepository repository = mock(HiringDecisionRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        HiringDecision decision = decision();
        HiringNotificationContext context = new HiringNotificationContext(
                "alex@example.test", "Alex Candidate", "Engineer", "Example Client", "Singapore"
        );
        when(repository.findDetailedById(42L)).thenReturn(Optional.of(decision));
        doThrow(new IllegalStateException("provider unavailable"))
                .when(notificationService)
                .sendHiring(NotificationEvent.HIRED, context);

        new ApplicantHiredNotificationListener(repository, notificationService)
                .onApplicantHired(new ApplicantHiredEvent(42L));

        verify(notificationService).sendHiring(NotificationEvent.HIRED, context);
    }

    @Test
    void hiringListenersAreAsynchronousAfterCommitWithNewReadOnlyTransactions() throws Exception {
        assertListenerContract(JobOfferIssuedNotificationListener.class.getMethod(
                "onJobOfferIssued", JobOfferIssuedEvent.class
        ));
        assertListenerContract(ApplicantHiredNotificationListener.class.getMethod(
                "onApplicantHired", ApplicantHiredEvent.class
        ));
    }

    private void assertListenerContract(Method method) {
        assertNotNull(method.getAnnotation(Async.class));
        TransactionalEventListener eventListener = method.getAnnotation(TransactionalEventListener.class);
        assertNotNull(eventListener);
        assertEquals(TransactionPhase.AFTER_COMMIT, eventListener.phase());
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
        assertTrue(transactional.readOnly());
    }

    private HiringDecision decision() {
        Applicant applicant = new Applicant();
        applicant.setFirstName("Alex");
        applicant.setLastName("Candidate");
        applicant.setEmail("alex@example.test");
        Client client = new Client();
        client.setCompanyName("Example Client");
        PositionOpening position = new PositionOpening();
        position.setTitle("Engineer");
        position.setClient(client);
        position.setWorkLocation("Singapore");
        HiringDecision decision = new HiringDecision();
        decision.setApplicant(applicant);
        decision.setPosition(position);
        return decision;
    }
}
