package com.company.iss.position.config;

import com.company.iss.client.entity.Client;
import com.company.iss.client.repository.ClientRepository;
import com.company.iss.position.entity.EmploymentType;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.entity.PositionStatus;
import com.company.iss.position.repository.PositionOpeningRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
public class PositionOpeningDataLoader {

    @Autowired
    private PositionOpeningRepository positionOpeningRepository;

    @Autowired
    private ClientRepository clientRepository;

    @PostConstruct
    public void init() {

        if (positionOpeningRepository.count() > 0) {
            return;
        }

        List<Client> clients = clientRepository.findAll();

        if (clients.isEmpty()) {
            return;
        }

        Random random = new Random();

        createOpening("Service Crew", pick(clients, random), "Tanza, Cavite", EmploymentType.FULL_TIME, 30);
        createOpening("Cashier", pick(clients, random), "Dasmarinas, Cavite", EmploymentType.FULL_TIME, 15);
        createOpening("Kitchen Crew", pick(clients, random), "Imus, Cavite", EmploymentType.FULL_TIME, 20);
        createOpening("Store Supervisor", pick(clients, random), "Bacoor, Cavite", EmploymentType.FULL_TIME, 5);
        createOpening("Branch Manager", pick(clients, random), "General Trias, Cavite", EmploymentType.FULL_TIME, 2);

        createOpening("Customer Service Representative", pick(clients, random), "Alabang, Muntinlupa", EmploymentType.FULL_TIME, 50);
        createOpening("Technical Support Representative", pick(clients, random), "Pasig City", EmploymentType.FULL_TIME, 35);
        createOpening("Call Center Agent", pick(clients, random), "Makati City", EmploymentType.FULL_TIME, 40);
        createOpening("Chat Support Agent", pick(clients, random), "Taguig City", EmploymentType.FULL_TIME, 25);
        createOpening("Email Support Specialist", pick(clients, random), "Ortigas, Pasig", EmploymentType.FULL_TIME, 18);

        createOpening("Warehouse Assistant", pick(clients, random), "Paranaque City", EmploymentType.FULL_TIME, 20);
        createOpening("Inventory Clerk", pick(clients, random), "Bacoor, Cavite", EmploymentType.FULL_TIME, 10);
        createOpening("Delivery Rider", pick(clients, random), "Imus, Cavite", EmploymentType.CONTRACTUAL, 25);
        createOpening("Logistics Coordinator", pick(clients, random), "Taguig City", EmploymentType.FULL_TIME, 8);
        createOpening("Forklift Operator", pick(clients, random), "Dasmarinas, Cavite", EmploymentType.FULL_TIME, 6);

        createOpening("Sales Associate", pick(clients, random), "SM Bacoor", EmploymentType.FULL_TIME, 15);
        createOpening("Promodiser", pick(clients, random), "SM Dasmarinas", EmploymentType.CONTRACTUAL, 12);
        createOpening("Retail Associate", pick(clients, random), "Vista Mall Dasma", EmploymentType.FULL_TIME, 10);
        createOpening("Cashier Supervisor", pick(clients, random), "Robinsons Dasmarinas", EmploymentType.FULL_TIME, 4);
        createOpening("Merchandising Staff", pick(clients, random), "Imus, Cavite", EmploymentType.FULL_TIME, 8);

        createOpening("Data Encoder", pick(clients, random), "Bacoor, Cavite", EmploymentType.CONTRACTUAL, 20);
        createOpening("Administrative Assistant", pick(clients, random), "Makati City", EmploymentType.FULL_TIME, 7);
        createOpening("Office Staff", pick(clients, random), "Taguig City", EmploymentType.FULL_TIME, 9);
        createOpening("Document Controller", pick(clients, random), "Alabang", EmploymentType.FULL_TIME, 5);
        createOpening("Receptionist", pick(clients, random), "Pasay City", EmploymentType.FULL_TIME, 4);

        createOpening("HR Assistant", pick(clients, random), "Taguig City", EmploymentType.FULL_TIME, 6);
        createOpening("Recruitment Associate", pick(clients, random), "Imus, Cavite", EmploymentType.FULL_TIME, 5);
        createOpening("Training Assistant", pick(clients, random), "Makati City", EmploymentType.FULL_TIME, 3);
        createOpening("Payroll Assistant", pick(clients, random), "Pasig City", EmploymentType.FULL_TIME, 4);
        createOpening("Compliance Staff", pick(clients, random), "Taguig City", EmploymentType.FULL_TIME, 2);

        createOpening("Barista", pick(clients, random), "BGC, Taguig", EmploymentType.FULL_TIME, 8);
        createOpening("Coffee Shop Crew", pick(clients, random), "Alabang", EmploymentType.FULL_TIME, 10);
        createOpening("Shift Lead", pick(clients, random), "Makati City", EmploymentType.FULL_TIME, 3);
        createOpening("Dining Crew", pick(clients, random), "Imus, Cavite", EmploymentType.FULL_TIME, 14);
        createOpening("Food Prep Staff", pick(clients, random), "Trece Martires", EmploymentType.FULL_TIME, 12);

        createOpening("Security Guard", pick(clients, random), "Dasmarinas, Cavite", EmploymentType.CONTRACTUAL, 15);
        createOpening("Maintenance Technician", pick(clients, random), "Bacoor, Cavite", EmploymentType.FULL_TIME, 6);
        createOpening("Building Maintenance Staff", pick(clients, random), "Taguig City", EmploymentType.FULL_TIME, 5);
        createOpening("Janitorial Staff", pick(clients, random), "Pasay City", EmploymentType.CONTRACTUAL, 10);
        createOpening("Utility Worker", pick(clients, random), "Imus, Cavite", EmploymentType.FULL_TIME, 7);

        createOpening("Driver", pick(clients, random), "Paranaque City", EmploymentType.FULL_TIME, 5);
        createOpening("Messenger", pick(clients, random), "Makati City", EmploymentType.FULL_TIME, 4);
        createOpening("Dispatcher", pick(clients, random), "Taguig City", EmploymentType.FULL_TIME, 3);
        createOpening("Operations Assistant", pick(clients, random), "Pasig City", EmploymentType.FULL_TIME, 6);
        createOpening("Operations Supervisor", pick(clients, random), "BGC, Taguig", EmploymentType.FULL_TIME, 2);

        createOpening("Marketing Assistant", pick(clients, random), "Makati City", EmploymentType.FULL_TIME, 4);
        createOpening("Social Media Assistant", pick(clients, random), "Taguig City", EmploymentType.FULL_TIME, 3);
        createOpening("Graphic Designer", pick(clients, random), "Pasig City", EmploymentType.FULL_TIME, 2);
        createOpening("Content Assistant", pick(clients, random), "Alabang", EmploymentType.FULL_TIME, 2);
        createOpening("Brand Associate", pick(clients, random), "Makati City", EmploymentType.FULL_TIME, 2);
    }

    private Client pick(List<Client> clients, Random random) {
        return clients.get(random.nextInt(clients.size()));
    }

    private void createOpening(String title, Client client, String workLocation, EmploymentType employmentType, Integer requiredHeadcount) {
        PositionOpening opening = new PositionOpening();

        opening.setTitle(title);
        opening.setClient(client);
        opening.setWorkLocation(workLocation);
        opening.setEmploymentType(employmentType);
        opening.setRequiredHeadcount(requiredHeadcount);
        opening.setAppliedCount(0);
        opening.setInterviewedCount(0);
        opening.setPassedCount(0);
        opening.setHiredCount(0);
        opening.setDescription("Seeded demo opening");
        opening.setStatus(PositionStatus.OPEN);
        opening.setActive(true);

        positionOpeningRepository.save(opening);
    }
}