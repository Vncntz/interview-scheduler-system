package com.company.iss.recruiter.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.auth.service.AccountLifecycleService;
import com.company.iss.auth.service.PasswordPolicy;
import com.company.iss.auth.service.PasswordResetService;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.branch.entity.Branch;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruiterServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PasswordPolicy passwordPolicy;
    @Mock SecurityService securityService;
    @Mock AccountLifecycleService accountLifecycleService;
    @Mock PasswordResetService passwordResetService;

    private RecruiterService service;

    @BeforeEach
    void setUp() {
        service = new RecruiterService(
                userRepository,
                passwordEncoder,
                passwordPolicy,
                securityService,
                accountLifecycleService,
                passwordResetService
        );
    }

    @Test
    void createRejectsAnApplicantLinkedUserBeforeAnyPersistenceOrPasswordWork() {
        User linkedUser = User.forApplicant(new Applicant());
        linkedUser.setRole(Role.RECRUITER);
        linkedUser.setFullName("Invalid Recruiter");
        linkedUser.setEmail("invalid@example.test");
        linkedUser.setBranch(new Branch());

        assertThrows(
                BusinessRuleViolationException.class,
                () -> service.save(linkedUser, "temporary password")
        );

        verify(userRepository, never()).existsByEmail(linkedUser.getEmail());
        verify(passwordPolicy, never()).validate("temporary password", "temporary password");
        verify(userRepository, never()).save(linkedUser);
    }

    @Test
    void createRejectsAnUnlinkedApplicantRoleBeforeAnyPersistenceOrPasswordWork() {
        User applicantUser = new User();
        applicantUser.setRole(Role.APPLICANT);
        applicantUser.setFullName("Invalid Recruiter");
        applicantUser.setEmail("unlinked-applicant@example.test");
        applicantUser.setBranch(new Branch());

        assertThrows(
                BusinessRuleViolationException.class,
                () -> service.save(applicantUser, "temporary password")
        );

        verify(userRepository, never()).existsByEmail(applicantUser.getEmail());
        verify(passwordPolicy, never()).validate("temporary password", "temporary password");
        verify(userRepository, never()).save(applicantUser);
    }

    @Test
    void createPreservesTheExistingRecruiterAccountWorkflow() {
        User input = new User();
        input.setFullName("Valid Recruiter");
        input.setEmail("recruiter@example.test");
        input.setBranch(new Branch());
        when(userRepository.existsByEmail(input.getEmail())).thenReturn(false);
        when(passwordEncoder.encode("temporary password")).thenReturn("encoded-password");
        when(userRepository.save(input)).thenReturn(input);

        User saved = service.save(input, "temporary password");

        assertEquals(Role.RECRUITER, saved.getRole());
        assertEquals("encoded-password", saved.getPasswordHash());
        assertTrue(saved.isMustChangePassword());
        assertTrue(saved.isActive());
        verify(passwordPolicy).validate("temporary password", "temporary password");
        verify(userRepository).save(input);
    }

    @Test
    void updatePreservesTheExistingRecruiterAccountWorkflow() {
        User input = new User();
        input.setId(7L);
        input.setFullName("Updated Recruiter");
        input.setEmail("updated@example.test");
        input.setBranch(new Branch());

        User persisted = new User();
        persisted.setId(7L);
        persisted.setRole(Role.RECRUITER);
        persisted.setFullName("Original Recruiter");
        persisted.setEmail("original@example.test");
        persisted.setBranch(new Branch());
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(persisted));
        when(userRepository.existsByEmail(input.getEmail())).thenReturn(false);
        when(userRepository.save(persisted)).thenReturn(persisted);

        User saved = service.save(input, "unused password");

        assertEquals("Updated Recruiter", saved.getFullName());
        assertEquals("updated@example.test", saved.getEmail());
        assertEquals(input.getBranch(), saved.getBranch());
        assertEquals(null, saved.getApplicant());
        verify(userRepository).save(persisted);
        verify(passwordPolicy, never()).validate("unused password", "unused password");
    }
}
