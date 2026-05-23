package com.company.iss.auth.service;

import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SecurityService {

    @Autowired
    private UserRepository userRepository;

    public String getAuthenticatedUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return null;
        }

        return authentication.getName();
    }

    public User getCurrentUser() {
        String email = getAuthenticatedUserEmail();

        if (email == null) {
            return null;
        }

        return userRepository.findByEmail(email).orElse(null);
    }

    public void logout() {
        SecurityContextHolder.clearContext();

        VaadinServletRequest request = VaadinServletRequest.getCurrent();

        if (request != null && request.getHttpServletRequest().getSession(false) != null) {
            request.getHttpServletRequest().getSession(false).invalidate();
        }

        UI.getCurrent().getPage().setLocation("/login");
    }
}