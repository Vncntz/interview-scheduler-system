package com.company.iss.auth.service;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final PasswordPolicy passwordPolicy;

    @Override
    public void run(String... args) {
        String email = environment.getProperty("ADMIN_EMAIL");
        String password = environment.getProperty("ADMIN_PASSWORD");

        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            return;
        }

        if (userRepository.existsByEmail(email)) {
            return;
        }

        passwordPolicy.validate(password, password);

        User admin = new User();
        admin.setEmail(email);
        admin.setFullName("System Administrator");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        admin.setMustChangePassword(true);

        userRepository.save(admin);
    }
}
