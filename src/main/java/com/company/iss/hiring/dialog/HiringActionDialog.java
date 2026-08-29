package com.company.iss.hiring.dialog;

import com.company.iss.shared.view.UserSafeNotifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextArea;

import java.util.function.Consumer;

public class HiringActionDialog extends Dialog {

    public HiringActionDialog(
            String title,
            String message,
            String confirmationLabel,
            boolean reasonRequired,
            Consumer<String> action,
            Runnable onSuccess
    ) {
        setHeaderTitle(title);
        setWidth("560px");
        setCloseOnOutsideClick(false);

        Paragraph confirmation = new Paragraph(message);
        TextArea remarks = new TextArea(reasonRequired ? "Reason" : "Notes (optional)");
        remarks.setRequiredIndicatorVisible(reasonRequired);
        remarks.setMaxLength(1000);
        remarks.setWidthFull();
        add(confirmation, remarks);

        Button cancel = new Button("Cancel", event -> close());
        Button confirm = new Button(confirmationLabel, event -> {
            if (reasonRequired && remarks.getValue().isBlank()) {
                remarks.setInvalid(true);
                remarks.setErrorMessage("A reason is required.");
                return;
            }
            try {
                action.accept(remarks.getValue());
                Notification notification = Notification.show(
                        "Hiring decision updated.",
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
        if (reasonRequired) {
            confirm.addThemeVariants(ButtonVariant.LUMO_ERROR);
        }
        getFooter().add(cancel, confirm);
    }
}
