package com.company.iss.hiring.dialog;

import com.company.iss.hiring.dto.EligibleHiringCandidate;
import com.company.iss.hiring.dto.IssueOfferCommand;
import com.company.iss.hiring.service.HiringDecisionService;
import com.company.iss.hiring.service.OfferDeadlinePolicy;
import com.company.iss.shared.view.UserSafeNotifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.binder.Binder;

import java.time.LocalDateTime;

public class IssueOfferDialog extends Dialog {

    private final Button confirmButton;

    public IssueOfferDialog(
            EligibleHiringCandidate candidate,
            HiringDecisionService hiringDecisionService,
            OfferDeadlinePolicy deadlinePolicy,
            Runnable onSuccess
    ) {
        setHeaderTitle("Issue job offer");
        setWidth("560px");
        setCloseOnOutsideClick(false);

        Paragraph confirmation = new Paragraph(
                "Issue an offer to %s for %s? Headcount is allocated only after the offer is accepted."
                        .formatted(candidate.applicantName(), candidate.position())
        );
        TextArea notes = new TextArea("Offer notes (optional)");
        notes.setMaxLength(1000);
        notes.setWidthFull();
        DateTimePicker responseDueAt = new DateTimePicker("Response deadline (optional)");
        responseDueAt.setStep(java.time.Duration.ofMinutes(15));
        responseDueAt.setHelperText("Leave blank when the offer has no response deadline.");
        responseDueAt.setWidthFull();
        Binder<OfferInput> binder = new Binder<>(OfferInput.class);
        binder.forField(responseDueAt)
                .withValidator(value -> value == null || deadlinePolicy.isFuture(value, deadlinePolicy.now()),
                        "Response deadline must be in the future.")
                .bind(OfferInput::getResponseDueAt, OfferInput::setResponseDueAt);
        binder.forField(notes)
                .bind(OfferInput::getNotes, OfferInput::setNotes);
        OfferInput input = new OfferInput();
        binder.setBean(input);
        add(confirmation, responseDueAt, notes);

        Button cancel = new Button("Cancel", event -> close());
        confirmButton = new Button("Issue offer", event -> {
            if (!binder.validate().isOk()) {
                return;
            }
            try {
                hiringDecisionService.issueOffer(new IssueOfferCommand(
                        candidate.applicantId(),
                        candidate.evaluationId(),
                        input.getResponseDueAt(),
                        input.getNotes()
                ));
                Notification notification = Notification.show(
                        "Job offer issued.",
                        3000,
                        Notification.Position.TOP_CENTER
                );
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                onSuccess.run();
                close();
            } catch (RuntimeException exception) {
                UserSafeNotifier.showError(exception);
            }
        });
        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(cancel, confirmButton);
    }

    Button confirmButton() {
        return confirmButton;
    }

    public static class OfferInput {

        private LocalDateTime responseDueAt;
        private String notes;

        public LocalDateTime getResponseDueAt() {
            return responseDueAt;
        }

        public void setResponseDueAt(LocalDateTime responseDueAt) {
            this.responseDueAt = responseDueAt;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}
