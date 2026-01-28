package com.magicbus.careercatalyst.onboarding;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class StudentProfileSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public StudentProfileSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS student_profiles (" +
            "student_id VARCHAR(64) PRIMARY KEY, " +
            "full_name VARCHAR(255), " +
            "dob DATE, " +
            "pan_number VARCHAR(32), " +
            "aadhaar_number VARCHAR(32), " +
            "annual_income VARCHAR(64), " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
        );
    }
}
