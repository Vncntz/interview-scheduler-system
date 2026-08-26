package com.company.iss.applicant.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class ApplicantRepositoryTest {

    @Autowired ApplicantRepository applicantRepository;
    @Autowired BranchRepository branchRepository;

    @Test
    void persistsBranchOwnershipAndExcludesBranchlessApplicantsFromRecruiterQuery() {
        Branch branch = new Branch();
        branch.setBranchCode("OWN");
        branch.setBranchName("Owned Branch");
        branch.setAddress("Address");
        branch.setCity("City");
        branch.setProvince("Province");
        branch = branchRepository.saveAndFlush(branch);

        Applicant owned = applicant("owned@example.test");
        owned.setBranch(branch);
        owned = applicantRepository.saveAndFlush(owned);
        applicantRepository.saveAndFlush(applicant("legacy@example.test"));

        var scoped = applicantRepository.findByBranchIdOrderByLastNameAscFirstNameAsc(branch.getId());

        assertEquals(1, scoped.size());
        assertEquals(owned.getId(), scoped.getFirst().getId());
        assertEquals(branch.getId(), scoped.getFirst().getBranch().getId());
        assertTrue(scoped.stream().noneMatch(item -> item.getBranch() == null));
    }

    private Applicant applicant(String email) {
        Applicant applicant = new Applicant();
        applicant.setFirstName("Test");
        applicant.setLastName("Applicant");
        applicant.setEmail(email);
        applicant.setMobileNumber("09170000000");
        applicant.setStatus(ApplicantStatus.NEW);
        applicant.setActive(true);
        return applicant;
    }
}
