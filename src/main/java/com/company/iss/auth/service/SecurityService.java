package com.company.iss.auth.service;

import com.company.iss.auth.entity.User;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.repository.UserRepository;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

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

    public User requireOperationsUser() {
        User user = getCurrentUser();
        if (user == null || !user.isActive()) {
            throw new AccessDeniedException("An active authenticated user is required.");
        }
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.RECRUITER) {
            throw new AccessDeniedException("You are not authorized to manage recruitment operations.");
        }
        if (user.getRole() == Role.RECRUITER
                && (user.getBranch() == null || user.getBranch().getId() == null)) {
            throw new AccessDeniedException("Your recruiter account is not assigned to a branch.");
        }
        return user;
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
