package com.company.iss.hiring.service;

import com.company.iss.hiring.event.JobOfferIssuedEvent;
import com.company.iss.hiring.repository.HiringDecisionRepository;
import com.company.iss.notification.dto.HiringNotificationContext;
import com.company.iss.notification.entity.NotificationEvent;
import com.company.iss.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class JobOfferIssuedNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(JobOfferIssuedNotificationListener.class);

    private final HiringDecisionRepository decisionRepository;
    private final NotificationService notificationService;

    public JobOfferIssuedNotificationListener(
            HiringDecisionRepository decisionRepository,
            NotificationService notificationService
    ) {
        this.decisionRepository = decisionRepository;
        this.notificationService = notificationService;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobOfferIssued(JobOfferIssuedEvent event) {
        try {
            decisionRepository.findDetailedById(event.hiringDecisionId()).ifPresentOrElse(
                    decision -> notificationService.sendHiring(NotificationEvent.JOB_OFFERED, toContext(decision)),
                    () -> log.warn(
                            "[NOTIFICATION] Job offer notification skipped decisionId={} reason=DECISION_NOT_FOUND",
                            event.hiringDecisionId()
                    )
            );
        } catch (RuntimeException exception) {
            log.error(
                    "[NOTIFICATION] Job offer notification failed decisionId={} exception={}",
                    event.hiringDecisionId(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private HiringNotificationContext toContext(com.company.iss.hiring.entity.HiringDecision decision) {
        var applicant = decision.getApplicant();
        var position = decision.getPosition();
        return new HiringNotificationContext(
                applicant.getEmail(),
                applicant.getFullName(),
                position.getTitle(),
                position.getClient() == null ? "" : position.getClient().getCompanyName(),
                position.getWorkLocation()
        );
    }
}
