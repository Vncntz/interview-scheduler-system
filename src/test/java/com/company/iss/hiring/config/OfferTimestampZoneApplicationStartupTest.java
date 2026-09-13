package com.company.iss.hiring.config;

import com.company.iss.hiring.entity.HiringDecision;
import com.company.iss.hiring.repository.HiringDecisionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferTimestampZoneApplicationStartupTest {

    private static final String TIMESTAMP_ZONE_VARIABLE = "OFFER_RESPONSE_TIMESTAMP_ZONE";
    private static final String MISSING_ZONE_MESSAGE =
            "Existing hiring decisions use zone-less timestamps. Set OFFER_RESPONSE_TIMESTAMP_ZONE explicitly "
                    + "to the historical zone before startup; the Asia/Manila default is safe only when no hiring "
                    + "decisions exist. If the historical zone has fall-back overlaps, complete an approved "
                    + "timestamp backfill/storage migration before starting.";
    private static final String BLANK_ZONE_MESSAGE = "OFFER_RESPONSE_TIMESTAMP_ZONE must not be blank.";
    private static final Path PRODUCTION_APPLICATION_PROPERTIES =
            Path.of("src", "main", "resources", "application.properties").toAbsolutePath().normalize();

    @Test
    void emptyDatabaseAndMissingVariableStartsWithProductionManilaDefault() throws Exception {
        String databaseUrl = databaseUrl();
        try (ConfigurableApplicationContext context = start(databaseUrl, Map.of(), false)) {
            assertEquals(
                    "Asia/Manila",
                    context.getEnvironment().getProperty("iss.hiring.offer-deadline.timestamp-zone")
            );
            assertEquals(
                    ZoneId.of("Asia/Manila"),
                    context.getBean(OfferDeadlineProperties.class).getTimestampZone()
            );
            assertEquals(0L, context.getBean(HiringDecisionRepository.class).count());
        } finally {
            shutDown(databaseUrl);
        }
    }

    @Test
    void existingDecisionAndMissingVariableFailsDuringRunnerExecution() throws Exception {
        String databaseUrl = databaseUrl();
        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> start(databaseUrl, Map.of(), true)
            );

            assertTrue(hasMessageInCauseChain(failure, MISSING_ZONE_MESSAGE));
        } finally {
            shutDown(databaseUrl);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankOrWhitespaceVariableFailsDuringApplicationInitialization(String configuredZone) throws Exception {
        String databaseUrl = databaseUrl();
        try {
            RuntimeException failure = assertThrows(
                    RuntimeException.class,
                    () -> start(databaseUrl, Map.of(TIMESTAMP_ZONE_VARIABLE, configuredZone), false)
            );

            assertTrue(hasMessageInCauseChain(failure, BLANK_ZONE_MESSAGE));
        } finally {
            shutDown(databaseUrl);
        }
    }

    @Test
    void explicitValidVariableAllowsExistingDecisionToStart() throws Exception {
        String databaseUrl = databaseUrl();
        try (ConfigurableApplicationContext context = start(
                databaseUrl,
                Map.of(TIMESTAMP_ZONE_VARIABLE, "UTC"),
                true
        )) {
            assertEquals(ZoneId.of("UTC"), context.getBean(OfferDeadlineProperties.class).getTimestampZone());
            assertEquals(1L, context.getBean(HiringDecisionRepository.class).count());
        } finally {
            shutDown(databaseUrl);
        }
    }

    private ConfigurableApplicationContext start(
            String databaseUrl,
            Map<String, Object> environmentVariables,
            boolean seedHiringDecision
    ) {
        assertTrue(
                Files.isRegularFile(PRODUCTION_APPLICATION_PROPERTIES),
                "The production application.properties must be available to this startup regression"
        );

        SpringApplication application = new SpringApplication(StartupTestApplication.class);
        application.setEnvironment(controlledEnvironment(environmentVariables));
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);

        return application.run(
                "--spring.config.location=" + PRODUCTION_APPLICATION_PROPERTIES.toUri(),
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.flyway.locations=classpath:db/migration/h2",
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.jpa.open-in-view=false",
                "--spring.main.banner-mode=off",
                "--logging.level.root=OFF",
                "--startup-test.seed-hiring-decision=" + seedHiringDecision
        );
    }

    private ConfigurableEnvironment controlledEnvironment(Map<String, Object> environmentVariables) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource("controlledEnvironment", environmentVariables)
        );
        return environment;
    }

    private String databaseUrl() {
        return "jdbc:h2:mem:offer_zone_startup_"
                + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    }

    private void shutDown(String databaseUrl) throws Exception {
        try (var connection = DriverManager.getConnection(databaseUrl, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }

    private boolean hasMessageInCauseChain(Throwable failure, String expectedMessage) {
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expectedMessage)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "com.vaadin.flow.spring.SpringBootAutoConfiguration",
            "com.vaadin.flow.spring.SpringSecurityAutoConfiguration",
            "com.vaadin.flow.spring.VaadinScopesConfig"
    })
    @EnableConfigurationProperties(OfferDeadlineProperties.class)
    @EnableJpaRepositories(basePackageClasses = HiringDecisionRepository.class)
    @EntityScan(basePackageClasses = HiringDecision.class, basePackages = "com.company.iss")
    @Import(OfferTimestampZoneStartupGuard.class)
    static class StartupTestApplication {

        @Bean
        SmartInitializingSingleton hiringDecisionFixture(
                @Value("${startup-test.seed-hiring-decision:false}") boolean seedHiringDecision,
                JdbcTemplate jdbcTemplate
        ) {
            return () -> {
                if (!seedHiringDecision) {
                    return;
                }
                jdbcTemplate.update("""
                        INSERT INTO branches
                            (id, active, created_at, updated_at, version, branch_code, city, province, branch_name, address)
                        VALUES (1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'STARTUP', 'City', 'Province', 'Startup Branch', 'Address')
                        """);
                jdbcTemplate.update("""
                        INSERT INTO users
                            (id, active, failed_login_attempts, must_change_password, created_at, updated_at, version,
                             email, full_name, password_hash, role)
                        VALUES (1, TRUE, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
                                'startup@example.test', 'Startup Test', 'unused', 'ADMIN')
                        """);
                jdbcTemplate.update("""
                        INSERT INTO position_openings
                            (id, active, applied_count, hired_count, interviewed_count, passed_count,
                             required_headcount, created_at, updated_at, version, title, work_location,
                             employment_type, status)
                        VALUES (1, TRUE, 1, 0, 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
                                'Startup Position', 'Test', 'FULL_TIME', 'OPEN')
                        """);
                jdbcTemplate.update("""
                        INSERT INTO applicants
                            (id, active, branch_id, position_opening_id, created_at, updated_at, version,
                             mobile_number, first_name, last_name, email, status)
                        VALUES (1, TRUE, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
                                '0000000000', 'Startup', 'Applicant', 'applicant@example.test', 'OFFERED')
                        """);
                jdbcTemplate.update("""
                        INSERT INTO bookings
                            (id, applicant_id, booked_date_time, created_at, updated_at, version,
                             booking_reference, status, interview_stage, reminder_generation)
                        VALUES (1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
                                'STARTUP-BOOKING', 'PASSED', 'INITIAL', 0)
                        """);
                jdbcTemplate.update("""
                        INSERT INTO interview_evaluations
                            (id, communication_score, technical_score, attitude_score, applicant_id, booking_id,
                             evaluator_id, created_at, evaluation_date, updated_at, version, result)
                        VALUES (1, 8, 8, 8, 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                                CURRENT_TIMESTAMP, 0, 'PASS')
                        """);
                jdbcTemplate.update("""
                        INSERT INTO hiring_decisions
                            (id, applicant_id, evaluation_id, offered_at, offered_by_id, position_id,
                             created_at, updated_at, version, status)
                        VALUES (1, 1, 1, CURRENT_TIMESTAMP, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'OFFERED')
                        """);
            };
        }
    }
}
