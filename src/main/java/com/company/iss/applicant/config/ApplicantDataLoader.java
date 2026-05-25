package com.company.iss.applicant.config;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.repository.PositionOpeningRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
public class ApplicantDataLoader {

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private PositionOpeningRepository positionOpeningRepository;

    @PostConstruct
    public void init() {

        if (applicantRepository.count() > 0) {
            return;
        }

        List<PositionOpening> openings = positionOpeningRepository.findAll();

        if (openings.isEmpty()) {
            return;
        }

        Random random = new Random();

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
    }
}