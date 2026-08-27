package com.company.iss.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FlywayMigrationTest {

    private static final String LOCATIONS = "classpath:db/migration/h2";

    @Test
    void freshMigrationRejectsApplicantWithoutBranch() throws SQLException {
        String url = databaseUrl("fresh");
        migrate(url, null);

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(branchlessApplicantInsert("fresh@example.test")));
        }
    }

    @Test
    void unresolvedLegacyApplicantPreventsV2Migration() throws SQLException {
        String url = databaseUrl("legacy");
        migrate(url, "1");

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate(branchlessApplicantInsert("legacy@example.test"));
        }

        assertThrows(FlywayException.class, () -> migrate(url, null));
    }

    private void migrate(String url, String target) {
        var configuration = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .cleanDisabled(true)
                .validateOnMigrate(true);
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private String databaseUrl(String suffix) {
        return "jdbc:h2:mem:flyway_" + suffix + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    }

    private String branchlessApplicantInsert(String email) {
        return """
                INSERT INTO applicants (
                    active, created_at, updated_at, mobile_number, first_name, last_name, email, status, branch_id
                ) VALUES (
                    TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '09170000000', 'Legacy', 'Applicant', '%s', 'NEW', NULL
                )
                """.formatted(email);
    }
}
