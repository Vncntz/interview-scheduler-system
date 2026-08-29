package com.company.iss.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;

@Configuration
public class AccountSecurityConfig {

    @Bean
    Clock accountSecurityClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom accountSecurityRandom() {
        return new SecureRandom();
    }
}
