package com.company.iss.notification.config;

import com.company.iss.notification.entity.NotificationChannel;
import com.company.iss.notification.entity.NotificationEvent;
import com.company.iss.notification.entity.NotificationSettings;
import com.company.iss.notification.entity.NotificationTemplate;
import com.company.iss.notification.repository.NotificationSettingsRepository;
import com.company.iss.notification.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class NotificationDataGeneratorTest {

    @Test
    void defaultSettingsKeepAllDeliveryChannelsDisabled() {
        NotificationSettingsRepository settingsRepository = mock(NotificationSettingsRepository.class);
        NotificationTemplateRepository templateRepository = mock(NotificationTemplateRepository.class);
        when(settingsRepository.count()).thenReturn(0L);
        when(templateRepository.existsByEventAndChannel(any(), any())).thenReturn(true);

        new NotificationDataGenerator(settingsRepository, templateRepository).init();

        ArgumentCaptor<NotificationSettings> settingsCaptor = ArgumentCaptor.forClass(NotificationSettings.class);
        verify(settingsRepository).save(settingsCaptor.capture());
        assertFalse(settingsCaptor.getValue().getEmailEnabled());
        assertFalse(settingsCaptor.getValue().getSmsEnabled());
    }

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
        assertTrue(template.getBody().contains("{{interviewStage}}"));
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

    @Test
    void provisionsBothHiringTemplatesWhenMissing() {
        NotificationSettingsRepository settingsRepository = mock(NotificationSettingsRepository.class);
        NotificationTemplateRepository templateRepository = mock(NotificationTemplateRepository.class);
        when(settingsRepository.count()).thenReturn(1L);
        when(templateRepository.existsByEventAndChannel(any(), any())).thenAnswer(invocation -> {
            NotificationEvent event = invocation.getArgument(0);
            return event != NotificationEvent.JOB_OFFERED && event != NotificationEvent.HIRED;
        });

        new NotificationDataGenerator(settingsRepository, templateRepository).init();

        ArgumentCaptor<NotificationTemplate> templateCaptor = ArgumentCaptor.forClass(NotificationTemplate.class);
        verify(templateRepository, times(2)).save(templateCaptor.capture());
        assertEquals(
                java.util.List.of(NotificationEvent.JOB_OFFERED, NotificationEvent.HIRED),
                templateCaptor.getAllValues().stream().map(NotificationTemplate::getEvent).toList()
        );
        assertTrue(templateCaptor.getAllValues().stream().allMatch(
                template -> template.getBody().contains("{{applicantName}}")
                        && template.getBody().contains("{{position}}")
        ));
    }

    @Test
    void provisionsPasswordResetTemplateWithoutEmbeddingCredentials() {
        NotificationSettingsRepository settingsRepository = mock(NotificationSettingsRepository.class);
        NotificationTemplateRepository templateRepository = mock(NotificationTemplateRepository.class);
        when(settingsRepository.count()).thenReturn(1L);
        when(templateRepository.existsByEventAndChannel(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0) != NotificationEvent.PASSWORD_RESET);

        new NotificationDataGenerator(settingsRepository, templateRepository).init();

        ArgumentCaptor<NotificationTemplate> templateCaptor = ArgumentCaptor.forClass(NotificationTemplate.class);
        verify(templateRepository).save(templateCaptor.capture());
        NotificationTemplate template = templateCaptor.getValue();
        assertEquals(NotificationEvent.PASSWORD_RESET, template.getEvent());
        assertTrue(template.getBody().contains("{{resetLink}}"));
        assertTrue(template.getBody().contains("{{expiresInMinutes}}"));
    }
}
