package com.company.iss.client.service;

import com.company.iss.client.entity.Client;
import com.company.iss.client.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ClientService {

    @Autowired
    private ClientRepository clientRepository;

    public List<Client> search(String keyword) {

        if (keyword == null || keyword.isBlank()) {
            return clientRepository.findAll();
        }

        return clientRepository.findByCompanyNameContainingIgnoreCaseOrAddressContainingIgnoreCase(keyword, keyword);
    }

    public List<Client> findActive() {
        return clientRepository.findByActiveTrue();
    }

    public Client save(Client client) {
        validate(client);

        Optional<Client> existingClient = clientRepository.findByCompanyNameIgnoreCase(client.getCompanyName());

        if (existingClient.isPresent()) {

            if (client.getId() == null || !existingClient.get().getId().equals(client.getId())) {
                throw new RuntimeException("Client company already exists.");
            }
        }

        return clientRepository.save(client);
    }

    public void activate(Client client) {
        client.setActive(true);
        clientRepository.save(client);
    }

    public void deactivate(Client client) {
        client.setActive(false);
        clientRepository.save(client);
    }

    private void validate(Client client) {

        if (client.getCompanyName() == null || client.getCompanyName().isBlank()) {
            throw new RuntimeException("Company name is required.");
        }

        if (client.getAddress() == null || client.getAddress().isBlank()) {
            throw new RuntimeException("Address is required.");
        }
    }
}