package com.company.iss.auth.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.JoinColumn;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class UserApplicantOwnershipRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired ApplicantRepository applicantRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired EntityManager entityManager;

    @Test
    void applicantAccountPersistsAndReloadsItsExplicitOwnershipLink() {
        Applicant applicant = persistApplicant("owner@example.test", "OWN1");
        User user = applicantUser(applicant, "portal@example.test");

        Long userId = userRepository.saveAndFlush(user).getId();
        entityManager.clear();

        User reloaded = userRepository.findById(userId).orElseThrow();
        assertEquals(Role.APPLICANT, reloaded.getRole());
        assertEquals(applicant.getId(), reloaded.getApplicant().getId());
    }

    @Test
    void databaseUniquenessRejectsASecondUserForTheSameApplicant() {
        Applicant applicant = persistApplicant("unique-owner@example.test", "OWN2");
        userRepository.saveAndFlush(applicantUser(applicant, "first@example.test"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(applicantUser(applicant, "second@example.test"))
        );
    }

    @Test
    void entityLifecycleRejectsApplicantWithoutOwnershipLink() {
        User user = operationsUser("unlinked-applicant@example.test", Role.APPLICANT);

        assertThrows(RuntimeException.class, () -> userRepository.saveAndFlush(user));
    }

    @Test
    void entityLifecycleRejectsOperationsRoleWithApplicantLink() {
        Applicant applicant = persistApplicant("invalid-owner@example.test", "OWN3");
        User user = applicantUser(applicant, "linked-admin@example.test");
        user.setRole(Role.ADMIN);

        assertThrows(RuntimeException.class, () -> userRepository.saveAndFlush(user));
    }

    @Test
    void existingOperationsAccountsRemainPersistableWithoutApplicantLinks() {
        User admin = userRepository.saveAndFlush(operationsUser("admin@example.test", Role.ADMIN));
        User recruiter = userRepository.saveAndFlush(operationsUser("recruiter@example.test", Role.RECRUITER));

        assertEquals(null, admin.getApplicant());
        assertEquals(null, recruiter.getApplicant());
    }

    @Test
    void ownershipMappingIsCreationOnlyAndTheJoinColumnIsImmutable() throws NoSuchFieldException {
        assertFalse(Arrays.stream(User.class.getMethods())
                .anyMatch(method -> method.getName().equals("setApplicant")));
        assertTrue(Arrays.stream(User.class.getMethods())
                .anyMatch(method -> method.getName().equals("forApplicant")));

        JoinColumn joinColumn = User.class.getDeclaredField("applicant").getAnnotation(JoinColumn.class);
        assertEquals("applicant_id", joinColumn.name());
        assertTrue(joinColumn.unique());
        assertFalse(joinColumn.updatable());
        assertEquals("fk_users_applicant", joinColumn.foreignKey().name());
    }

    @Test
    void applicantAccountFactoryRejectsAMissingApplicant() {
        assertThrows(NullPointerException.class, () -> User.forApplicant(null));
    }

    private Applicant persistApplicant(String email, String branchCode) {
        Branch branch = new Branch();
        branch.setBranchCode(branchCode);
        branch.setBranchName("Ownership Branch " + branchCode);
        branch.setAddress("Test Address");
        branch.setCity("Manila");
        branch.setProvince("Metro Manila");
        branch.setActive(true);
        branch = branchRepository.saveAndFlush(branch);

        Applicant applicant = new Applicant();
        applicant.setBranch(branch);
        applicant.setFirstName("Ownership");
        applicant.setLastName("Applicant");
        applicant.setEmail(email);
        applicant.setMobileNumber("09170000000");
        applicant.setStatus(ApplicantStatus.NEW);
        applicant.setActive(true);
        return applicantRepository.saveAndFlush(applicant);
    }

    private User applicantUser(Applicant applicant, String email) {
        User user = User.forApplicant(applicant);
        user.setEmail(email);
        user.setPasswordHash("test-only-hash");
        user.setFullName("Applicant User");
        user.setActive(true);
        return user;
    }

    private User operationsUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("test-only-hash");
        user.setFullName("Operations User");
        user.setRole(role);
        user.setActive(true);
        return user;
    }
}
