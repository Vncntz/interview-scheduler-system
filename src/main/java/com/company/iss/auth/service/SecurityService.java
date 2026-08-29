package com.company.iss.auth.service;

import com.company.iss.auth.entity.User;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.repository.UserRepository;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class SecurityService {

    private final UserRepository userRepository;
    private final Clock clock;

    public SecurityService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

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
        return requireOperationsUser("You are not authorized to manage recruitment operations.");
    }

    public User requireOperationsUser(String unauthorizedRoleMessage) {
        User user = requireAuthenticatedActiveUser();
        if (user.isMustChangePassword()) {
            throw new AccessDeniedException("You must change your password before continuing.");
        }
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.RECRUITER) {
            throw new AccessDeniedException(unauthorizedRoleMessage);
        }
        if (user.getRole() == Role.RECRUITER
                && (user.getBranch() == null || user.getBranch().getId() == null)) {
            throw new AccessDeniedException("Your recruiter account is not assigned to a branch.");
        }
        return user;
    }

    public User requireAdmin() {
        User user = requireOperationsUser();
        if (user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Administrator access is required.");
        }
        return user;
    }

    public User requireAuthenticatedActiveUser() {
        User user = getCurrentUser();
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null || !user.isActive()
                || (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(now))) {
            throw new AccessDeniedException("An active authenticated user is required.");
        }
        return user;
    }

    public void logout() {
        logout("/login");
    }

    public void logoutAfterPasswordChange() {
        logout("/login?password-changed");
    }

    private void logout(String destination) {
        SecurityContextHolder.clearContext();

        VaadinServletRequest request = VaadinServletRequest.getCurrent();

        if (request != null && request.getHttpServletRequest().getSession(false) != null) {
            request.getHttpServletRequest().getSession(false).invalidate();
        }

        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.getPage().setLocation(destination);
        }
    }
}
