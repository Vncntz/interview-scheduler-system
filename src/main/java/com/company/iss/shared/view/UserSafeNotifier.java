package com.company.iss.shared.view;

import com.company.iss.booking.exception.BookingCancellationException;
import com.company.iss.booking.exception.BookingRescheduleException;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.data.binder.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

public final class UserSafeNotifier {

    static final String ACCESS_DENIED_MESSAGE = "You are not authorized to perform this action.";
    static final String VALIDATION_MESSAGE = "Please correct invalid values and try again.";
    private static final Logger log = LoggerFactory.getLogger(UserSafeNotifier.class);

    private UserSafeNotifier() {
    }

    public static void showError(Throwable error) {
        UserError userError = classify(error);
        if (userError.reference() != null) {
            log.error("Unexpected UI error [reference={}]", userError.reference(), error);
        } else if (containsCause(error, AccessDeniedException.class)) {
            log.warn("Denied UI action", error);
        }

        Notification notification = Notification.show(
                userError.message(),
                5000,
                Notification.Position.TOP_CENTER
        );
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    static UserError classify(Throwable error) {
        if (containsCause(error, AccessDeniedException.class)) {
            return new UserError(ACCESS_DENIED_MESSAGE, null);
        }
        if (containsCause(error, ValidationException.class)
                || containsCause(error, jakarta.validation.ValidationException.class)) {
            return new UserError(VALIDATION_MESSAGE, null);
        }
        if (isTrustedBusinessError(error) && hasMessage(error)) {
            return new UserError(error.getMessage(), null);
        }

        String reference = UUID.randomUUID().toString();
        return new UserError(
                "An unexpected error occurred. Please try again or contact support with reference " + reference + ".",
                reference
        );
    }

    private static boolean isTrustedBusinessError(Throwable error) {
        return error != null && (
                error instanceof BusinessRuleViolationException
                        || error instanceof BookingCancellationException
                        || error instanceof BookingRescheduleException
        );
    }

    private static boolean hasMessage(Throwable error) {
        return error.getMessage() != null && !error.getMessage().isBlank();
    }

    private static boolean containsCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    record UserError(String message, String reference) {
    }
}
