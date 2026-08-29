package com.company.iss.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "iss.security")
@Getter
@Setter
public class AccountSecurityProperties {

    private final Password password = new Password();
    private final Lockout lockout = new Lockout();
    private final PasswordReset passwordReset = new PasswordReset();

    @Getter
    @Setter
    public static class Password {
        private int minLength = 15;
        private int maxLength = 64;
        private int maxUtf8Bytes = 72;
    }

    @Getter
    @Setter
    public static class Lockout {
        private int maxFailedAttempts = 5;
        private Duration duration = Duration.ofMinutes(15);
    }

    @Getter
    @Setter
    public static class PasswordReset {
        private Duration ttl = Duration.ofMinutes(30);
        private String publicBaseUrl = "";
        private String signingSecret = "";
    }
}
