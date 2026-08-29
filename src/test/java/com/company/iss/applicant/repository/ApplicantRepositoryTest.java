package com.company.iss.applicant.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class ApplicantRepositoryTest {

    @Autowired ApplicantRepository applicantRepository;
    @Autowired BranchRepository branchRepository;

    @Test
    void persistsBranchOwnershipAndScopesApplicantsToTheRequestedBranch() {
        Branch branch = new Branch();
        branch.setBranchCode("OWN");
        branch.setBranchName("Owned Branch");
        branch.setAddress("Address");
        branch.setCity("City");
        branch.setProvince("Province");
        branch = branchRepository.saveAndFlush(branch);

        Branch otherBranch = new Branch();
        otherBranch.setBranchCode("OTHER");
        otherBranch.setBranchName("Other Branch");
        otherBranch.setAddress("Other address");
        otherBranch.setCity("Other city");
        otherBranch.setProvince("Other province");
        otherBranch = branchRepository.saveAndFlush(otherBranch);

        Applicant owned = applicant("owned@example.test");
        owned.setBranch(branch);
        owned = applicantRepository.saveAndFlush(owned);
        Applicant outOfScope = applicant("other@example.test");
        outOfScope.setBranch(otherBranch);
        applicantRepository.saveAndFlush(outOfScope);

        var scoped = applicantRepository.findGridPage(
                branch.getId(), null, null, PageRequest.of(0, 50)
        );

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
