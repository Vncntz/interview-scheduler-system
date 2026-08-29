package com.company.iss.applicant.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.branch.entity.Branch;
import com.company.iss.client.entity.Client;
import com.company.iss.position.entity.EmploymentType;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.entity.PositionStatus;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class ApplicantGridRepositoryTest {

    @Autowired ApplicantRepository applicantRepository;
    @Autowired EntityManager entityManager;

    @Test
    void pagedFetchIsStableCountAgreesAndDisplayedRelationsAreInitialized() {
        Branch ownedBranch = persistBranch("OWN", "Owned Branch");
        Branch otherBranch = persistBranch("OTHER", "Other Branch");
        PositionOpening position = persistPosition();

        Applicant first = persistApplicant("Alex", "Candidate", "alex-1@example.test", ownedBranch, position);
        Applicant second = persistApplicant("Alex", "Candidate", "alex-2@example.test", ownedBranch, position);
        Applicant third = persistApplicant("Alex", "Candidate", "alex-3@example.test", ownedBranch, position);
        persistApplicant("Alex", "Candidate", "other@example.test", otherBranch, position);
        entityManager.flush();
        entityManager.clear();

        List<Applicant> pageZero = applicantRepository.findGridPage(
                ownedBranch.getId(), null, null, PageRequest.of(0, 2)
        );
        List<Applicant> pageOne = applicantRepository.findGridPage(
                ownedBranch.getId(), null, null, PageRequest.of(1, 2)
        );

        assertEquals(List.of(first.getId(), second.getId()), pageZero.stream().map(Applicant::getId).toList());
        assertEquals(List.of(third.getId()), pageOne.stream().map(Applicant::getId).toList());
        assertEquals(3L, applicantRepository.countGrid(ownedBranch.getId(), null, null));

        var pageZeroIds = new HashSet<>(pageZero.stream().map(Applicant::getId).toList());
        assertTrue(pageOne.stream().map(Applicant::getId).noneMatch(pageZeroIds::contains));
        pageZero.forEach(applicant -> {
            assertTrue(Hibernate.isInitialized(applicant.getBranch()));
            assertTrue(Hibernate.isInitialized(applicant.getPositionOpening()));
            assertTrue(Hibernate.isInitialized(applicant.getPositionOpening().getClient()));
        });
    }

    @Test
    void keywordAndStatusUseTheSamePredicatesForFetchAndCount() {
        Branch branch = persistBranch("FILTER", "Filter Branch");
        PositionOpening position = persistPosition();
        persistApplicant("Mina", "Santos", "mina@example.test", branch, position);
        Applicant screening = persistApplicant("Lia", "Reyes", "lia@example.test", branch, position);
        screening.setStatus(ApplicantStatus.SCREENING);
        persistApplicant("Lia", "Other", "lia.other@example.test", branch, position);
        entityManager.flush();
        entityManager.clear();

        List<Applicant> matches = applicantRepository.findGridPage(
                branch.getId(), "lia", ApplicantStatus.SCREENING, PageRequest.of(0, 50)
        );

        assertEquals(List.of(screening.getId()), matches.stream().map(Applicant::getId).toList());
        assertEquals(1L, applicantRepository.countGrid(branch.getId(), "lia", ApplicantStatus.SCREENING));
        assertEquals(1L, applicantRepository.countGrid(branch.getId(), "mina@example", null));
        assertEquals(1L, applicantRepository.countGrid(branch.getId(), "santos", null));
    }

    private Branch persistBranch(String code, String name) {
        Branch branch = new Branch();
        branch.setBranchCode(code);
        branch.setBranchName(name);
        branch.setAddress("Address");
        branch.setCity("City");
        branch.setProvince("Province");
        entityManager.persist(branch);
        return branch;
    }

    private PositionOpening persistPosition() {
        Client client = new Client();
        client.setCompanyName("Applicant Grid Client");
        client.setAddress("Client Address");
        entityManager.persist(client);

        PositionOpening position = new PositionOpening();
        position.setTitle("Engineer");
        position.setClient(client);
        position.setWorkLocation("Manila");
        position.setEmploymentType(EmploymentType.FULL_TIME);
        position.setRequiredHeadcount(5);
        position.setStatus(PositionStatus.OPEN);
        position.setActive(true);
        entityManager.persist(position);
        return position;
    }

    private Applicant persistApplicant(
            String firstName,
            String lastName,
            String email,
            Branch branch,
            PositionOpening position
    ) {
        Applicant applicant = new Applicant();
        applicant.setFirstName(firstName);
        applicant.setLastName(lastName);
        applicant.setEmail(email);
        applicant.setMobileNumber("09170000000");
        applicant.setBranch(branch);
        applicant.setPositionOpening(position);
        applicant.setStatus(ApplicantStatus.NEW);
        applicant.setActive(true);
        entityManager.persist(applicant);
        return applicant;
    }
}
