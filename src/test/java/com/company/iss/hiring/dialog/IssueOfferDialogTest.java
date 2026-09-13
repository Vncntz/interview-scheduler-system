package com.company.iss.hiring.dialog;

import com.company.iss.hiring.config.OfferDeadlineProperties;
import com.company.iss.hiring.dto.EligibleHiringCandidate;
import com.company.iss.hiring.dto.IssueOfferCommand;
import com.company.iss.hiring.service.HiringDecisionService;
import com.company.iss.hiring.service.OfferDeadlinePolicy;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class IssueOfferDialogTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 2, 0);

    @AfterEach
    void clearUi() {
        UI.setCurrent(null);
    }

    @Test
    void futureDeadlineIsPassedToIssueOfferCommand() {
        UI ui = new UI();
        UI.setCurrent(ui);
        HiringDecisionService service = mock(HiringDecisionService.class);
        AtomicBoolean succeeded = new AtomicBoolean();
        IssueOfferDialog dialog = new IssueOfferDialog(candidate(), service, policy(), () -> succeeded.set(true));
        DateTimePicker deadline = descendant(dialog, DateTimePicker.class);
        deadline.setValue(NOW.plusDays(2));

        UI.setCurrent(ui);
        issueButton(dialog).click();

        ArgumentCaptor<IssueOfferCommand> command = ArgumentCaptor.forClass(IssueOfferCommand.class);
        verify(service).issueOffer(command.capture());
        assertEquals(NOW.plusDays(2), command.getValue().responseDueAt());
        assertTrue(succeeded.get());
    }

    @Test
    void nonFutureDeadlineShowsFieldValidationAndDoesNotCallService() {
        HiringDecisionService service = mock(HiringDecisionService.class);
        IssueOfferDialog dialog = new IssueOfferDialog(candidate(), service, policy(), () -> { });
        DateTimePicker deadline = descendant(dialog, DateTimePicker.class);
        deadline.setValue(NOW);

        issueButton(dialog).click();

        assertTrue(deadline.isInvalid());
        assertEquals("Response deadline must be in the future.", deadline.getErrorMessage());
        verify(service, never()).issueOffer(org.mockito.ArgumentMatchers.any());
        assertFalse(dialog.isOpened());
    }

    private EligibleHiringCandidate candidate() {
        return new EligibleHiringCandidate(
                10L, 20L, "Alex Candidate", "Main", "Engineer", "Client", "Singapore", NOW.minusDays(1));
    }

    private OfferDeadlinePolicy policy() {
        OfferDeadlineProperties properties = new OfferDeadlineProperties();
        properties.setTimestampZone(ZoneOffset.UTC);
        return new OfferDeadlinePolicy(
                Clock.fixed(Instant.parse("2026-09-13T02:00:00Z"), ZoneOffset.UTC), properties);
    }

    private Button issueButton(IssueOfferDialog dialog) {
        return dialog.confirmButton();
    }

    private <T extends Component> T descendant(Component component, Class<T> type) {
        return descendants(component).filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
