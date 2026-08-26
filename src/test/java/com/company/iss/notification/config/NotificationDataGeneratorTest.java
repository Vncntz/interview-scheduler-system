package com.company.iss.notification.config;

import com.company.iss.notification.entity.NotificationChannel;
import com.company.iss.notification.entity.NotificationEvent;
import com.company.iss.notification.entity.NotificationTemplate;
import com.company.iss.notification.repository.NotificationSettingsRepository;
import com.company.iss.notification.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDataGeneratorTest {

    @Test
    void provisionsRescheduleTemplateWhenOtherTemplatesAlreadyExist() {
        NotificationSettingsRepository settingsRepository = mock(NotificationSettingsRepository.class);
        NotificationTemplateRepository templateRepository = mock(NotificationTemplateRepository.class);
        when(settingsRepository.count()).thenReturn(1L);
        when(templateRepository.existsByEventAndChannel(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0) != NotificationEvent.BOOKING_RESCHEDULED);

        new NotificationDataGenerator(settingsRepository, templateRepository).init();

        ArgumentCaptor<NotificationTemplate> templateCaptor = ArgumentCaptor.forClass(NotificationTemplate.class);
        verify(templateRepository).save(templateCaptor.capture());
        NotificationTemplate template = templateCaptor.getValue();
        assertEquals(NotificationEvent.BOOKING_RESCHEDULED, template.getEvent());
        assertEquals(NotificationChannel.EMAIL, template.getChannel());
        assertTrue(template.getBody().contains("{{bookingReference}}"));
    }

    @Test
    void doesNotDuplicateExistingRescheduleTemplate() {
        NotificationSettingsRepository settingsRepository = mock(NotificationSettingsRepository.class);
        NotificationTemplateRepository templateRepository = mock(NotificationTemplateRepository.class);
        when(settingsRepository.count()).thenReturn(1L);
        when(templateRepository.existsByEventAndChannel(any(), any())).thenReturn(true);

        new NotificationDataGenerator(settingsRepository, templateRepository).init();

        verify(templateRepository, never()).save(any());
    }
}
