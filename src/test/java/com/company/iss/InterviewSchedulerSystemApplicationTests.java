package com.company.iss;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import com.company.iss.auth.service.AccountAuthenticationProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class InterviewSchedulerSystemApplicationTests {

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SecurityFilterChain securityFilterChain;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Test
	void contextLoads() {
		assertEquals("5", flyway.info().current().getVersion().getVersion());
		String nullable = jdbcTemplate.queryForObject(
				"""
				SELECT IS_NULLABLE
				FROM INFORMATION_SCHEMA.COLUMNS
				WHERE TABLE_NAME = 'APPLICANTS' AND COLUMN_NAME = 'BRANCH_ID'
				""",
				String.class
		);
		assertEquals("NO", nullable);
		assertEquals(
				0,
				jdbcTemplate.queryForObject(
						"""
						SELECT COUNT(*)
						FROM INFORMATION_SCHEMA.COLUMNS
						WHERE TABLE_NAME = 'NOTIFICATION_SETTINGS'
						  AND COLUMN_NAME IN ('SMTP_PASSWORD', 'SMS_API_KEY')
						""",
						Integer.class
				)
		);
		assertEquals(
				2,
				jdbcTemplate.queryForObject(
						"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME IN ('HIRING_DECISIONS', 'HIRING_DECISION_AUDITS')",
						Integer.class
				)
		);
		assertEquals(
				2,
				jdbcTemplate.queryForObject(
						"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME IN ('PASSWORD_RESET_REQUESTS', 'ACCOUNT_SECURITY_AUDITS')",
						Integer.class
				)
		);
		assertEquals(
				1,
				securityFilterChain.getFilters().stream()
						.filter(ConcurrentSessionFilter.class::isInstance)
						.count()
		);
		ProviderManager providerManager = (ProviderManager) authenticationManager;
		assertEquals(1, providerManager.getProviders().size());
		assertEquals(AccountAuthenticationProvider.class, providerManager.getProviders().getFirst().getClass());
		assertEquals(
				0,
				providerManager.getProviders().stream().filter(DaoAuthenticationProvider.class::isInstance).count()
		);
	}

}
