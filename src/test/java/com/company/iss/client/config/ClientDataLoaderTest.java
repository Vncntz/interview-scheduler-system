package com.company.iss.client.config;

import com.company.iss.client.repository.ClientRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientDataLoaderTest {

    @Test
    void seedOrderIsDeterministic() {
        assertEquals(seedCompanySequence(), seedCompanySequence());
    }

    @Test
    void existingClientDataMakesLoaderIdempotent() {
        ClientRepository repository = mock(ClientRepository.class);
        when(repository.count()).thenReturn(1L);

        new ClientDataLoader(repository).run();

        verify(repository, never()).save(any());
    }

    private List<String> seedCompanySequence() {
        ClientRepository repository = mock(ClientRepository.class);

        new ClientDataLoader(repository).run();

        ArgumentCaptor<com.company.iss.client.entity.Client> captor =
                ArgumentCaptor.forClass(com.company.iss.client.entity.Client.class);
        verify(repository, org.mockito.Mockito.times(50)).save(captor.capture());
        return captor.getAllValues().stream()
                .map(com.company.iss.client.entity.Client::getCompanyName)
                .toList();
    }
}
