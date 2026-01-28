package com.magicbus.careercatalyst.onboarding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Service
public class OnboardingService {

    private final AzureDocumentIntelligenceClient docClient;
    private final PowerAutomateNotifier notifier;
    private final JdbcTemplate jdbcTemplate;

    public OnboardingService(AzureDocumentIntelligenceClient docClient,
                             PowerAutomateNotifier notifier,
                             JdbcTemplate jdbcTemplate) {
        this.docClient = docClient;
        this.notifier = notifier;
        this.jdbcTemplate = jdbcTemplate;
    }

    public OnboardingResult verify(String studentId,
                                   MultipartFile aadhaar,
                                   MultipartFile pan,
                                   MultipartFile income) {
        try {
            DocumentExtractionResult aadhaarResult = docClient.analyzeIdDocument(aadhaar.getBytes(), "AADHAAR");
            DocumentExtractionResult panResult = docClient.analyzeIdDocument(pan.getBytes(), "PAN");
            DocumentExtractionResult incomeResult = docClient.analyzeIncomeDocument(income.getBytes());

            String profileName = fetchStudentName(studentId);
            boolean nameMatch = NameMatcher.allMatch(profileName,
                    aadhaarResult.getName(), panResult.getName(), incomeResult.getName());

            Map<String, DocumentExtractionResult> docs = new HashMap<>();
            docs.put("aadhaar", aadhaarResult);
            docs.put("pan", panResult);
            docs.put("income", incomeResult);

            checkConfidence(studentId, "aadhaar", aadhaarResult.getConfidence());
            checkConfidence(studentId, "pan", panResult.getConfidence());
            checkConfidence(studentId, "income", incomeResult.getConfidence());

            boolean verified = nameMatch
                    && aadhaarResult.getConfidence() >= 0.90
                    && panResult.getConfidence() >= 0.90
                    && incomeResult.getConfidence() >= 0.90;

            String notes = nameMatch ? "Name match successful" : "Name mismatch across documents";
            return new OnboardingResult(verified, studentId, docs, notes);
        } catch (Exception e) {
            return new OnboardingResult(false, studentId, Map.of(), "Verification failed: " + e.getMessage());
        }
    }

    private void checkConfidence(String studentId, String docType, double confidence) {
        if (confidence < 0.90) {
            notifier.notifyLowConfidence(studentId, docType, confidence);
        }
    }

    private String fetchStudentName(String studentId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT full_name FROM student_profiles WHERE student_id = ?",
                    String.class,
                    studentId
            );
        } catch (Exception e) {
            return "";
        }
    }
}
