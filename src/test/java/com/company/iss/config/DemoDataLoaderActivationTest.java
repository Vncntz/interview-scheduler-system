package com.company.iss.config;

import com.company.iss.applicant.config.ApplicantDataLoader;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.branch.repository.BranchRepository;
import com.company.iss.client.config.ClientDataLoader;
import com.company.iss.client.repository.ClientRepository;
import com.company.iss.position.config.PositionOpeningDataLoader;
import com.company.iss.position.repository.PositionOpeningRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class DemoDataLoaderActivationTest {

    @Test
    void loadersRequireDevProfileAndExplicitEnablement() {
        try (var production = context(false, true);
            var disabledDev = context(true, false);
             var enabledDev = context(true, true)) {
            assertNoDemoLoaders(production);
            assertNoDemoLoaders(disabledDev);
            assertNotNull(enabledDev.getBean(ClientDataLoader.class));
            assertNotNull(enabledDev.getBean(PositionOpeningDataLoader.class));
            assertNotNull(enabledDev.getBean(ApplicantDataLoader.class));
        }
    }

    @Test
    void loadersHaveStableOrderAndTransactionalRunBoundaries() throws NoSuchMethodException {
        assertEquals(10, ClientDataLoader.class.getAnnotation(Order.class).value());
        assertEquals(20, PositionOpeningDataLoader.class.getAnnotation(Order.class).value());
        assertEquals(30, ApplicantDataLoader.class.getAnnotation(Order.class).value());

        assertTransactionalRun(ClientDataLoader.class);
        assertTransactionalRun(PositionOpeningDataLoader.class);
        assertTransactionalRun(ApplicantDataLoader.class);
    }

    private void assertTransactionalRun(Class<?> loaderType) throws NoSuchMethodException {
        Method run = loaderType.getMethod("run", String[].class);
        assertNotNull(run.getAnnotation(Transactional.class));
    }

    private void assertNoDemoLoaders(AnnotationConfigApplicationContext context) {
        assertFalse(context.containsBeanDefinition("clientDataLoader"));
        assertFalse(context.containsBeanDefinition("positionOpeningDataLoader"));
        assertFalse(context.containsBeanDefinition("applicantDataLoader"));
    }

    private AnnotationConfigApplicationContext context(boolean devProfile, boolean enabled) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (devProfile) {
            context.getEnvironment().setActiveProfiles("dev");
        }
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                context,
                "iss.demo-data.enabled=" + enabled
        );
        context.registerBean(ClientRepository.class, () -> mock(ClientRepository.class));
        context.registerBean(PositionOpeningRepository.class, () -> mock(PositionOpeningRepository.class));
        context.registerBean(ApplicantRepository.class, () -> mock(ApplicantRepository.class));
        context.registerBean(BranchRepository.class, () -> mock(BranchRepository.class));
        context.register(ClientDataLoader.class, PositionOpeningDataLoader.class, ApplicantDataLoader.class);
        context.refresh();
        return context;
    }
}
