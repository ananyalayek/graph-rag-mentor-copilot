package com.magicbus.careercatalyst.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DataLoadingService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataLoadingService.class);

    private final VectorStore vectorStore;

    @Autowired
    public DataLoadingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Starting data loading process...");

        // For simplicity, we assume students.csv is in the resources folder
        ClassPathResource resource = new ClassPathResource("students.csv");
        if (!resource.exists()) {
            logger.error("students.csv not found in classpath. Please ensure it is in the src/main/resources folder.");
            // We should have a better way to handle this in production
            return;
        }

        List<Document> documents = new ArrayList<>();
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
                String skills = record.get("skills");
                String interests = record.get("interests");
                String education = record.get("education_level");

                String content = String.format(
                    "Student Profile for %s (ID: %s): Education: %s, Skills: %s, Interests: %s.",
                    name, studentId, education, skills, interests
                );

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("student_id", studentId);
                metadata.put("name", name);
                metadata.put("education_level", education);

                documents.add(new Document(content, metadata));
            }
        }

        if (!documents.isEmpty()) {
            logger.info("Adding {} student profiles to the vector store.", documents.size());
            vectorStore.add(documents);
            logger.info("Data loading complete.");
        } else {
            logger.warn("No documents were generated from students.csv.");
        }
    }
}
