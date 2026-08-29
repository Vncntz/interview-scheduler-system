package com.company.iss.config;

import com.company.iss.auth.service.AccountAuthenticationProvider;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@SpringBootTest
class SecurityConfigTest {

    @Autowired SecurityFilterChain securityFilterChain;
    @Autowired AuthenticationManager authenticationManager;
    @Autowired SessionRegistry sessionRegistry;

    @Test
    void successfulLoginUsesRoleAwareRootInsteadOfSavedChangePasswordRequest()
            throws ServletException, IOException {
        UsernamePasswordAuthenticationFilter loginFilter = securityFilterChain.getFilters().stream()
                .filter(UsernamePasswordAuthenticationFilter.class::isInstance)
                .map(UsernamePasswordAuthenticationFilter.class::cast)
                .findFirst()
                .orElseThrow();
        AuthenticationSuccessHandler successHandler = (AuthenticationSuccessHandler) ReflectionTestUtils.getField(
                loginFilter,
                "successHandler"
        );
        assertNotNull(successHandler);

        MockHttpServletRequest savedRequest = new MockHttpServletRequest("GET", "/change-password");
        MockHttpServletResponse savedResponse = new MockHttpServletResponse();
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.saveRequest(savedRequest, savedResponse);

        MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", "/login");
        loginRequest.setSession(savedRequest.getSession());
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(
                loginRequest,
                loginResponse,
                UsernamePasswordAuthenticationToken.authenticated("admin@example.test", null, java.util.List.of())
        );

        assertEquals("/", loginResponse.getRedirectedUrl());
        assertNull(requestCache.getRequest(loginRequest, loginResponse));
    }

    @Test
    void customAuthenticationProviderAndSharedSessionRegistryRemainConfigured() {
        ProviderManager providerManager = assertInstanceOf(ProviderManager.class, authenticationManager);
        assertEquals(1, providerManager.getProviders().size());
        assertInstanceOf(AccountAuthenticationProvider.class, providerManager.getProviders().getFirst());

        UsernamePasswordAuthenticationFilter loginFilter = securityFilterChain.getFilters().stream()
                .filter(UsernamePasswordAuthenticationFilter.class::isInstance)
                .map(UsernamePasswordAuthenticationFilter.class::cast)
                .findFirst()
                .orElseThrow();
        Object sessionStrategy = ReflectionTestUtils.getField(loginFilter, "sessionStrategy");
        assertNotNull(sessionStrategy);
        ConcurrentSessionFilter concurrentSessionFilter = securityFilterChain.getFilters().stream()
                .filter(ConcurrentSessionFilter.class::isInstance)
                .map(ConcurrentSessionFilter.class::cast)
                .findFirst()
                .orElseThrow();
        assertSame(sessionRegistry, ReflectionTestUtils.getField(concurrentSessionFilter, "sessionRegistry"));
    }
}
