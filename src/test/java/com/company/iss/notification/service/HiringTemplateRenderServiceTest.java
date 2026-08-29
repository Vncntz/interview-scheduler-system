package com.company.iss.notification.service;

import com.company.iss.notification.dto.HiringNotificationContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HiringTemplateRenderServiceTest {

    @Test
    void rendersOnlySafeHiringTokensAndHandlesMissingValues() {
        HiringNotificationContext context = new HiringNotificationContext(
                "alex@example.test",
                "Alex Candidate",
                "Engineer",
                null,
                "Singapore"
        );

        String rendered = new TemplateRenderService().render(
                "{{applicantName}} | {{position}} | {{client}} | {{workLocation}} | {{unknown}}",
                context
        );

        assertEquals("Alex Candidate | Engineer |  | Singapore | {{unknown}}", rendered);
    }
}
