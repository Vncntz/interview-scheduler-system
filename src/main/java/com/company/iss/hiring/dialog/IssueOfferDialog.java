package com.company.iss.hiring.dialog;

import com.company.iss.hiring.dto.EligibleHiringCandidate;
import com.company.iss.hiring.dto.IssueOfferCommand;
import com.company.iss.hiring.service.HiringDecisionService;
import com.company.iss.shared.view.UserSafeNotifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextArea;

public class IssueOfferDialog extends Dialog {

    public IssueOfferDialog(
            EligibleHiringCandidate candidate,
            HiringDecisionService hiringDecisionService,
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
        add(confirmation, notes);

        Button cancel = new Button("Cancel", event -> close());
        Button confirm = new Button("Issue offer", event -> {
            try {
                hiringDecisionService.issueOffer(new IssueOfferCommand(
                        candidate.applicantId(),
                        candidate.evaluationId(),
                        notes.getValue()
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
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(cancel, confirm);
    }
}
