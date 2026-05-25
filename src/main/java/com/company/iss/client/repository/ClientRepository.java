package com.company.iss.client.repository;

import com.company.iss.client.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    List<Client> findByCompanyNameContainingIgnoreCaseOrAddressContainingIgnoreCase(String companyName, String address);

    Optional<Client> findByCompanyNameIgnoreCase(String companyName);

    List<Client> findByActiveTrue();

}