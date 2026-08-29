package com.company.iss.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountAuthenticationProviderTest {

    @Test
    void createsAuthenticatedTokenOnlyAfterServiceReturnsSuccessfully() {
        AccountAuthenticationService service = mock(AccountAuthenticationService.class);
        UserDetails principal = new User(
                "recruiter@example.test",
                "",
                List.of(new SimpleGrantedAuthority("ROLE_RECRUITER"))
        );
        when(service.authenticate("recruiter@example.test", "valid password")).thenReturn(principal);
        AccountAuthenticationProvider provider = new AccountAuthenticationProvider(service);
        UsernamePasswordAuthenticationToken request = UsernamePasswordAuthenticationToken.unauthenticated(
                "recruiter@example.test",
                "valid password"
        );
        request.setDetails("request-details");

        Authentication result = provider.authenticate(request);

        assertTrue(result.isAuthenticated());
        assertEquals(principal, result.getPrincipal());
        assertNull(result.getCredentials());
        assertEquals("request-details", result.getDetails());
        verify(service).authenticate("recruiter@example.test", "valid password");
    }

    @Test
    void transactionOrCommitFailureCannotProduceAuthenticatedToken() {
        AccountAuthenticationService service = mock(AccountAuthenticationService.class);
        when(service.authenticate("recruiter@example.test", "valid password"))
                .thenThrow(new DataIntegrityViolationException("test commit failure"));
        AccountAuthenticationProvider provider = new AccountAuthenticationProvider(service);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                        "recruiter@example.test",
                        "valid password"
                ))
        );
    }

    @Test
    void supportsOnlyUsernamePasswordAuthenticationTokens() {
        AccountAuthenticationProvider provider = new AccountAuthenticationProvider(
                mock(AccountAuthenticationService.class)
        );

        assertTrue(provider.supports(UsernamePasswordAuthenticationToken.class));
    }
}
