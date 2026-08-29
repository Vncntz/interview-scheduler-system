package com.company.iss.position.config;

import com.company.iss.client.entity.Client;
import com.company.iss.client.repository.ClientRepository;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.repository.PositionOpeningRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PositionOpeningDataLoaderTest {

    @Test
    void existingPositionDataMakesLoaderIdempotent() {
        PositionOpeningRepository positions = mock(PositionOpeningRepository.class);
        ClientRepository clients = mock(ClientRepository.class);
        when(positions.count()).thenReturn(1L);

        new PositionOpeningDataLoader(positions, clients).run();

        verify(positions, never()).save(any());
        verify(clients, never()).findAll();
    }

    @Test
    void missingClientsSafelySkipsPositionCreation() {
        PositionOpeningRepository positions = mock(PositionOpeningRepository.class);
        ClientRepository clients = mock(ClientRepository.class);
        when(positions.count()).thenReturn(0L);
        when(clients.findAll()).thenReturn(List.of());

        new PositionOpeningDataLoader(positions, clients).run();

        verify(positions, never()).save(any());
    }

    @Test
    void sortedInputsAndFixedSeedProduceDeterministicClientRelations() {
        assertEquals(seedClientSequence(List.of(client(2L, "Zulu"), client(1L, "Alpha"))),
                seedClientSequence(List.of(client(1L, "Alpha"), client(2L, "Zulu"))));
    }

    private List<String> seedClientSequence(List<Client> input) {
        PositionOpeningRepository positions = mock(PositionOpeningRepository.class);
        ClientRepository clients = mock(ClientRepository.class);
        when(positions.count()).thenReturn(0L);
        when(clients.findAll()).thenReturn(input);

        new PositionOpeningDataLoader(positions, clients).run();

        ArgumentCaptor<PositionOpening> captor = ArgumentCaptor.forClass(PositionOpening.class);
        verify(positions, org.mockito.Mockito.times(50)).save(captor.capture());
        return captor.getAllValues().stream()
                .map(opening -> opening.getClient().getCompanyName())
                .toList();
    }

    private Client client(Long id, String companyName) {
        Client client = new Client();
        client.setId(id);
        client.setCompanyName(companyName);
        return client;
    }
}
