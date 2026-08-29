package com.company.iss.config;

import com.company.iss.auth.view.LoginView;
import com.company.iss.auth.service.AccountAuthenticationProvider;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SessionRegistry sessionRegistry,
            AuthenticationManager authenticationManager
    ) throws Exception {
        // 1. Authorize public access to static asset patterns before Vaadin takes over
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/images/**", "/VAADIN/**").permitAll()
        );

        // 2. Load default Vaadin configuration settings
        http.with(
                VaadinSecurityConfigurer.vaadin(),
                config -> config
                        .loginView(LoginView.class)
                        .defaultSuccessUrl("/", true)
        );

        http.authenticationManager(authenticationManager);

        http.sessionManagement(session -> session
                .maximumSessions(-1)
                .sessionRegistry(sessionRegistry)
        );

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AccountAuthenticationProvider authenticationProvider) {
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
