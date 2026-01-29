package com.magicbus.careercatalyst.onboarding;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class StudentProfileLookup {

    private static final Logger logger = LoggerFactory.getLogger(StudentProfileLookup.class);

    private final Map<String, String> studentNames = new HashMap<>();

    public StudentProfileLookup() {
        loadProfiles();
    }

    public String getNameById(String studentId) {
        if (studentId == null) {
            return "";
        }
        return studentNames.getOrDefault(studentId, "");
    }

    private void loadProfiles() {
        try {
            ClassPathResource resource = new ClassPathResource("students.csv");
            if (!resource.exists()) {
                logger.warn("students.csv not found; onboarding name matching will be skipped.");
                return;
            }

            try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                Iterable<CSVRecord> records = CSVFormat.DEFAULT
                        .builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .build()
                        .parse(reader);

                for (CSVRecord record : records) {
                    String studentId = record.get("student_id");
                    String name = record.get("name");
                    if (studentId != null && !studentId.isBlank()) {
                        studentNames.put(studentId, name == null ? "" : name);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load students.csv: {}", e.getMessage());
        }
    }
}
