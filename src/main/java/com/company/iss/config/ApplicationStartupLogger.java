package com.company.iss.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

@Component
public class ApplicationStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupLogger.class);

    private final DataSource dataSource;
    private final Environment environment;

    public ApplicationStartupLogger(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logApplicationReady() {
        String port = environment.getProperty(
                "local.server.port",
                environment.getProperty("server.port", "8080")
        );
        String profiles = environment.getActiveProfiles().length == 0
                ? "default"
                : String.join(",", Arrays.asList(environment.getActiveProfiles()));

        log.info(
                "[CONFIG] Application started status=RUNNING server=Tomcat port={} database={} environment={} url=http://localhost:{}",
                port,
                databaseProductName(),
                profiles,
                port
        );
    }

    private String databaseProductName() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException exception) {
            log.error("[CONFIG] Database metadata lookup failed", exception);
            return "Unavailable";
        }
    }
}
