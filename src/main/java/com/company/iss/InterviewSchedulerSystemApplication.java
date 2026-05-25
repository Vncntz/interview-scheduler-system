package com.company.iss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.aura.Aura;

@SpringBootApplication
@StyleSheet("styles.css")
@StyleSheet("styles.css")
@StyleSheet(Aura.STYLESHEET)
public class InterviewSchedulerSystemApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(InterviewSchedulerSystemApplication.class, args);
    }
}
