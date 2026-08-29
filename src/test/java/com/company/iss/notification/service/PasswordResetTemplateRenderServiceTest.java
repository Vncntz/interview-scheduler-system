package com.company.iss.notification.service;

import com.company.iss.notification.dto.PasswordResetNotificationContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PasswordResetTemplateRenderServiceTest {

    @Test
    void rendersOnlyApprovedPasswordResetPlaceholders() {
        var context = new PasswordResetNotificationContext(
                "recruiter@example.test",
                "Recruiter User",
                "https://iss.example.test/reset-password?token=test-token",
                30
        );

        assertEquals(
                "Recruiter User | https://iss.example.test/reset-password?token=test-token | 30 | {{password}}",
                new TemplateRenderService().render(
                        "{{userName}} | {{resetLink}} | {{expiresInMinutes}} | {{password}}",
                        context
                )
        );
    }
}
