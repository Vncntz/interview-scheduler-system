package com.company.iss;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class InterviewSchedulerSystemApplicationTests {

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
		assertEquals("2", flyway.info().current().getVersion().getVersion());
		String nullable = jdbcTemplate.queryForObject(
				"""
				SELECT IS_NULLABLE
				FROM INFORMATION_SCHEMA.COLUMNS
				WHERE TABLE_NAME = 'APPLICANTS' AND COLUMN_NAME = 'BRANCH_ID'
				""",
				String.class
		);
		assertEquals("NO", nullable);
	}

}
