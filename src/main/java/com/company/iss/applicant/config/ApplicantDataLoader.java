package com.company.iss.applicant.config;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.repository.PositionOpeningRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "iss.demo-data", name = "enabled", havingValue = "true")
@Order(30)
public class ApplicantDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ApplicantDataLoader.class);
    private static final long RANDOM_SEED = 42L;
    private static final int DEMO_APPLICANT_COUNT = 100;

    private final ApplicantRepository applicantRepository;
    private final PositionOpeningRepository positionOpeningRepository;
    private final BranchRepository branchRepository;

    public ApplicantDataLoader(
            ApplicantRepository applicantRepository,
            PositionOpeningRepository positionOpeningRepository,
            BranchRepository branchRepository
    ) {
        this.applicantRepository = applicantRepository;
        this.positionOpeningRepository = positionOpeningRepository;
        this.branchRepository = branchRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {

        if (applicantRepository.count() > 0) {
            log.info("[DEMO_DATA] Applicant seed skipped reason=APPLICANT_DATA_PRESENT");
            return;
        }

        List<PositionOpening> openings = positionOpeningRepository.findAll().stream()
                .sorted(Comparator.comparing(PositionOpening::getTitle)
                        .thenComparing(PositionOpening::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<Branch> branches = branchRepository.findAll().stream()
                .sorted(Comparator.comparing(Branch::getBranchCode)
                        .thenComparing(Branch::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        if (openings.isEmpty()) {
            log.warn("[DEMO_DATA] Applicant seed skipped reason=NO_POSITIONS");
            return;
        }
        if (branches.isEmpty()) {
            log.warn("[DEMO_DATA] Applicant seed skipped reason=NO_BRANCHES");
            return;
        }

        Random random = new Random(RANDOM_SEED);

        String[] firstNames = {
                "Juan", "Maria", "Jose", "Ana", "Mark", "Paolo", "Carlo", "Rica", "Angela", "Bryan",
                "Patricia", "Nicole", "Jayson", "Kevin", "Monica", "Princess", "Kenneth", "Melissa", "Ryan", "Sophia",
                "Bea", "Chris", "Vanessa", "Daniel", "Mika", "Julia", "Noel", "Marvin", "Louie", "Tina"
        };

        String[] lastNames = {
                "Dela Cruz", "Santos", "Reyes", "Cruz", "Torres", "Flores", "Ramos", "Gomez", "Bautista", "Rivera",
                "Lim", "Mendoza", "Garcia", "Navarro", "Aquino", "Castro", "Fernandez", "Villanueva", "Salazar", "Domingo"
        };

        String[] sources = {
                "Walk-in",
                "Facebook",
                "JobStreet",
                "Indeed",
                "Referral",
                "LinkedIn",
                "TikTok Ad",
                "Agency Referral"
        };

        for (int i = 1; i <= 100; i++) {

            String firstName = firstNames[random.nextInt(firstNames.length)];
            String lastName = lastNames[random.nextInt(lastNames.length)];

            PositionOpening opening =
                    openings.get(random.nextInt(openings.size()));

            Applicant applicant = new Applicant();

            applicant.setFirstName(firstName);
            applicant.setMiddleName("M");
            applicant.setLastName(lastName);

            applicant.setEmail(
                    firstName.toLowerCase()
                            + "."
                            + lastName.replace(" ", "").toLowerCase()
                            + i
                            + "@gmail.com"
            );

            applicant.setMobileNumber(
                    "09" + (100000000 + random.nextInt(899999999))
            );

            applicant.setPositionOpening(opening);
            applicant.setBranch(branches.get((i - 1) % branches.size()));

            applicant.setSource(
                    sources[random.nextInt(sources.length)]
            );

            applicant.setRemarks("Seeded demo applicant");

            applicant.setStatus(ApplicantStatus.NEW);

            applicant.setActive(true);

            applicantRepository.save(applicant);

            opening.setAppliedCount(
                    opening.getAppliedCount() + 1
            );

            positionOpeningRepository.save(opening);
        }

        log.info("[DEMO_DATA] Applicant seed completed applicantsCreated={}", DEMO_APPLICANT_COUNT);
    }
}
