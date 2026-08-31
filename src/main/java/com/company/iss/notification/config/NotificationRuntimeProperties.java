package com.company.iss.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "iss.notification")
@Getter
public class NotificationRuntimeProperties {

    private final Smtp smtp = new Smtp();

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
}
