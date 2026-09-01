package com.company.iss.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;

@Component
@ConfigurationProperties(prefix = "iss.notification")
@Getter
public class NotificationRuntimeProperties {

    private final Smtp smtp = new Smtp();
    private final Reminders reminders = new Reminders();

    @Getter
    @Setter
    public static class Smtp {
        private String password = "";
        private Duration connectionTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(5);
        private Duration writeTimeout = Duration.ofSeconds(5);

        public boolean isPasswordConfigured() {
            return password != null && !password.isBlank();
        }
    }
    @Getter
    public static class Reminders {
        private boolean enabled;
        private String businessZone = "Asia/Manila";
        private Duration fixedDelay = Duration.ofMinutes(5);
        private Duration initialDelay = Duration.ofSeconds(30);
        private int batchSize = 100;
        private int maxAttempts = 3;
        private Duration retryDelay = Duration.ofMinutes(10);
        private Duration staleClaimTimeout = Duration.ofMinutes(10);

        public ZoneId zoneId() {
            return ZoneId.of(businessZone);
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setBusinessZone(String businessZone) {
            try {
                ZoneId.of(Objects.requireNonNull(businessZone, "Reminder business zone is required."));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Reminder business zone must be a valid time-zone ID.", exception);
            }
            this.businessZone = businessZone;
        }

        public void setFixedDelay(Duration fixedDelay) {
            this.fixedDelay = requirePositive(fixedDelay, "Reminder fixed delay");
        }

        public void setInitialDelay(Duration initialDelay) {
            if (initialDelay == null || initialDelay.isNegative()) {
                throw new IllegalArgumentException("Reminder initial delay must not be negative.");
            }
            this.initialDelay = initialDelay;
        }

        public void setBatchSize(int batchSize) {
            if (batchSize < 1 || batchSize > 1_000) {
                throw new IllegalArgumentException("Reminder batch size must be between 1 and 1000.");
            }
            this.batchSize = batchSize;
        }

        public void setMaxAttempts(int maxAttempts) {
            if (maxAttempts < 1 || maxAttempts > 10) {
                throw new IllegalArgumentException("Reminder max attempts must be between 1 and 10.");
            }
            this.maxAttempts = maxAttempts;
        }

        public void setRetryDelay(Duration retryDelay) {
            this.retryDelay = requirePositive(retryDelay, "Reminder retry delay");
        }

        public void setStaleClaimTimeout(Duration staleClaimTimeout) {
            this.staleClaimTimeout = requirePositive(staleClaimTimeout, "Reminder stale-claim timeout");
        }

        private Duration requirePositive(Duration duration, String name) {
            if (duration == null || duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive.");
            }
            return duration;
        }
    }
}
